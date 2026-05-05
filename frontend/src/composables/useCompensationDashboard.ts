import { computed, onMounted, onScopeDispose, reactive, ref, watch } from 'vue'
import {
  queryOrderCompensations,
  queryPaymentCompensations,
  reconcilePaymentManually,
  reconcilePaymentsInBulk,
  replayOrderTimeoutInBulk,
  replayOrderTimeoutManually,
} from '../services/compensationApi'
import { HttpClientError } from '../services/httpClient'
import type { ApiResponseMeta } from '../types/api/common'
import type {
  OrderCompensationAggregateResponse,
  OrderCompensationQueryResponse,
  PaymentCompensationAggregateResponse,
  PaymentCompensationQueryResponse,
} from '../types/api/compensation'
import {
  buildAuditQueryTemplates,
  buildAuditQueryLinks,
  buildDashboardSearchParams,
  buildExportRows,
  buildOrderBulkReplayRequest,
  buildOrderManualReplayRequest,
  buildReplayAuditFilters,
  buildOrderQuery,
  buildPaymentBulkReplayRequest,
  buildPaymentManualReplayRequest,
  buildPaymentQuery,
  createDefaultDashboardState,
  createAuditObservabilityConfig,
  describeDashboardError,
  deriveSummaryCards,
  deserializeDashboardState,
  getAggregateKey,
  getDashboardItemLabel,
  getLastTraceId,
  readDashboardStateFromSearch,
  serializeDashboardState,
  summarizeBulkReplay,
  type AuditObservabilityConfig,
  type AuditFilters,
  type AuditQueryKind,
  type AuditQueryTemplates,
  type CompensationView,
  type DashboardItem,
} from '../views/admin/compensationDashboardModel'

type DashboardResponse = OrderCompensationQueryResponse | PaymentCompensationQueryResponse

interface ActionFeedback {
  title: string
  code: string
  traceId: string | null
  summary: string
  details: string[]
  tone: 'success' | 'warning' | 'danger'
  auditFilters: AuditFilters | null
}

const STORAGE_KEY = 'sangui.compensation.dashboard.state'
const COPY_FEEDBACK_MS = 1800

interface UseCompensationDashboardOptions {
  auditObservabilityConfig?: AuditObservabilityConfig
}

export function useCompensationDashboard(options: UseCompensationDashboardOptions = {}) {
  const defaults = createDefaultDashboardState()
  const auditObservabilityConfig = options.auditObservabilityConfig ?? createAuditObservabilityConfig()
  const activeView = ref<CompensationView>(defaults.view)
  const filters = reactive(defaults.filters)
  const replayControls = reactive(defaults.replayControls)
  const auditFilters = reactive(defaults.auditFilters)
  const isLoading = ref(false)
  const response = ref<DashboardResponse | null>(null)
  const lastMeta = ref<ApiResponseMeta | null>(null)
  const error = ref<HttpClientError | null>(null)
  const actionError = ref<HttpClientError | null>(null)
  const actionErrorAuditFilters = ref<AuditFilters | null>(null)
  const lastAction = ref<ActionFeedback | null>(null)
  const isBulkRunning = ref(false)
  const pendingManualKey = ref<string | null>(null)
  const copiedTraceKey = ref<string | null>(null)
  const copiedAuditQueryKey = ref<string | null>(null)
  const isReady = ref(false)

  let copiedTraceTimer: number | null = null
  let copiedAuditQueryTimer: number | null = null

  const items = computed(() => response.value?.items ?? [])
  const summaryCards = computed(() => deriveSummaryCards(activeView.value, response.value))
  const auditQueryTemplates = computed(() => buildAuditQueryTemplates(auditFilters))
  const auditQueryLinks = computed(() => buildAuditQueryLinks(auditQueryTemplates.value, auditObservabilityConfig))
  const canGoPrev = computed(() => (response.value?.pageNo ?? 1) > 1)
  const canGoNext = computed(() => {
    if (!response.value) {
      return false
    }

    return response.value.pageNo * response.value.pageSize < response.value.total
  })
  const canRunReplay = computed(() => replayControls.operator.trim().length > 0)
  const isAnyReplayRunning = computed(() => isBulkRunning.value || pendingManualKey.value !== null)
  const bulkTargetCount = computed(() => {
    if (items.value.length === 0) {
      return 0
    }

    return Math.min(Math.max(1, replayControls.bulkLimit), items.value.length)
  })
  const errorDescription = computed(() => (error.value ? describeDashboardError(error.value) : ''))
  const actionErrorDescription = computed(() => (actionError.value ? describeDashboardError(actionError.value) : ''))

  async function fetchRecords(options: { resetResponseOnError?: boolean } = {}) {
    const { resetResponseOnError = true } = options

    isLoading.value = true
    error.value = null

    try {
      if (activeView.value === 'order') {
        const result = await queryOrderCompensations(buildOrderQuery(filters))
        response.value = result.data
        lastMeta.value = result.meta
      } else {
        const result = await queryPaymentCompensations(buildPaymentQuery(filters))
        response.value = result.data
        lastMeta.value = result.meta
      }
    } catch (caught) {
      if (resetResponseOnError) {
        response.value = null
        lastMeta.value = null
      }
      error.value = toHttpClientError(caught, 'Unexpected request failure.')
    } finally {
      isLoading.value = false
    }
  }

  async function submit() {
    filters.pageNo = 1
    await fetchRecords()
  }

  async function reset() {
    Object.assign(filters, createDefaultDashboardState().filters)
    await fetchRecords()
  }

  async function setView(nextView: CompensationView) {
    if (activeView.value === nextView) {
      return
    }

    activeView.value = nextView
    filters.pageNo = 1
    await fetchRecords()
  }

  async function goToPage(pageNo: number) {
    filters.pageNo = pageNo
    await fetchRecords()
  }

  async function setPageSize(pageSize: number) {
    filters.pageSize = pageSize
    filters.pageNo = 1
    await fetchRecords()
  }

  function validateReplayOperator() {
    if (replayControls.operator.trim().length > 0) {
      return true
    }

    actionError.value = new HttpClientError(
      'Replay operator is required before running manual or bulk replay.',
      {
        code: 'VALIDATION_FAILED',
        status: 400,
        traceId: null,
      },
    )
    return false
  }

  async function runManualReplay(item: DashboardItem) {
    if (!validateReplayOperator()) {
      return
    }

    const actionKey = getAggregateKey(activeView.value, item)
    if (pendingManualKey.value === actionKey || isBulkRunning.value) {
      return
    }

    pendingManualKey.value = actionKey
    actionError.value = null
    actionErrorAuditFilters.value = null
    lastAction.value = null
    const currentView = activeView.value
    const operator = replayControls.operator.trim()

    try {
      if (currentView === 'order') {
        const result = await replayOrderTimeoutManually(buildOrderManualReplayRequest(
          filters,
          item as OrderCompensationAggregateResponse,
          replayControls,
        ))
        lastAction.value = {
          title: `Order replay ${humanize(result.data.result)}`,
          code: result.meta.code,
          traceId: result.meta.traceId || null,
          summary: `${getDashboardItemLabel(currentView, item)} replay result ${humanize(result.data.result)}.`,
          details: compact([
            `Operator ${operator}`,
            result.data.errorCode ? `Error ${result.data.errorCode}` : null,
            result.data.reason ?? null,
          ]),
          tone: result.data.result === 'failed' ? 'danger' : result.data.result === 'skipped' ? 'warning' : 'success',
          auditFilters: buildReplayAuditFilters(currentView, 'manual', result.meta.traceId || null, operator, 'success', filters.shopId),
        }
      } else {
        const result = await reconcilePaymentManually(buildPaymentManualReplayRequest(
          filters,
          item as PaymentCompensationAggregateResponse,
          replayControls,
        ))
        lastAction.value = {
          title: `Payment replay ${humanize(result.data.result)}`,
          code: result.meta.code,
          traceId: result.meta.traceId || null,
          summary: `${getDashboardItemLabel(currentView, item)} replay result ${humanize(result.data.result)}.`,
          details: compact([
            `Operator ${operator}`,
            result.data.errorCode ? `Error ${result.data.errorCode}` : null,
            result.data.reason ?? null,
          ]),
          tone: result.data.result === 'failed' ? 'danger' : result.data.result === 'skipped' ? 'warning' : 'success',
          auditFilters: buildReplayAuditFilters(currentView, 'manual', result.meta.traceId || null, operator, 'success', filters.shopId),
        }
      }

      await fetchRecords({ resetResponseOnError: false })
    } catch (caught) {
      const httpError = toHttpClientError(caught, 'Manual replay failed.')
      actionError.value = httpError
      actionErrorAuditFilters.value = httpError.traceId
        ? buildReplayAuditFilters(currentView, 'manual', httpError.traceId, operator, 'failed', filters.shopId)
        : null
    } finally {
      pendingManualKey.value = null
    }
  }

  async function runBulkReplay() {
    if (!validateReplayOperator()) {
      return
    }
    if (items.value.length === 0 || isBulkRunning.value || pendingManualKey.value) {
      return
    }

    isBulkRunning.value = true
    actionError.value = null
    actionErrorAuditFilters.value = null
    lastAction.value = null
    const currentView = activeView.value
    const operator = replayControls.operator.trim()

    try {
      if (currentView === 'order') {
        const result = await replayOrderTimeoutInBulk(buildOrderBulkReplayRequest(
          filters,
          items.value as OrderCompensationAggregateResponse[],
          replayControls,
        ))
        lastAction.value = {
          title: replayControls.dryRun ? 'Order bulk dry-run complete' : 'Order bulk replay complete',
          code: result.meta.code,
          traceId: result.meta.traceId || null,
          summary: summarizeBulkReplay(result.data),
          details: result.data.items.slice(0, 3).map((entry) => {
            const target = entry.order.orderNo
            const failure = entry.errorCode ? ` (${entry.errorCode})` : ''
            return `${target}: ${humanize(entry.result)}${failure}`
          }),
          tone: replayControls.dryRun ? 'warning' : result.data.failedCount > 0 ? 'danger' : 'success',
          auditFilters: buildReplayAuditFilters(currentView, 'bulk', result.meta.traceId || null, operator, 'success', filters.shopId),
        }
      } else {
        const result = await reconcilePaymentsInBulk(buildPaymentBulkReplayRequest(
          filters,
          items.value as PaymentCompensationAggregateResponse[],
          replayControls,
        ))
        lastAction.value = {
          title: replayControls.dryRun ? 'Payment bulk dry-run complete' : 'Payment bulk replay complete',
          code: result.meta.code,
          traceId: result.meta.traceId || null,
          summary: summarizeBulkReplay(result.data),
          details: result.data.items.slice(0, 3).map((entry) => {
            const target = entry.payment.paymentNo
            const failure = entry.errorCode ? ` (${entry.errorCode})` : ''
            return `${target}: ${humanize(entry.result)}${failure}`
          }),
          tone: replayControls.dryRun ? 'warning' : result.data.failedCount > 0 ? 'danger' : 'success',
          auditFilters: buildReplayAuditFilters(currentView, 'bulk', result.meta.traceId || null, operator, 'success', filters.shopId),
        }
      }

      if (!replayControls.dryRun) {
        await fetchRecords({ resetResponseOnError: false })
      }
    } catch (caught) {
      const httpError = toHttpClientError(caught, 'Bulk replay failed.')
      actionError.value = httpError
      actionErrorAuditFilters.value = httpError.traceId
        ? buildReplayAuditFilters(currentView, 'bulk', httpError.traceId, operator, 'failed', filters.shopId)
        : null
    } finally {
      isBulkRunning.value = false
    }
  }

  async function copyTraceId(item: DashboardItem) {
    const traceId = getLastTraceId(item)
    if (!traceId || typeof window === 'undefined' || !window.navigator?.clipboard) {
      return
    }

    await window.navigator.clipboard.writeText(traceId)
    copiedTraceKey.value = getAggregateKey(activeView.value, item)
    if (copiedTraceTimer !== null) {
      window.clearTimeout(copiedTraceTimer)
    }
    copiedTraceTimer = window.setTimeout(() => {
      copiedTraceKey.value = null
      copiedTraceTimer = null
    }, COPY_FEEDBACK_MS)
  }

  async function copyAuditQuery(kind: AuditQueryKind) {
    if (typeof window === 'undefined' || !window.navigator?.clipboard) {
      return
    }

    await window.navigator.clipboard.writeText(auditQueryTemplates.value[kind])
    copiedAuditQueryKey.value = kind
    if (copiedAuditQueryTimer !== null) {
      window.clearTimeout(copiedAuditQueryTimer)
    }
    copiedAuditQueryTimer = window.setTimeout(() => {
      copiedAuditQueryKey.value = null
      copiedAuditQueryTimer = null
    }, COPY_FEEDBACK_MS)
  }

  function openAuditQuery(kind: AuditQueryKind) {
    if (typeof window === 'undefined') {
      return
    }

    const url = auditQueryLinks.value[kind]
    if (!url) {
      return
    }

    window.open(url, '_blank', 'noopener,noreferrer')
  }

  function applyAuditTrail(nextFilters: AuditFilters | null) {
    if (!nextFilters) {
      return
    }

    Object.assign(auditFilters, nextFilters)
  }

  function isManualReplayPending(item: DashboardItem): boolean {
    return pendingManualKey.value === getAggregateKey(activeView.value, item)
  }

  function isTraceCopied(item: DashboardItem): boolean {
    return copiedTraceKey.value === getAggregateKey(activeView.value, item)
  }

  function exportCurrentPage() {
    if (typeof window === 'undefined' || items.value.length === 0) {
      return
    }

    const rows = buildExportRows(activeView.value, items.value)
    const header = [
      'kind',
      'businessKey',
      'status',
      'latestResult',
      'latestTrigger',
      'latestOperator',
      'latestTraceId',
      'latestAttemptAt',
      'matchedAttemptCount',
      'totalAttemptCount',
    ]
    const csvContent = [
      header.join(','),
      ...rows.map((row) => header.map((key) => escapeCsv(row[key as keyof typeof row])).join(',')),
    ].join('\n')

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8' })
    const url = window.URL.createObjectURL(blob)
    const anchor = window.document.createElement('a')
    anchor.href = url
    anchor.download = `${activeView.value}-compensation-page-${filters.pageNo}.csv`
    anchor.click()
    window.URL.revokeObjectURL(url)
  }

  function restoreState() {
    if (typeof window === 'undefined') {
      return
    }

    const fromSearch = readDashboardStateFromSearch(window.location.search)
    const fromStorage = deserializeDashboardState(window.localStorage.getItem(STORAGE_KEY))
    const nextState = fromSearch ?? fromStorage ?? createDefaultDashboardState()

    activeView.value = nextState.view
    Object.assign(filters, nextState.filters)
    Object.assign(replayControls, nextState.replayControls)
    Object.assign(auditFilters, nextState.auditFilters)
  }

  watch([activeView, filters, replayControls, auditFilters], () => {
    if (!isReady.value || typeof window === 'undefined') {
      return
    }

    const state = {
      view: activeView.value,
      filters: { ...filters },
      replayControls: { ...replayControls },
      auditFilters: { ...auditFilters },
    }
    window.localStorage.setItem(STORAGE_KEY, serializeDashboardState(state))
    const params = buildDashboardSearchParams(state)
    const nextSearch = params.toString()
    const nextUrl = nextSearch ? `${window.location.pathname}?${nextSearch}` : window.location.pathname
    window.history.replaceState({}, '', nextUrl)
  }, { deep: true })

  onMounted(() => {
    restoreState()
    isReady.value = true
    void fetchRecords()
  })

  onScopeDispose(() => {
    if (copiedTraceTimer !== null && typeof window !== 'undefined') {
      window.clearTimeout(copiedTraceTimer)
    }
    if (copiedAuditQueryTimer !== null && typeof window !== 'undefined') {
      window.clearTimeout(copiedAuditQueryTimer)
    }
  })

  return {
    activeView,
    filters,
    replayControls,
    auditFilters,
    auditQueryTemplates,
    auditQueryLinks,
    isLoading,
    response,
    lastMeta,
    error,
    errorDescription,
    actionError,
    actionErrorAuditFilters,
    actionErrorDescription,
    lastAction,
    isBulkRunning,
    items,
    summaryCards,
    canGoPrev,
    canGoNext,
    canRunReplay,
    isAnyReplayRunning,
    bulkTargetCount,
    submit,
    reset,
    setView,
    goToPage,
    setPageSize,
    runManualReplay,
    runBulkReplay,
    isManualReplayPending,
    copyTraceId,
    isTraceCopied,
    copyAuditQuery,
    openAuditQuery,
    copiedAuditQueryKey,
    applyAuditTrail,
    exportCurrentPage,
  }
}

function toHttpClientError(caught: unknown, fallbackMessage: string): HttpClientError {
  if (caught instanceof HttpClientError) {
    return caught
  }

  return new HttpClientError(fallbackMessage, {
    code: 'UNEXPECTED_ERROR',
    status: 0,
    traceId: null,
  })
}

function compact(values: Array<string | null>): string[] {
  return values.filter((value): value is string => Boolean(value))
}

function humanize(value: string): string {
  return value
    .split(/[_-]/g)
    .filter(Boolean)
    .map((part) => `${part.charAt(0).toUpperCase()}${part.slice(1).toLowerCase()}`)
    .join(' ')
}

function escapeCsv(value: string): string {
  const normalized = value.split('"').join('""')
  return `"${normalized}"`
}

import { computed, ref, type Ref } from 'vue'
import { useAppPreferences } from './useAppPreferences'
import {
  listAdminSeckillActivities,
  getAdminSeckillActivity,
  createAdminSeckillActivity,
  updateAdminSeckillActivity,
  updateAdminSeckillActivityStatus,
  bindAdminSeckillActivitySku,
} from '../services/seckillApi'
import type { PersistedOpsSession } from '../types/api/auth'
import type {
  AdminSeckillActivityDetailResponse,
  AdminSeckillActivityStatus,
  AdminSeckillActivityStatusFilter,
} from '../types/api/seckill'
import {
  toSeckillAdminError,
  type SeckillAdminErrorState,
} from '../views/admin/seckillActivityManagementModel'

interface SeckillActivityListItemView {
  activityId: number
  activityName: string
  status: AdminSeckillActivityStatus
  startsAt: string
  endsAt: string
  serverTime: string
  skuCount: number
  totalActivityStock: number
  soldCount: number
}

interface SeckillActivityFormDraft {
  activityId: number | null
  activityName: string
  activityDescription: string
  status: AdminSeckillActivityStatus
  startsAt: string
  endsAt: string
}

interface BindSkuPayload {
  activityStock: number
  seckillPriceCent: number
}

function createEmptyDraft(): SeckillActivityFormDraft {
  return {
    activityId: null,
    activityName: '',
    activityDescription: '',
    status: 'draft',
    startsAt: '',
    endsAt: '',
  }
}

function createDraftFromDetail(detail: AdminSeckillActivityDetailResponse): SeckillActivityFormDraft {
  return {
    activityId: detail.activityId,
    activityName: detail.activityName,
    activityDescription: detail.description ?? '',
    status: detail.status,
    startsAt: detail.startsAt,
    endsAt: detail.endsAt,
  }
}

function createSubmissionGate() {
  const pending = ref(false)

  function begin(): boolean {
    if (pending.value) {
      return false
    }
    pending.value = true
    return true
  }

  function end() {
    pending.value = false
  }

  function isPending() {
    return pending.value
  }

  return { begin, end, isPending }
}

function createRequestId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID()
  }
  return `req-${Date.now()}`
}

export function useSeckillActivityManagement(
  session: Ref<PersistedOpsSession | null>,
  canAccessWorkspace: Ref<boolean>,
) {
  const { t } = useAppPreferences()
  const items = ref<SeckillActivityListItemView[]>([])
  const detail = ref<AdminSeckillActivityDetailResponse | null>(null)
  const draft = ref<SeckillActivityFormDraft>(createEmptyDraft())
  const filterStatus = ref<AdminSeckillActivityStatusFilter>('all')
  const listError = ref<SeckillAdminErrorState | null>(null)
  const detailError = ref<SeckillAdminErrorState | null>(null)
  const actionError = ref<SeckillAdminErrorState | null>(null)
  const isLoadingList = ref(false)
  const isLoadingDetail = ref(false)
  const listGate = createSubmissionGate()
  const actionGate = createSubmissionGate()

  const validation = computed(() => {
    return {
      valid: true,
      errors: {
        activityStock: '',
        skus: [] as Array<{ activityStock: string }>,
      },
    }
  })

  const isSaving = computed(() => listGate.isPending() || actionGate.isPending())
  const selectedItem = computed(() => items.value.find((item) => item.activityId === detail.value?.activityId) ?? null)

  async function bootstrap() {
    if (!canAccessWorkspace.value || !session.value) {
      return
    }
    await refreshList()
  }

  async function refreshList(selectFirst = false) {
    if (!canAccessWorkspace.value || !session.value || !listGate.begin()) {
      return
    }
    isLoadingList.value = true
    listError.value = null
    try {
      const result = await listAdminSeckillActivities({
        page: 1,
        size: 20,
        status: filterStatus.value,
      })
      items.value = result.data.items
      if (selectFirst && result.data.items.length > 0) {
        await selectActivity(result.data.items[0].activityId)
      }
      if (result.data.items.length === 0) {
        detail.value = null
        draft.value = createEmptyDraft()
      }
    } catch (caught) {
      listError.value = toSeckillAdminError(caught, 'Unable to load seckill activities.')
      items.value = []
    } finally {
      isLoadingList.value = false
      listGate.end()
    }
  }

  async function selectActivity(activityId: number) {
    if (!canAccessWorkspace.value || !session.value) {
      return
    }
    isLoadingDetail.value = true
    detailError.value = null
    actionError.value = null
    try {
      const result = await getAdminSeckillActivity(activityId)
      detail.value = result.data
      draft.value = createDraftFromDetail(result.data)
    } catch (caught) {
      detailError.value = toSeckillAdminError(caught, 'Unable to load activity detail.')
    } finally {
      isLoadingDetail.value = false
    }
  }

  function createNewActivity() {
    detail.value = null
    detailError.value = null
    actionError.value = null
    draft.value = createEmptyDraft()
  }

  async function saveDraft() {
    if (!canAccessWorkspace.value || !actionGate.begin()) {
      return false
    }
    actionError.value = null
    try {
      const currentSession = session.value
      if (!currentSession) {
        actionError.value = {
          code: 'AUTH_TOKEN_MISSING',
          message: 'Missing admin session.',
          traceId: null,
        }
        return false
      }
      const requestBody = {
        shopId: currentSession.shopId,
        userId: String(currentSession.userId),
        activityName: draft.value.activityName.trim(),
        description: draft.value.activityDescription.trim() || null,
        startsAt: draft.value.startsAt.trim(),
        endsAt: draft.value.endsAt.trim(),
        skus: [],
      }
      let nextActivityId = draft.value.activityId
      if (draft.value.activityId === null) {
        const result = await createAdminSeckillActivity(requestBody)
        nextActivityId = result.data.activityId
      } else {
        await updateAdminSeckillActivity(draft.value.activityId, requestBody)
      }
      await refreshList()
      if (nextActivityId !== null) {
        await selectActivity(nextActivityId)
      }
      return true
    } catch (caught) {
      actionError.value = toSeckillAdminError(caught, 'Unable to save activity.')
      return false
    } finally {
      actionGate.end()
    }
  }

  async function changeStatus(nextStatus: string) {
    if (!canAccessWorkspace.value || !detail.value?.activityId || !actionGate.begin()) {
      return false
    }
    actionError.value = null
    try {
      const result = await updateAdminSeckillActivityStatus(detail.value.activityId, {
        status: nextStatus,
        requestId: createRequestId(),
      })
      detail.value = result.data
      draft.value = createDraftFromDetail(result.data)
      await refreshList()
      return true
    } catch (caught) {
      actionError.value = toSeckillAdminError(caught, 'Unable to update status.')
      return false
    } finally {
      actionGate.end()
    }
  }

  async function bindSku(skuId: number, payload: BindSkuPayload) {
    if (!canAccessWorkspace.value || !detail.value?.activityId || !actionGate.begin()) {
      return false
    }
    actionError.value = null
    const { activityStock, seckillPriceCent } = payload
    if (activityStock < 0) {
      actionError.value = {
        code: 'VALIDATION_FAILED',
        message: t('seckillAdmin.activityStockNegative'),
        traceId: null,
      }
      actionGate.end()
      return false
    }
    const skuInfo = detail.value.skus.find((s) => s.skuId === skuId)
    if (skuInfo && activityStock > skuInfo.availableStock) {
      actionError.value = {
        code: 'VALIDATION_FAILED',
        message: t('seckillAdmin.activityStockExceeds'),
        traceId: null,
      }
      actionGate.end()
      return false
    }
    try {
      const result = await bindAdminSeckillActivitySku(detail.value.activityId, {
        productId: skuInfo?.productId ?? 0,
        skuId,
        activityStock,
        seckillPriceCent: seckillPriceCent || undefined,
        requestId: createRequestId(),
      })
      detail.value = result.data
      draft.value = createDraftFromDetail(result.data)
      await refreshList()
      return true
    } catch (caught) {
      actionError.value = toSeckillAdminError(caught, 'Unable to bind SKU.')
      return false
    } finally {
      actionGate.end()
    }
  }

  function retry() {
    if (detail.value?.activityId) {
      void selectActivity(detail.value.activityId)
      return
    }
    void refreshList()
  }

  function setFilterStatus(nextStatus: AdminSeckillActivityStatusFilter) {
    filterStatus.value = nextStatus
    void refreshList(true)
  }

  return {
    items,
    detail,
    draft,
    filterStatus,
    listError,
    detailError,
    actionError,
    isLoadingList,
    isLoadingDetail,
    isSaving,
    validation,
    selectedItem,
    bootstrap,
    refreshList,
    selectActivity,
    createNewActivity,
    saveDraft,
    changeStatus,
    bindSku,
    retry,
    setFilterStatus,
  }
}

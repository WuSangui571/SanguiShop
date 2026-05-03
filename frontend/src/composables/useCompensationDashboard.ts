import { computed, onMounted, reactive, ref } from 'vue'
import { queryOrderCompensations, queryPaymentCompensations } from '../services/compensationApi'
import { HttpClientError } from '../services/httpClient'
import type { ApiResponseMeta } from '../types/api/common'
import type {
  OrderCompensationQueryResponse,
  PaymentCompensationQueryResponse,
} from '../types/api/compensation'
import {
  buildOrderQuery,
  buildPaymentQuery,
  createDefaultFilters,
  deriveSummaryCards,
  type CompensationView,
} from '../views/admin/compensationDashboardModel'

type DashboardResponse = OrderCompensationQueryResponse | PaymentCompensationQueryResponse

export function useCompensationDashboard() {
  const activeView = ref<CompensationView>('payment')
  const filters = reactive(createDefaultFilters())
  const isLoading = ref(false)
  const response = ref<DashboardResponse | null>(null)
  const lastMeta = ref<ApiResponseMeta | null>(null)
  const error = ref<HttpClientError | null>(null)

  const items = computed(() => response.value?.items ?? [])
  const summaryCards = computed(() => deriveSummaryCards(activeView.value, response.value))
  const canGoPrev = computed(() => (response.value?.pageNo ?? 1) > 1)
  const canGoNext = computed(() => {
    if (!response.value) {
      return false
    }

    return response.value.pageNo * response.value.pageSize < response.value.total
  })

  async function fetchRecords() {
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
      response.value = null
      lastMeta.value = null
      error.value = caught instanceof HttpClientError
        ? caught
        : new HttpClientError('Unexpected request failure.', {
            code: 'UNEXPECTED_ERROR',
            status: 0,
            traceId: null,
          })
    } finally {
      isLoading.value = false
    }
  }

  async function submit() {
    filters.pageNo = 1
    await fetchRecords()
  }

  async function reset() {
    Object.assign(filters, createDefaultFilters())
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

  onMounted(() => {
    void fetchRecords()
  })

  return {
    activeView,
    filters,
    isLoading,
    response,
    lastMeta,
    error,
    items,
    summaryCards,
    canGoPrev,
    canGoNext,
    submit,
    reset,
    setView,
    goToPage,
    setPageSize,
  }
}

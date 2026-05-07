import { computed, ref, type Ref } from 'vue'
import { cancelAdminOrder, getAdminOrder, listAdminOrders } from '../services/orderApi'
import { getAdminPaymentByOrderId } from '../services/paymentApi'
import type { PersistedOpsSession } from '../types/api/auth'
import type { AdminOrderDetailResponse, AdminOrderSummaryResponse } from '../types/api/order'
import type { PaymentResponse } from '../types/api/payment'
import {
  applyAdminPaymentToDetail,
  applyAdminPaymentToSummaries,
  buildAdminCancelOrderRequest,
  buildAdminOrderQuery,
  canCancelAdminOrder,
  createDefaultOrderFilters,
  createSubmissionGate,
  toAdminOrderError,
  type AdminOrderErrorState,
  type AdminOrderFilterDraft,
} from '../views/admin/orderManagementModel'

interface UseOrderManagementOptions {
  createRequestId?: () => string
  initialFilters?: AdminOrderFilterDraft | null
  initialOrderId?: number | null
}

export function useOrderManagement(
  session: Ref<PersistedOpsSession | null>,
  canAccessWorkspace: Ref<boolean>,
  options: UseOrderManagementOptions = {},
) {
  const filters = ref<AdminOrderFilterDraft>(options.initialFilters ?? createDefaultOrderFilters())
  const items = ref<AdminOrderSummaryResponse[]>([])
  const total = ref(0)
  const detail = ref<AdminOrderDetailResponse | null>(null)
  const payment = ref<PaymentResponse | null>(null)
  const listError = ref<AdminOrderErrorState | null>(null)
  const detailError = ref<AdminOrderErrorState | null>(null)
  const paymentError = ref<AdminOrderErrorState | null>(null)
  const actionError = ref<AdminOrderErrorState | null>(null)
  const isLoadingList = ref(false)
  const isLoadingDetail = ref(false)
  const isRefreshingPayment = ref(false)
  const listGate = createSubmissionGate()
  const actionGate = createSubmissionGate()
  const selectedItem = computed(() => items.value.find((item) => item.orderId === detail.value?.orderId) ?? null)
  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / Math.max(1, filters.value.size))))
  const canCancelSelectedOrder = computed(() => Boolean(detail.value && canCancelAdminOrder(detail.value.status)))
  const isActionPending = computed(() => actionGate.isPending())

  async function bootstrap(orderId = options.initialOrderId ?? null) {
    if (!canAccessWorkspace.value || !session.value) {
      return
    }
    await refreshList()
    if (orderId && orderId > 0) {
      await selectOrder(orderId)
    }
  }

  async function refreshList(selectFirst = false) {
    if (!canAccessWorkspace.value || !listGate.begin()) {
      return
    }
    isLoadingList.value = true
    listError.value = null
    try {
      const result = await listAdminOrders(buildAdminOrderQuery(filters.value))
      items.value = result.data.items
      total.value = result.data.total
      filters.value = {
        ...filters.value,
        page: result.data.page,
        size: result.data.size,
      }
      if (selectFirst && result.data.items.length > 0) {
        await selectOrder(result.data.items[0].orderId)
      }
      if (result.data.items.length === 0) {
        detail.value = null
        payment.value = null
      }
    } catch (caught) {
      listError.value = toAdminOrderError(caught, 'Unable to load admin orders.')
      items.value = []
      total.value = 0
    } finally {
      isLoadingList.value = false
      listGate.end()
    }
  }

  async function selectOrder(orderId: number) {
    if (!canAccessWorkspace.value) {
      return
    }
    isLoadingDetail.value = true
    detailError.value = null
    paymentError.value = null
    actionError.value = null
    try {
      const result = await getAdminOrder(orderId)
      detail.value = result.data
      await refreshPaymentStatus(false)
    } catch (caught) {
      detailError.value = toAdminOrderError(caught, 'Unable to load order detail.')
      detail.value = null
      payment.value = null
    } finally {
      isLoadingDetail.value = false
    }
  }

  async function refreshDetail() {
    if (!detail.value) {
      await refreshList()
      return
    }
    await selectOrder(detail.value.orderId)
    await refreshList()
  }

  async function refreshPaymentStatus(showMissingError = true) {
    if (!detail.value || isRefreshingPayment.value) {
      return null
    }
    isRefreshingPayment.value = true
    paymentError.value = null
    try {
      const result = await getAdminPaymentByOrderId(detail.value.orderId)
      payment.value = result.data
      detail.value = applyAdminPaymentToDetail(detail.value, result.data)
      items.value = applyAdminPaymentToSummaries(items.value, result.data)
      return result.data
    } catch (caught) {
      const error = toAdminOrderError(caught, 'Unable to refresh payment status.')
      payment.value = null
      if (showMissingError || error.code !== 'PAYMENT_NOT_FOUND') {
        paymentError.value = error
      }
      return null
    } finally {
      isRefreshingPayment.value = false
    }
  }

  async function cancelSelectedOrder() {
    if (!detail.value || !canCancelSelectedOrder.value || !actionGate.begin()) {
      return false
    }
    actionError.value = null
    const requestId = (options.createRequestId ?? createRequestId)()
    try {
      const result = await cancelAdminOrder(
        detail.value.orderId,
        buildAdminCancelOrderRequest(requestId),
      )
      detail.value = result.data
      await refreshPaymentStatus(false)
      await refreshList()
      return true
    } catch (caught) {
      actionError.value = toAdminOrderError(caught, 'Unable to cancel order.')
      return false
    } finally {
      actionGate.end()
    }
  }

  function updateFilters(patch: Partial<AdminOrderFilterDraft>) {
    filters.value = {
      ...filters.value,
      ...patch,
      page: patch.page ?? 1,
    }
  }

  function goToPage(page: number) {
    filters.value = {
      ...filters.value,
      page,
    }
    void refreshList()
  }

  function retry() {
    if (detail.value?.orderId) {
      void selectOrder(detail.value.orderId)
      return
    }
    void refreshList()
  }

  return {
    filters,
    items,
    total,
    totalPages,
    detail,
    payment,
    selectedItem,
    listError,
    detailError,
    paymentError,
    actionError,
    isLoadingList,
    isLoadingDetail,
    isRefreshingPayment,
    isActionPending,
    canCancelSelectedOrder,
    bootstrap,
    refreshList,
    selectOrder,
    refreshDetail,
    refreshPaymentStatus,
    cancelSelectedOrder,
    updateFilters,
    goToPage,
    retry,
  }
}

function createRequestId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID()
  }
  return `adm-order-${Date.now()}`
}

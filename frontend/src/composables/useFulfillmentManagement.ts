import { computed, reactive, ref, type Ref } from 'vue'
import { getAdminFulfillment, listAdminFulfillments, shipAdminFulfillment } from '../services/fulfillmentApi'
import type { PersistedOpsSession } from '../types/api/auth'
import type { AdminFulfillmentResponse } from '../types/api/order'
import {
  buildAdminFulfillmentQuery,
  buildShipFulfillmentRequest,
  canShipFulfillment,
  createDefaultFulfillmentFilters,
  createShipmentGate,
  toFulfillmentError,
  type FulfillmentErrorState,
  type FulfillmentFilterDraft,
} from '../views/admin/fulfillmentManagementModel'

interface UseFulfillmentManagementOptions {
  createRequestId?: () => string
}

export function useFulfillmentManagement(
  session: Ref<PersistedOpsSession | null>,
  canAccessWorkspace: Ref<boolean>,
  options: UseFulfillmentManagementOptions = {},
) {
  const filters = ref<FulfillmentFilterDraft>(createDefaultFulfillmentFilters())
  const items = ref<AdminFulfillmentResponse[]>([])
  const total = ref(0)
  const detail = ref<AdminFulfillmentResponse | null>(null)
  const listError = ref<FulfillmentErrorState | null>(null)
  const detailError = ref<FulfillmentErrorState | null>(null)
  const actionError = ref<FulfillmentErrorState | null>(null)
  const isLoadingList = ref(false)
  const isLoadingDetail = ref(false)
  const listGate = createShipmentGate()
  const actionGate = createShipmentGate()
  const shipDraft = reactive({
    carrier: '',
    trackingNo: '',
  })
  const selectedItem = computed(() => items.value.find((item) => item.orderId === detail.value?.orderId) ?? null)
  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / Math.max(1, filters.value.size))))
  const canShipSelected = computed(() => Boolean(detail.value && canShipFulfillment(detail.value.fulfillmentStatus, detail.value.status)))
  const isActionPending = computed(() => actionGate.isPending())

  async function bootstrap() {
    if (!canAccessWorkspace.value || !session.value) {
      return
    }
    await refreshList(true)
  }

  async function refreshList(selectFirst = false) {
    if (!canAccessWorkspace.value || !listGate.begin()) {
      return
    }
    isLoadingList.value = true
    listError.value = null
    try {
      const result = await listAdminFulfillments(buildAdminFulfillmentQuery(filters.value))
      items.value = result.data.items
      total.value = result.data.total
      filters.value = {
        ...filters.value,
        page: result.data.page,
        size: result.data.size,
      }
      if (selectFirst && result.data.items.length > 0) {
        await selectFulfillment(result.data.items[0].orderId)
      }
      if (result.data.items.length === 0) {
        detail.value = null
        resetShipDraft()
      }
    } catch (caught) {
      listError.value = toFulfillmentError(caught, 'Unable to load fulfillments.')
      items.value = []
      total.value = 0
    } finally {
      isLoadingList.value = false
      listGate.end()
    }
  }

  async function selectFulfillment(orderId: number) {
    if (!canAccessWorkspace.value) {
      return
    }
    isLoadingDetail.value = true
    detailError.value = null
    actionError.value = null
    try {
      const result = await getAdminFulfillment(orderId)
      detail.value = result.data
      resetShipDraft()
    } catch (caught) {
      detailError.value = toFulfillmentError(caught, 'Unable to load fulfillment detail.')
      detail.value = null
    } finally {
      isLoadingDetail.value = false
    }
  }

  async function refreshDetail() {
    if (!detail.value) {
      await refreshList(true)
      return
    }
    await selectFulfillment(detail.value.orderId)
    await refreshList()
  }

  async function shipSelectedFulfillment() {
    if (!detail.value || !canShipSelected.value || !actionGate.begin()) {
      return false
    }
    actionError.value = null
    const requestId = (options.createRequestId ?? createRequestId)()
    try {
      const result = await shipAdminFulfillment(
        detail.value.orderId,
        buildShipFulfillmentRequest(requestId, shipDraft.carrier, shipDraft.trackingNo),
      )
      detail.value = result.data
      resetShipDraft()
      await refreshList()
      return true
    } catch (caught) {
      actionError.value = toFulfillmentError(caught, 'Unable to ship order.')
      return false
    } finally {
      actionGate.end()
    }
  }

  function updateFilters(patch: Partial<FulfillmentFilterDraft>) {
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
      void selectFulfillment(detail.value.orderId)
      return
    }
    void refreshList(true)
  }

  function resetShipDraft() {
    shipDraft.carrier = ''
    shipDraft.trackingNo = ''
  }

  return {
    filters,
    items,
    total,
    totalPages,
    detail,
    selectedItem,
    shipDraft,
    listError,
    detailError,
    actionError,
    isLoadingList,
    isLoadingDetail,
    isActionPending,
    canShipSelected,
    bootstrap,
    refreshList,
    selectFulfillment,
    refreshDetail,
    shipSelectedFulfillment,
    updateFilters,
    goToPage,
    retry,
  }
}

function createRequestId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID()
  }
  return `adm-ship-${Date.now()}`
}

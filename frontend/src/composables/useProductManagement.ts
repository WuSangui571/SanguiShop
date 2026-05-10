import { computed, ref, type Ref } from 'vue'
import {
  adjustSkuStock,
  createProduct,
  getAdminProduct,
  listAdminProducts,
  updateProduct,
  updateProductStatus,
} from '../services/productApi'
import type { PersistedOpsSession } from '../types/api/auth'
import type { ProductAdminStatusFilter, ProductDetailResponse, ProductStatus } from '../types/api/product'
import {
  buildCreateProductRequest,
  buildStatusUpdateRequest,
  buildStockAdjustmentRequest,
  buildUpdateProductRequest,
  createDraftFromDetail,
  createEmptyProductDraft,
  createSubmissionGate,
  normalizeProductDraft,
  toProductAdminError,
  validateProductDraft,
  type ProductAdminErrorState,
  type ProductFormDraft,
  type ProductSkuDraft,
} from '../views/admin/productManagementModel'

interface ProductAdminListItemView {
  productId: number
  productName: string
  productDescription: string
  minPriceCent: number
  maxPriceCent: number
  status: ProductStatus
  skuCount: number
  availableStockTotal: number
  reservedStockTotal: number
}

export function useProductManagement(session: Ref<PersistedOpsSession | null>, canAccessWorkspace: Ref<boolean>) {
  const items = ref<ProductAdminListItemView[]>([])
  const detail = ref<ProductDetailResponse | null>(null)
  const draft = ref<ProductFormDraft>(createEmptyProductDraft())
  const filterStatus = ref<ProductAdminStatusFilter>('all')
  const listError = ref<ProductAdminErrorState | null>(null)
  const detailError = ref<ProductAdminErrorState | null>(null)
  const actionError = ref<ProductAdminErrorState | null>(null)
  const isLoadingList = ref(false)
  const isLoadingDetail = ref(false)
  const listGate = createSubmissionGate()
  const actionGate = createSubmissionGate()
  const validation = computed(() => validateProductDraft(draft.value))
  const isSaving = computed(() => listGate.isPending() || actionGate.isPending())
  const canSubmit = computed(() => validation.value.valid && !isSaving.value)
  const selectedItem = computed(() => items.value.find((item) => item.productId === detail.value?.productId) ?? null)

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
      const result = await listAdminProducts({
        page: 1,
        size: 20,
        status: filterStatus.value,
      })
      items.value = result.data.items
      if (selectFirst && result.data.items.length > 0) {
        await selectProduct(result.data.items[0].productId)
      }
      if (result.data.items.length === 0) {
        detail.value = null
        draft.value = createEmptyProductDraft()
      }
    } catch (caught) {
      listError.value = toProductAdminError(caught, 'Unable to load admin products.')
      items.value = []
    } finally {
      isLoadingList.value = false
      listGate.end()
    }
  }

  async function selectProduct(productId: number) {
    if (!canAccessWorkspace.value || !session.value) {
      return
    }
    isLoadingDetail.value = true
    detailError.value = null
    actionError.value = null
    try {
      const result = await getAdminProduct(productId)
      detail.value = result.data
      draft.value = createDraftFromDetail(result.data)
    } catch (caught) {
      detailError.value = toProductAdminError(caught, 'Unable to load product detail.')
      detail.value = null
    } finally {
      isLoadingDetail.value = false
    }
  }

  function createNewProduct() {
    detail.value = null
    detailError.value = null
    actionError.value = null
    draft.value = createEmptyProductDraft()
  }

  function addSku() {
    draft.value = {
      ...draft.value,
      skus: [...draft.value.skus, createEmptySkuDraft()],
    }
  }

  function removeSku(index: number) {
    draft.value = {
      ...draft.value,
      skus: draft.value.skus.filter((_, itemIndex) => itemIndex !== index),
    }
  }

  function updateSku(index: number, patch: Partial<ProductSkuDraft>) {
    draft.value = {
      ...draft.value,
      skus: draft.value.skus.map((sku, itemIndex) => itemIndex === index ? { ...sku, ...patch } : sku),
    }
  }

  function updateDraft(patch: Partial<ProductFormDraft>) {
    draft.value = {
      ...draft.value,
      ...patch,
    }
  }

  async function saveDraft() {
    if (!canAccessWorkspace.value || !actionGate.begin()) {
      return false
    }
    actionError.value = null
    const validationResult = validateProductDraft(draft.value)
    if (!validationResult.valid) {
      actionGate.end()
      return false
    }

    const normalized = normalizeProductDraft(draft.value)
    try {
      let nextProductId = normalized.productId
      const currentSession = session.value
      if (!currentSession) {
        actionError.value = {
          code: 'AUTH_TOKEN_MISSING',
          message: 'Missing admin session.',
          traceId: null,
        }
        return false
      }
      if (normalized.productId === null) {
        const request = buildCreateProductRequest(currentSession.shopId, String(currentSession.userId), draft.value)
        const result = await createProduct(request)
        nextProductId = result.data.productId
      } else {
        const request = buildUpdateProductRequest(currentSession.shopId, String(currentSession.userId), draft.value)
        const result = await updateProduct(request)
        nextProductId = result.data.productId
      }
      await refreshList()
      if (nextProductId !== null) {
        await selectProduct(nextProductId)
      }
      return true
    } catch (caught) {
      actionError.value = toProductAdminError(caught, 'Unable to save product.')
      return false
    } finally {
      actionGate.end()
    }
  }

  async function changeStatus(nextStatus: ProductStatus) {
    if (!canAccessWorkspace.value || !detail.value?.productId || !actionGate.begin()) {
      return false
    }
    actionError.value = null
    try {
      const result = await updateProductStatus(detail.value.productId, buildStatusUpdateRequest(nextStatus, createRequestId()))
      detail.value = result.data
      draft.value = createDraftFromDetail(result.data)
      await refreshList()
      return true
    } catch (caught) {
      actionError.value = toProductAdminError(caught, 'Unable to update status.')
      return false
    } finally {
      actionGate.end()
    }
  }

  async function adjustStock(skuId: number, availableStock: number) {
    if (!canAccessWorkspace.value || !detail.value?.productId || !actionGate.begin()) {
      return false
    }
    actionError.value = null
    try {
      const result = await adjustSkuStock(
        detail.value.productId,
        skuId,
        buildStockAdjustmentRequest(availableStock, createRequestId()),
      )
      detail.value = result.data
      draft.value = createDraftFromDetail(result.data)
      await refreshList()
      return true
    } catch (caught) {
      actionError.value = toProductAdminError(caught, 'Unable to adjust stock.')
      return false
    } finally {
      actionGate.end()
    }
  }

  function retry() {
    if (detail.value?.productId) {
      void selectProduct(detail.value.productId)
      return
    }
    void refreshList()
  }

  function setFilterStatus(nextStatus: ProductAdminStatusFilter) {
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
    canSubmit,
    bootstrap,
    refreshList,
    selectProduct,
    createNewProduct,
    addSku,
    removeSku,
    updateSku,
    updateDraft,
    saveDraft,
    changeStatus,
    adjustStock,
    retry,
    setFilterStatus,
  }
}

function createEmptySkuDraft(): ProductSkuDraft {
  return {
    skuId: null,
    skuCode: '',
    skuName: '',
    priceCent: null,
    availableStock: null,
  }
}

function createRequestId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID()
  }
  return `req-${Date.now()}`
}

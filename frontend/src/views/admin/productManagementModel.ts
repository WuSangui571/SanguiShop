import { ref } from 'vue'
import { HttpClientError } from '../../services/httpClient'
import { formatMoney } from '../../utils/format'
import type {
  ProductAdminSummaryResponse,
  ProductDetailResponse,
  ProductSkuDraftRequest,
  ProductSkuResponse,
  ProductSkuStockAdjustmentRequest,
  ProductStatus,
  ProductStatusUpdateRequest,
  ProductUpdateRequest,
  ProductCreateRequest,
} from '../../types/api/product'

export interface ProductAdminErrorState {
  code: string
  message: string
  traceId: string | null
}

export interface ProductSkuDraft {
  skuId: number | null
  skuCode: string
  skuName: string
  priceCent: number | null
  availableStock: number | null
}

export interface ProductFormDraft {
  productId: number | null
  productName: string
  productDescription: string
  status: ProductStatus
  skus: ProductSkuDraft[]
}

export interface ProductSkuFieldErrors {
  skuCode: string
  skuName: string
  priceCent: string
  availableStock: string
}

export interface ProductFormValidationResult {
  valid: boolean
  errors: {
    productName: string
    productDescription: string
    status: string
    skus: ProductSkuFieldErrors[]
  }
}

export function createEmptyProductDraft(): ProductFormDraft {
  return {
    productId: null,
    productName: '',
    productDescription: '',
    status: 'draft',
    skus: [createEmptySkuDraft()],
  }
}

export function createEmptySkuDraft(): ProductSkuDraft {
  return {
    skuId: null,
    skuCode: '',
    skuName: '',
    priceCent: null,
    availableStock: null,
  }
}

export function createDraftFromDetail(detail: ProductDetailResponse): ProductFormDraft {
  return {
    productId: detail.productId,
    productName: detail.productName,
    productDescription: detail.productDescription,
    status: detail.status,
    skus: detail.skus.map((sku) => mapSkuResponseToDraft(sku)),
  }
}

export function normalizeProductDraft(draft: ProductFormDraft): ProductFormDraft {
  return {
    productId: draft.productId,
    productName: draft.productName.trim(),
    productDescription: draft.productDescription.trim(),
    status: draft.status,
    skus: draft.skus.map((sku) => ({
      skuId: sku.skuId,
      skuCode: sku.skuCode.trim(),
      skuName: sku.skuName.trim(),
      priceCent: normalizeNumber(sku.priceCent),
      availableStock: normalizeNumber(sku.availableStock),
    })),
  }
}

export function validateProductDraft(draft: ProductFormDraft): ProductFormValidationResult {
  const normalized = normalizeProductDraft(draft)
  const skuErrors = normalized.skus.map(() => ({
    skuCode: '',
    skuName: '',
    priceCent: '',
    availableStock: '',
  }))

  const errors = {
    productName: normalized.productName ? '' : 'productName required',
    productDescription: '',
    status: normalized.status ? '' : 'status required',
    skus: skuErrors,
  }

  if (!normalized.productName) {
    errors.productName = 'productName required'
  }

  if (normalized.skus.length === 0) {
    return {
      valid: false,
      errors: {
        ...errors,
        skus: [{
          skuCode: 'sku required',
          skuName: 'sku required',
          priceCent: 'sku required',
          availableStock: 'sku required',
        }],
      },
    }
  }

  const seenCodes = new Map<string, number>()
  normalized.skus.forEach((sku, index) => {
    const priceCent = normalizeNumber(sku.priceCent)
    const availableStock = normalizeNumber(sku.availableStock)

    if (!sku.skuCode) {
      errors.skus[index].skuCode = 'skuCode required'
    }
    if (!sku.skuName) {
      errors.skus[index].skuName = 'skuName required'
    }
    if (!isPositiveInteger(priceCent)) {
      errors.skus[index].priceCent = 'priceCent must be a positive integer'
    }
    if (!isNonNegativeInteger(availableStock)) {
      errors.skus[index].availableStock = 'availableStock must be a non-negative integer'
    }

    const duplicateIndex = seenCodes.get(sku.skuCode.toLowerCase())
    if (duplicateIndex !== undefined) {
      errors.skus[index].skuCode = 'duplicate skuCode'
      errors.skus[duplicateIndex].skuCode = 'duplicate skuCode'
    } else if (sku.skuCode) {
      seenCodes.set(sku.skuCode.toLowerCase(), index)
    }
  })

  return {
    valid: errors.productName === ''
      && errors.skus.every((sku) => sku.skuCode === '' && sku.skuName === '' && sku.priceCent === '' && sku.availableStock === ''),
    errors,
  }
}

export function buildCreateProductRequest(shopId: number, userId: string, draft: ProductFormDraft): ProductCreateRequest {
  const normalized = normalizeProductDraft(draft)
  return {
    shopId,
    userId,
    productName: normalized.productName,
    productDescription: normalized.productDescription,
    skus: normalized.skus.map((sku) => mapSkuDraftToRequest(sku)),
  }
}

export function buildUpdateProductRequest(shopId: number, userId: string, draft: ProductFormDraft): ProductUpdateRequest {
  const normalized = normalizeProductDraft(draft)
  if (normalized.productId === null) {
    throw new Error('productId is required')
  }
  return {
    productId: normalized.productId,
    shopId,
    userId,
    productName: normalized.productName,
    productDescription: normalized.productDescription,
    skus: normalized.skus.map((sku) => mapSkuDraftToRequest(sku)),
  }
}

export function buildStatusUpdateRequest(status: ProductStatus, requestId: string): ProductStatusUpdateRequest {
  return { status, requestId }
}

export function buildStockAdjustmentRequest(availableStock: number, requestId: string): ProductSkuStockAdjustmentRequest {
  return { availableStock, requestId }
}

export function toProductAdminError(
  caught: unknown,
  fallback: string,
  fallbackCode = 'UNEXPECTED_ERROR',
): ProductAdminErrorState {
  if (caught instanceof HttpClientError) {
    return {
      code: caught.code,
      message: caught.message,
      traceId: caught.traceId,
    }
  }

  return {
    code: fallbackCode,
    message: fallback,
    traceId: null,
  }
}

export function summarizeAdminProduct(item: ProductAdminSummaryResponse) {
  const price = item.minPriceCent === item.maxPriceCent
    ? formatMoney(item.minPriceCent)
    : `${formatMoney(item.minPriceCent)} - ${formatMoney(item.maxPriceCent)}`
  return {
    price,
    skuCount: item.skuCount,
    availableStockTotal: item.availableStockTotal,
    reservedStockTotal: item.reservedStockTotal,
  }
}

export function createSubmissionGate() {
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

  return {
    begin,
    end,
    isPending,
  }
}

function mapSkuResponseToDraft(sku: ProductSkuResponse): ProductSkuDraft {
  return {
    skuId: sku.skuId,
    skuCode: sku.skuCode,
    skuName: sku.skuName,
    priceCent: sku.priceCent,
    availableStock: sku.availableStock,
  }
}

function mapSkuDraftToRequest(sku: ProductSkuDraft): ProductSkuDraftRequest {
  return {
    skuCode: sku.skuCode,
    skuName: sku.skuName,
    priceCent: normalizeNumber(sku.priceCent),
    availableStock: normalizeNumber(sku.availableStock),
  }
}

function normalizeNumber(value: number | null): number {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return 0
  }
  return Math.trunc(value)
}

function isPositiveInteger(value: number): boolean {
  return Number.isInteger(value) && value > 0
}

function isNonNegativeInteger(value: number): boolean {
  return Number.isInteger(value) && value >= 0
}

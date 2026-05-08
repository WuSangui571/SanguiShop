import { getJson, postJson, putJson } from './httpClient'
import type {
  ProductAdminListResponse,
  ProductAdminStatusFilter,
  ProductCreateRequest,
  ProductDetailResponse,
  ProductListResponse,
  ProductReviewPageResponse,
  ProductSkuStockAdjustmentRequest,
  ProductStatusUpdateRequest,
  ProductUpdateRequest,
} from '../types/api/product'

export function listProducts(payload: { page: number; size: number }) {
  return getJson<ProductListResponse>('/api/products', payload, { authContext: 'none' })
}

export function getProduct(productId: number) {
  return getJson<ProductDetailResponse>(`/api/products/${productId}`, {}, { authContext: 'none' })
}

export function listProductReviews(productId: number, payload: { page: number; size: number }) {
  return getJson<ProductReviewPageResponse>(
    `/api/products/${encodeURIComponent(String(productId))}/reviews`,
    payload,
    { authContext: 'none' },
  )
}

export function listAdminProducts(payload: { page: number; size: number; status: ProductAdminStatusFilter }) {
  const query: Record<string, string | number> = {
    page: payload.page,
    size: payload.size,
  }
  if (payload.status !== 'all') {
    query.status = payload.status
  }

  return getJson<ProductAdminListResponse>('/api/admin/products', query, { authContext: 'ops' })
}

export function getAdminProduct(productId: number) {
  return getJson<ProductDetailResponse>(`/api/admin/products/${productId}`, {}, { authContext: 'ops' })
}

export function createProduct(payload: ProductCreateRequest) {
  return postJson<ProductDetailResponse>('/api/admin/products', payload, { authContext: 'ops' })
}

export function updateProduct(payload: ProductUpdateRequest) {
  const { productId, ...body } = payload
  return putJson<ProductDetailResponse>(`/api/admin/products/${productId}`, body, { authContext: 'ops' })
}

export function updateProductStatus(productId: number, payload: ProductStatusUpdateRequest) {
  return postJson<ProductDetailResponse>(`/api/admin/products/${productId}/status`, payload, { authContext: 'ops' })
}

export function adjustSkuStock(productId: number, skuId: number, payload: ProductSkuStockAdjustmentRequest) {
  return postJson<ProductDetailResponse>(
    `/api/admin/products/${productId}/skus/${skuId}/stock-adjustments`,
    payload,
    { authContext: 'ops' },
  )
}

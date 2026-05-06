import { getJson } from './httpClient'
import type { ProductDetailResponse, ProductListResponse } from '../types/api/product'

export function listProducts(payload: { page: number; size: number }) {
  return getJson<ProductListResponse>('/api/products', payload, { authContext: 'none' })
}

export function getProduct(productId: number) {
  return getJson<ProductDetailResponse>(`/api/products/${productId}`, {}, { authContext: 'none' })
}

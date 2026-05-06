import type { PageResponse } from './common'

export type ProductStatus = 'draft' | 'active' | 'inactive' | string

export interface ProductSummaryResponse {
  productId: number
  productName: string
  productDescription: string
  minPriceCent: number
  maxPriceCent: number
  status: ProductStatus
}

export interface ProductSkuResponse {
  skuId: number
  skuCode: string
  skuName: string
  priceCent: number
  availableStock: number
  reservedStock: number
}

export interface ProductDetailResponse {
  productId: number
  productName: string
  productDescription: string
  status: ProductStatus
  skus: ProductSkuResponse[]
}

export type ProductListResponse = PageResponse<ProductSummaryResponse>

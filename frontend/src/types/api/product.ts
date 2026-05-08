import type { PageResponse } from './common'

export type ProductStatus = 'draft' | 'active' | 'inactive' | string
export type ProductAdminStatusFilter = ProductStatus | 'all'

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

export interface ProductReviewItemResponse {
  reviewId: number
  rating: number
  content: string | null
  imageUrls: string[]
  createdAt: string
  maskedUserId: string
  skuName: string
}

export interface ProductReviewPageResponse {
  productId: number
  averageRating: number
  reviewCount: number
  page: number
  size: number
  items: ProductReviewItemResponse[]
}

export interface ProductAdminSummaryResponse {
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

export interface ProductSkuDraftRequest {
  skuCode: string
  skuName: string
  priceCent: number
  availableStock: number
}

export interface ProductCreateRequest {
  shopId: number
  userId: string
  productName: string
  productDescription: string
  skus: ProductSkuDraftRequest[]
}

export interface ProductUpdateRequest extends ProductCreateRequest {
  productId: number
}

export interface ProductStatusUpdateRequest {
  status: ProductStatus
  requestId: string
}

export interface ProductSkuStockAdjustmentRequest {
  availableStock: number
  requestId: string
}

export type ProductListResponse = PageResponse<ProductSummaryResponse>
export type ProductAdminListResponse = PageResponse<ProductAdminSummaryResponse>

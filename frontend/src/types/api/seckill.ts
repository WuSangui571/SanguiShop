import type { PageResponse } from './common'

export type AdminSeckillActivityStatus = 'draft' | 'scheduled' | 'active' | 'ended' | string
export type AdminSeckillActivityStatusFilter = AdminSeckillActivityStatus | 'all'

export interface AdminSeckillActivitySummaryResponse {
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

export interface AdminSeckillActivitySkuResponse {
  productId: number
  productName: string
  skuId: number
  skuCode: string
  skuName: string
  priceCent: number
  seckillPriceCent: number
  availableStock: number
  activityStock: number
  soldCount: number
}

export interface AdminSeckillActivityDetailResponse extends AdminSeckillActivitySummaryResponse {
  description?: string | null
  skus: AdminSeckillActivitySkuResponse[]
}

export interface AdminSeckillActivityDraftRequest {
  shopId: number
  userId: string
  activityName: string
  description?: string | null
  startsAt: string
  endsAt: string
  skus: Array<{
    productId: number
    skuId: number
    activityStock: number
    seckillPriceCent: number
  }>
}

export interface AdminSeckillActivityStatusUpdateRequest {
  status: string
  requestId: string
}

export interface AdminSeckillActivityBindSkuRequest {
  productId: number
  skuId: number
  activityStock: number
  seckillPriceCent?: number
  requestId: string
}

export type AdminSeckillActivityListResponse = PageResponse<AdminSeckillActivitySummaryResponse>

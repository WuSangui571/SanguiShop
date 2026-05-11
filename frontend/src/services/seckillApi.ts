import { getJson, postJson, putJson } from './httpClient'
import type {
  AdminSeckillActivityDetailResponse,
  AdminSeckillActivityDraftRequest,
  AdminSeckillActivityListResponse,
  AdminSeckillActivityStatusFilter,
  AdminSeckillActivityStatusUpdateRequest,
  AdminSeckillActivityBindSkuRequest,
} from '../types/api/seckill'

export function listAdminSeckillActivities(payload: { page: number; size: number; status: AdminSeckillActivityStatusFilter }) {
  const query: Record<string, string | number> = {
    page: payload.page,
    size: payload.size,
  }
  if (payload.status !== 'all') {
    query.status = payload.status
  }

  return getJson<AdminSeckillActivityListResponse>('/api/admin/seckill/activities', query, { authContext: 'ops' })
}

export function getAdminSeckillActivity(activityId: number) {
  return getJson<AdminSeckillActivityDetailResponse>(`/api/admin/seckill/activities/${activityId}`, {}, { authContext: 'ops' })
}

export function createAdminSeckillActivity(payload: AdminSeckillActivityDraftRequest) {
  return postJson<AdminSeckillActivityDetailResponse>('/api/admin/seckill/activities', payload, { authContext: 'ops' })
}

export function updateAdminSeckillActivity(activityId: number, payload: AdminSeckillActivityDraftRequest) {
  return putJson<AdminSeckillActivityDetailResponse>(`/api/admin/seckill/activities/${activityId}`, payload, { authContext: 'ops' })
}

export function updateAdminSeckillActivityStatus(activityId: number, payload: AdminSeckillActivityStatusUpdateRequest) {
  return postJson<AdminSeckillActivityDetailResponse>(`/api/admin/seckill/activities/${activityId}/status`, payload, { authContext: 'ops' })
}

export function bindAdminSeckillActivitySku(activityId: number, payload: AdminSeckillActivityBindSkuRequest) {
  return postJson<AdminSeckillActivityDetailResponse>(`/api/admin/seckill/activities/${activityId}/skus`, payload, { authContext: 'ops' })
}

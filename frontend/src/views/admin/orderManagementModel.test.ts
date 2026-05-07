import { describe, expect, it } from 'vitest'
import { HttpClientError } from '../../services/httpClient'
import {
  buildAdminCancelOrderRequest,
  buildAdminOrderQuery,
  canCancelAdminOrder,
  createDefaultOrderFilters,
  createSubmissionGate,
  getAdminOrderStatusLabel,
  toAdminOrderError,
} from './orderManagementModel'

describe('orderManagementModel', () => {
  it('builds trimmed filter payload and omits all or blank filters', () => {
    const filters = createDefaultOrderFilters()
    filters.status = 'all'
    filters.orderNo = ' ORD-001 '
    filters.userId = ' 10001 '
    filters.fromTime = '2026-05-01T10:00'
    filters.toTime = '2026-05-02T10:00:00+08:00'

    expect(buildAdminOrderQuery(filters)).toEqual({
      page: 1,
      size: 20,
      orderNo: 'ORD-001',
      userId: '10001',
      fromTime: '2026-05-01T10:00:00+08:00',
      toTime: '2026-05-02T10:00:00+08:00',
    })
  })

  it('clamps pagination payload', () => {
    const filters = createDefaultOrderFilters()
    filters.page = -1
    filters.size = 500
    filters.status = 'paid'

    expect(buildAdminOrderQuery(filters)).toEqual({
      page: 1,
      size: 100,
      status: 'paid',
    })
  })

  it('labels known statuses and falls back to raw unknown status', () => {
    const labels = {
      created: 'Unpaid',
      paid: 'Paid',
      cancelled: 'Cancelled',
    }

    expect(getAdminOrderStatusLabel('created', labels)).toBe('Unpaid')
    expect(getAdminOrderStatusLabel('paid', labels)).toBe('Paid')
    expect(getAdminOrderStatusLabel('refunding', labels)).toBe('refunding')
  })

  it('preserves backend error code message and traceId', () => {
    const error = toAdminOrderError(
      new HttpClientError('Order status invalid.', {
        code: 'ORDER_STATUS_INVALID',
        status: 409,
        traceId: 'trace-order-invalid',
      }),
      'fallback',
    )

    expect(error).toEqual({
      code: 'ORDER_STATUS_INVALID',
      message: 'Order status invalid.',
      traceId: 'trace-order-invalid',
    })
  })

  it('guards duplicate cancel submissions', () => {
    const gate = createSubmissionGate()

    expect(gate.begin()).toBe(true)
    expect(gate.begin()).toBe(false)
    gate.end()
    expect(gate.begin()).toBe(true)
  })

  it('builds cancel requestId and only allows created orders to cancel', () => {
    expect(buildAdminCancelOrderRequest(' adm-cancel-001 ')).toEqual({ requestId: 'adm-cancel-001' })
    expect(canCancelAdminOrder('created')).toBe(true)
    expect(canCancelAdminOrder('paid')).toBe(false)
    expect(canCancelAdminOrder('cancelled')).toBe(false)
  })
})

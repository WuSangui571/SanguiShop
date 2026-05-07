import { describe, expect, it } from 'vitest'
import { HttpClientError } from '../../services/httpClient'
import {
  buildAdminFulfillmentQuery,
  buildShipFulfillmentRequest,
  canShipFulfillment,
  createDefaultFulfillmentFilters,
  createShipmentGate,
  getFulfillmentStatusLabel,
  toFulfillmentError,
} from './fulfillmentManagementModel'

describe('fulfillmentManagementModel', () => {
  it('builds trimmed filter payload and omits all or blank filters', () => {
    const filters = createDefaultFulfillmentFilters()
    filters.status = 'all'
    filters.orderNo = ' ORD-001 '
    filters.userId = ' 10001 '
    filters.fromTime = '2026-05-07T10:00'
    filters.toTime = '2026-05-07T18:00:00+08:00'

    expect(buildAdminFulfillmentQuery(filters)).toEqual({
      page: 1,
      size: 20,
      orderNo: 'ORD-001',
      userId: '10001',
      fromTime: '2026-05-07T10:00:00+08:00',
      toTime: '2026-05-07T18:00:00+08:00',
    })
  })

  it('labels known statuses and falls back to raw unknown status', () => {
    const labels = {
      unshipped: 'Awaiting shipment',
      shipped: 'Shipped',
    }

    expect(getFulfillmentStatusLabel('unshipped', labels)).toBe('Awaiting shipment')
    expect(getFulfillmentStatusLabel('shipped', labels)).toBe('Shipped')
    expect(getFulfillmentStatusLabel('delivered', labels)).toBe('delivered')
  })

  it('builds trimmed ship payload', () => {
    expect(buildShipFulfillmentRequest(' req-1 ', ' SF Express ', ' SF123 ')).toEqual({
      requestId: 'req-1',
      carrier: 'SF Express',
      trackingNo: 'SF123',
    })
  })

  it('preserves backend error code message and traceId', () => {
    const error = toFulfillmentError(
      new HttpClientError('Order status invalid.', {
        code: 'ORDER_STATUS_INVALID',
        status: 409,
        traceId: 'trace-ship-invalid',
      }),
      'fallback',
    )

    expect(error).toEqual({
      code: 'ORDER_STATUS_INVALID',
      message: 'Order status invalid.',
      traceId: 'trace-ship-invalid',
    })
  })

  it('guards duplicate ship submissions and only allows unshipped rows', () => {
    const gate = createShipmentGate()

    expect(gate.begin()).toBe(true)
    expect(gate.begin()).toBe(false)
    gate.end()
    expect(gate.begin()).toBe(true)
    expect(canShipFulfillment('unshipped')).toBe(true)
    expect(canShipFulfillment('shipped')).toBe(false)
  })
})

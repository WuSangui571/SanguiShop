import { describe, expect, it } from 'vitest'
import { HttpClientError } from '../../services/httpClient'
import {
  applyAdminPaymentToDetail,
  applyAdminPaymentToSummaries,
  buildAdminOrderSearchParams,
  buildAdminCancelOrderRequest,
  buildAdminOrderQuery,
  canCancelAdminOrder,
  createDefaultOrderFilters,
  createSubmissionGate,
  deriveAdminOrderTimeline,
  deserializeAdminOrderFilters,
  getAdminOrderTimelineDescription,
  getAdminOrderStatusLabel,
  readAdminOrderFiltersFromSearch,
  readAdminOrderIdFromSearch,
  serializeAdminOrderFilters,
  toAdminOrderError,
} from './orderManagementModel'
import type { AdminOrderDetailResponse, AdminOrderSummaryResponse } from '../../types/api/order'
import type { PaymentResponse } from '../../types/api/payment'

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
    expect(getAdminOrderStatusLabel('cancelled', labels)).toBe('Cancelled')
    expect(getAdminOrderStatusLabel('shipped', { ...labels, shipped: 'Shipped' })).toBe('Shipped')
    expect(getAdminOrderStatusLabel('completed', { ...labels, completed: 'Completed' })).toBe('Completed')
    expect(getAdminOrderStatusLabel('refunding', labels)).toBe('refunding')
  })

  it('reads order deep link and filter state from search params', () => {
    const filters = readAdminOrderFiltersFromSearch('?workspace=order&orderId=101&status=paid&orderNo= ORD-9 &userId=10001&from=2026-05-01T10:00&to=2026-05-02T10:00&page=3&size=50')

    expect(readAdminOrderIdFromSearch('?workspace=order&orderId=101')).toBe(101)
    expect(readAdminOrderIdFromSearch('?workspace=order&orderId=0')).toBeNull()
    expect(filters).toEqual({
      status: 'paid',
      orderNo: ' ORD-9 ',
      userId: '10001',
      fromTime: '2026-05-01T10:00',
      toTime: '2026-05-02T10:00',
      page: 3,
      size: 50,
    })
  })

  it('builds shareable order search params without all or blank filters', () => {
    const filters = createDefaultOrderFilters()
    filters.status = 'all'
    filters.orderNo = ' ORD-001 '
    filters.userId = ''
    filters.page = 2

    expect(buildAdminOrderSearchParams(filters, 101).toString()).toBe('workspace=order&orderNo=ORD-001&page=2&size=20&orderId=101')
  })

  it('serializes and restores order filters from session storage payload', () => {
    const filters = createDefaultOrderFilters()
    filters.status = 'cancelled'
    filters.orderNo = 'ORD-002'
    filters.page = 4

    expect(deserializeAdminOrderFilters(serializeAdminOrderFilters(filters))).toEqual(filters)
    expect(deserializeAdminOrderFilters('{"version":2}')).toBeNull()
    expect(deserializeAdminOrderFilters('not-json')).toBeNull()
  })

  it('derives operator-readable timeline descriptions', () => {
    const statusLabels = {
      created: 'Unpaid',
      paid: 'Paid',
      cancelled: 'Cancelled',
      shipped: 'Shipped',
      completed: 'Completed',
    }
    const timelineLabels = {
      created: 'Order was created and is awaiting payment.',
      paid: 'Payment was confirmed.',
      cancelled: 'Order was cancelled and release work should be complete.',
      shipped: 'Shipment was confirmed.',
      completed: 'Receipt was confirmed and the transaction is complete.',
      unknown: 'Backend returned an unrecognized status.',
    }

    expect(getAdminOrderTimelineDescription('created', timelineLabels)).toBe('Order was created and is awaiting payment.')
    expect(getAdminOrderTimelineDescription('paid', timelineLabels)).toBe('Payment was confirmed.')
    expect(getAdminOrderTimelineDescription('cancelled', timelineLabels)).toBe('Order was cancelled and release work should be complete.')
    expect(getAdminOrderTimelineDescription('shipped', timelineLabels)).toBe('Shipment was confirmed.')
    expect(getAdminOrderTimelineDescription('completed', timelineLabels)).toBe('Receipt was confirmed and the transaction is complete.')
    expect(getAdminOrderTimelineDescription('refunding', timelineLabels)).toBe('Backend returned an unrecognized status.')
    expect(deriveAdminOrderTimeline([
      { status: 'created', occurredAt: '2026-05-01T10:00:00+08:00', traceId: 'trace-created' },
      { status: 'shipped', occurredAt: '2026-05-02T10:00:00+08:00', traceId: 'trace-shipped' },
    ], statusLabels, timelineLabels)).toEqual([
      {
        status: 'created',
        statusLabel: 'Unpaid',
        occurredAt: '2026-05-01T10:00:00+08:00',
        traceId: 'trace-created',
        description: 'Order was created and is awaiting payment.',
      },
      {
        status: 'shipped',
        statusLabel: 'Shipped',
        occurredAt: '2026-05-02T10:00:00+08:00',
        traceId: 'trace-shipped',
        description: 'Shipment was confirmed.',
      },
    ])
    expect(deriveAdminOrderTimeline([
      { status: 'completed', occurredAt: '2026-05-03T10:00:00+08:00', traceId: 'trace-completed' },
    ], statusLabels, timelineLabels)).toEqual([
      {
        status: 'completed',
        statusLabel: 'Completed',
        occurredAt: '2026-05-03T10:00:00+08:00',
        traceId: 'trace-completed',
        description: 'Receipt was confirmed and the transaction is complete.',
      },
    ])
  })

  it('writes refreshed payment number into current detail and list item while preserving main status', () => {
    const detail: AdminOrderDetailResponse = {
      orderId: 101,
      orderNo: 'ORD-101',
      shopId: 1,
      userId: '10001',
      requestId: 'req-101',
      reservationNo: 'RSV-101',
      paymentNo: null,
      status: 'paid',
      totalAmountCent: 9900,
      traceId: 'trace-101',
      createdAt: '2026-05-01T10:00:00+08:00',
      updatedAt: '2026-05-01T10:00:00+08:00',
      items: [],
      statusTimeline: [],
    }
    const summaries: AdminOrderSummaryResponse[] = [
      {
        orderId: 101,
        orderNo: 'ORD-101',
        shopId: 1,
        userId: '10001',
        status: 'paid',
        totalAmountCent: 9900,
        paymentNo: null,
        itemCount: 1,
        traceId: 'trace-101',
        createdAt: '2026-05-01T10:00:00+08:00',
        updatedAt: '2026-05-01T10:00:00+08:00',
      },
      {
        orderId: 102,
        orderNo: 'ORD-102',
        shopId: 1,
        userId: '10001',
        status: 'created',
        totalAmountCent: 59900,
        paymentNo: null,
        itemCount: 1,
        traceId: 'trace-102',
        createdAt: '2026-05-01T10:00:00+08:00',
        updatedAt: '2026-05-01T10:00:00+08:00',
      },
    ]
    const payment: PaymentResponse = {
      paymentId: 201,
      paymentNo: 'PAY-201',
      orderId: 101,
      orderNo: 'ORD-101',
      shopId: 1,
      userId: '10001',
      channel: 'mock',
      status: 'paid',
      amountCent: 9900,
    }

    const mergedDetail = applyAdminPaymentToDetail(detail, payment)
    expect(mergedDetail).toMatchObject({
      paymentNo: 'PAY-201',
      status: 'paid',
    })

    const mergedSummaries = applyAdminPaymentToSummaries(summaries, payment)
    expect(mergedSummaries[0].paymentNo).toBe('PAY-201')
    expect(mergedSummaries[0].status).toBe('paid')
    expect(mergedSummaries[1].paymentNo).toBeNull()
    expect(mergedSummaries[1].status).toBe('created')
  })

  it('preserves shipped completed and cancelled main statuses after payment refresh returns paid or unknown status', () => {
    const testCases: { status: string; paymentNo: string | null; paymentStatus: string }[] = [
      { status: 'shipped', paymentNo: null, paymentStatus: 'paid' },
      { status: 'completed', paymentNo: null, paymentStatus: 'paid' },
      { status: 'cancelled', paymentNo: null, paymentStatus: 'paid' },
      { status: 'completed', paymentNo: 'PAY-EXISTING', paymentStatus: 'settling' },
    ]

    testCases.forEach(({ status, paymentNo, paymentStatus }) => {
      const detail: AdminOrderDetailResponse = {
        orderId: 101,
        orderNo: `ORD-${status}`,
        shopId: 1,
        userId: '10001',
        requestId: 'req-101',
        reservationNo: 'RSV-101',
        paymentNo,
        status,
        totalAmountCent: 9900,
        traceId: 'trace-101',
        createdAt: '2026-05-01T10:00:00+08:00',
        updatedAt: '2026-05-01T10:00:00+08:00',
        items: [],
        statusTimeline: [],
      }

      const summaries: AdminOrderSummaryResponse[] = [
        {
          orderId: 101,
          orderNo: `ORD-${status}`,
          shopId: 1,
          userId: '10001',
          status,
          totalAmountCent: 9900,
          paymentNo,
          itemCount: 1,
          traceId: 'trace-101',
          createdAt: '2026-05-01T10:00:00+08:00',
          updatedAt: '2026-05-01T10:00:00+08:00',
        },
      ]

      const payment: PaymentResponse = {
        paymentId: 201,
        paymentNo: 'PAY-201',
        orderId: 101,
        orderNo: `ORD-${status}`,
        shopId: 1,
        userId: '10001',
        channel: 'mock',
        status: paymentStatus,
        amountCent: 9900,
      }

      const mergedDetail = applyAdminPaymentToDetail(detail, payment)
      expect(mergedDetail).toMatchObject({
        status,
        paymentNo: 'PAY-201',
      })

      const mergedSummaries = applyAdminPaymentToSummaries(summaries, payment)
      expect(mergedSummaries[0].status).toBe(status)
      expect(mergedSummaries[0].paymentNo).toBe('PAY-201')
    })
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
    expect(canCancelAdminOrder('completed')).toBe(false)
  })
})

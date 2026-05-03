import { describe, expect, it } from 'vitest'
import {
  buildOrderQuery,
  buildPaymentQuery,
  deriveSummaryCards,
  type DashboardFilters,
} from '../src/views/admin/compensationDashboardModel'
import type {
  OrderCompensationQueryResponse,
  PaymentCompensationQueryResponse,
} from '../src/types/api/compensation'

function baseFilters(): DashboardFilters {
  return {
    shopId: '1',
    orderId: '101',
    paymentNo: 'PAY-001',
    trigger: 'manual',
    result: 'failed',
    operator: 'ops-user',
    traceId: 'trace-1',
    fromTime: '2026-05-03T12:00',
    toTime: '2026-05-03T13:00',
    pageNo: 2,
    pageSize: 20,
  }
}

describe('compensationDashboardModel', () => {
  it('builds order query payloads with optional filters removed', () => {
    const query = buildOrderQuery({
      ...baseFilters(),
      paymentNo: '',
    })

    expect(query).toMatchObject({
      shopId: 1,
      orderId: 101,
      trigger: 'manual',
      result: 'failed',
      operator: 'ops-user',
      traceId: 'trace-1',
      pageNo: 2,
      pageSize: 20,
    })
    expect(query.fromTime).toContain('2026-05-03T')
    expect(query.toTime).toContain('2026-05-03T')
  })

  it('builds payment query payloads and preserves paymentNo', () => {
    const query = buildPaymentQuery(baseFilters())

    expect(query.paymentNo).toBe('PAY-001')
    expect(query.shopId).toBe(1)
    expect(query.orderId).toBe(101)
  })

  it('derives summary cards from query responses', () => {
    const response: PaymentCompensationQueryResponse = {
      shopId: 1,
      pageNo: 1,
      pageSize: 20,
      total: 3,
      items: [
        {
          payment: {
            paymentId: 1,
            paymentNo: 'PAY-001',
            orderId: 11,
            orderNo: 'ORD-001',
            userId: '10001',
            channel: 'mock',
            status: 'created',
            amountCent: 59900,
            traceId: 'pay-trace-1',
            createdAt: '2026-05-03T12:00:00+08:00',
            updatedAt: '2026-05-03T12:01:00+08:00',
            lastCompensationResult: 'failed',
            lastCompensationErrorCode: 'DOWNSTREAM_TIMEOUT',
            lastCompensationReason: 'order confirm timeout',
            lastCompensationTraceId: 'trace-pay-1',
            lastCompensationTrigger: 'manual',
            lastCompensationOperator: 'ops-user',
            lastCompensatedAt: '2026-05-03T12:05:00+08:00',
          },
          matchedAttemptCount: 2,
          totalAttemptCount: 3,
          latestAttemptAt: '2026-05-03T12:05:00+08:00',
          attempts: [],
        },
      ],
    }

    const cards = deriveSummaryCards('payment', response)

    expect(cards[0].value).toBe('3')
    expect(cards[1].value).toBe('2')
    expect(cards[2].value).toBe('1')
    expect(cards[3].value).toBe('1')
  })

  it('handles empty order responses', () => {
    const response: OrderCompensationQueryResponse = {
      shopId: 1,
      pageNo: 1,
      pageSize: 20,
      total: 0,
      items: [],
    }

    const cards = deriveSummaryCards('order', response)

    expect(cards.every((card) => card.value === '0')).toBe(true)
  })
})

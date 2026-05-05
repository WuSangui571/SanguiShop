import { describe, expect, it } from 'vitest'
import {
  buildDashboardSearchParams,
  buildOrderBulkReplayRequest,
  createDefaultDashboardState,
  deserializeDashboardState,
  readDashboardStateFromSearch,
  serializeDashboardState,
} from './compensationDashboardModel'
import type { OrderCompensationAggregateResponse } from '../../types/api/compensation'

function createOrderAggregate(orderId: number, orderNo: string): OrderCompensationAggregateResponse {
  return {
    order: {
      orderId,
      orderNo,
      userId: '10001',
      reservationNo: null,
      status: 'created',
      totalAmountCent: 1000,
      traceId: `trace-${orderId}`,
      createdAt: '2026-05-05T08:00:00Z',
      updatedAt: '2026-05-05T08:05:00Z',
      lastCompensationResult: 'failed',
      lastCompensationErrorCode: 'DOWNSTREAM_TIMEOUT',
      lastCompensationReason: 'timeout',
      lastCompensationTraceId: `trace-last-${orderId}`,
      lastCompensationTrigger: 'manual',
      lastCompensationOperator: 'ops-a',
      lastCompensatedAt: '2026-05-05T08:05:00Z',
    },
    matchedAttemptCount: 1,
    totalAttemptCount: 1,
    latestAttemptAt: '2026-05-05T08:05:00Z',
    attempts: [],
  }
}

describe('compensationDashboardModel', () => {
  it('creates a default state with a bounded time window', () => {
    const state = createDefaultDashboardState(new Date('2026-05-05T12:30:00Z'))

    expect(state.view).toBe('payment')
    expect(state.filters.pageNo).toBe(1)
    expect(state.filters.pageSize).toBe(20)
    expect(state.filters.fromTime).toBeTruthy()
    expect(state.filters.toTime).toBeTruthy()
    expect(state.replayControls.dryRun).toBe(true)
  })

  it('builds bounded bulk replay payloads from visible rows', () => {
    const state = createDefaultDashboardState(new Date('2026-05-05T12:30:00Z'))
    state.filters.shopId = '9'
    state.replayControls.operator = 'ops-oncall'
    state.replayControls.bulkLimit = 2

    const payload = buildOrderBulkReplayRequest(state.filters, [
      createOrderAggregate(101, 'ORD-101'),
      createOrderAggregate(102, 'ORD-102'),
      createOrderAggregate(103, 'ORD-103'),
    ], state.replayControls)

    expect(payload.shopId).toBe(9)
    expect(payload.operator).toBe('ops-oncall')
    expect(payload.limit).toBe(2)
    expect(payload.orderIds).toEqual([101, 102])
  })

  it('round-trips dashboard state through storage and URL params', () => {
    const state = createDefaultDashboardState(new Date('2026-05-05T12:30:00Z'))
    state.view = 'order'
    state.filters.shopId = '3'
    state.filters.orderId = '88'
    state.replayControls.operator = 'ops-b'
    state.replayControls.bulkLimit = 7
    state.replayControls.dryRun = false

    const serialized = serializeDashboardState(state)
    const fromStorage = deserializeDashboardState(serialized, new Date('2026-05-05T12:30:00Z'))
    const fromSearch = readDashboardStateFromSearch(`?${buildDashboardSearchParams(state).toString()}`, new Date('2026-05-05T12:30:00Z'))

    expect(fromStorage).not.toBeNull()
    expect(fromStorage?.view).toBe('order')
    expect(fromStorage?.filters.orderId).toBe('88')
    expect(fromStorage?.replayControls.operator).toBe('ops-b')

    expect(fromSearch).not.toBeNull()
    expect(fromSearch?.view).toBe('order')
    expect(fromSearch?.filters.shopId).toBe('3')
    expect(fromSearch?.replayControls.bulkLimit).toBe(7)
    expect(fromSearch?.replayControls.dryRun).toBe(false)
  })
})

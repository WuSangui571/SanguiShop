import { describe, expect, it } from 'vitest'
import type { OrderResponse } from '../src/types/api/order'
import {
  createMallOrderFulfillmentView,
  describeMallOrderListSummary,
  getMallOrderStatusLabel,
} from '../src/views/mall/mallOrderStatusModel'

const summaryLabels = {
  created: 'Unpaid',
  paid: 'Paid',
  paidAwaitingShipment: 'Paid, awaiting shipment',
  cancelled: 'Cancelled',
  shipped: 'Shipped, view tracking number',
  unknown: 'Unknown',
}

const fulfillmentLabels = {
  awaitingShipment: 'Awaiting shipment',
  shipped: 'Shipped',
  notReady: 'Unpaid',
  cancelled: 'Cancelled',
  unknown: 'Unknown',
  shippedMessage: 'The order has shipped.',
  awaitingShipmentMessage: 'The merchant is preparing shipment.',
  notReadyMessage: 'Shipment starts after payment.',
  cancelledMessage: 'This order has no logistics information.',
  unknownMessage: 'The fulfillment status is not recognized.',
  unknownStatusPrefix: 'Unknown fulfillment status: ',
  carrierPending: 'Carrier pending',
  trackingNoPending: 'Tracking number pending',
}

describe('mallOrderStatusModel', () => {
  it('summarizes created and cancelled orders without logistics data', () => {
    expect(describeMallOrderListSummary(createOrder({ status: 'created' }), summaryLabels)).toBe('Unpaid')
    expect(describeMallOrderListSummary(createOrder({ status: 'cancelled' }), summaryLabels)).toBe('Cancelled')

    const createdFulfillment = createMallOrderFulfillmentView(createOrder({ status: 'created' }), fulfillmentLabels)
    expect(createdFulfillment.statusLabel).toBe('Unpaid')
    expect(createdFulfillment.message).toBe('Shipment starts after payment.')
    expect(createdFulfillment.showShipmentFields).toBe(false)
  })

  it('describes paid unshipped orders as awaiting shipment', () => {
    const order = createOrder({
      status: 'paid',
      fulfillmentStatus: 'unshipped',
    })

    expect(describeMallOrderListSummary(order, summaryLabels)).toBe('Paid, awaiting shipment')

    const fulfillment = createMallOrderFulfillmentView(order, fulfillmentLabels)
    expect(fulfillment.statusLabel).toBe('Awaiting shipment')
    expect(fulfillment.message).toBe('The merchant is preparing shipment.')
    expect(fulfillment.showShipmentFields).toBe(false)
  })

  it('shows shipped carrier, tracking number, and shipped time when available', () => {
    const order = createOrder({
      status: 'paid',
      fulfillmentStatus: 'shipped',
      carrier: 'SF Express',
      trackingNo: 'SF123456789CN',
      shippedAt: '2026-05-07T11:30:00+08:00',
    })

    expect(describeMallOrderListSummary(order, summaryLabels)).toBe('Shipped, view tracking number')

    const fulfillment = createMallOrderFulfillmentView(order, fulfillmentLabels)
    expect(fulfillment).toEqual({
      statusLabel: 'Shipped',
      message: 'The order has shipped.',
      carrier: 'SF Express',
      trackingNo: 'SF123456789CN',
      shippedAt: '2026-05-07T11:30:00+08:00',
      showShipmentFields: true,
    })
  })

  it('keeps clear placeholders for shipped orders with missing logistics fields', () => {
    const fulfillment = createMallOrderFulfillmentView(createOrder({
      status: 'shipped',
      fulfillmentStatus: 'shipped',
      carrier: null,
      trackingNo: '',
      shippedAt: null,
    }), fulfillmentLabels)

    expect(fulfillment.carrier).toBe('Carrier pending')
    expect(fulfillment.trackingNo).toBe('Tracking number pending')
    expect(fulfillment.shippedAt).toBeNull()
    expect(fulfillment.showShipmentFields).toBe(true)
  })

  it('falls back to raw unknown order and fulfillment statuses', () => {
    const unknownOrder = createOrder({ status: 'reviewing' })
    expect(describeMallOrderListSummary(unknownOrder, summaryLabels)).toBe('reviewing')
    expect(getMallOrderStatusLabel('reviewing', summaryLabels)).toBe('reviewing')

    const unknownFulfillment = createMallOrderFulfillmentView(createOrder({
      status: 'paid',
      fulfillmentStatus: 'packing',
    }), fulfillmentLabels)

    expect(describeMallOrderListSummary(createOrder({
      status: 'paid',
      fulfillmentStatus: 'packing',
    }), summaryLabels)).toBe('Paid, packing')
    expect(unknownFulfillment.statusLabel).toBe('packing')
    expect(unknownFulfillment.message).toBe('Unknown fulfillment status: packing')
    expect(unknownFulfillment.showShipmentFields).toBe(false)
  })
})

function createOrder(patch: Partial<OrderResponse> = {}): OrderResponse {
  return {
    orderId: 501,
    orderNo: 'ORD-501',
    shopId: 1,
    userId: '10001',
    requestId: 'req-501',
    status: 'created',
    totalAmountCent: 59900,
    items: [],
    ...patch,
  }
}

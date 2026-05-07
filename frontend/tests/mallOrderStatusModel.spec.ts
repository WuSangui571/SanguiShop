import { describe, expect, it } from 'vitest'
import type { OrderResponse } from '../src/types/api/order'
import {
  createMallOrderActionView,
  createMallOrderFulfillmentView,
  createMallOrderLifecycleTimeline,
  createMallPaymentRefreshView,
  describeMallOrderListSummary,
  getMallOrderStatusLabel,
  mergeOrderIntoList,
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

const lifecycleLabels = {
  createdTitle: 'Order created',
  createdDescription: 'Waiting for payment.',
  paidAwaitingShipmentTitle: 'Payment complete',
  paidAwaitingShipmentDescription: 'Waiting for merchant shipment.',
  shippedTitle: 'Order shipped',
  shippedDescription: 'Carrier and tracking number are shown here only.',
  cancelledTitle: 'Order cancelled',
  cancelledDescription: 'Payment and shipment actions are unavailable.',
  unknownTitle: 'Unknown status',
  unknownDescriptionPrefix: 'Backend status ',
  refreshSuggestion: 'Refresh order for the latest result.',
}

const actionLabels = {
  pay: 'Pay',
  paid: 'Paid',
  cancel: 'Cancel order',
  actionReady: 'You can pay or cancel this unpaid order.',
  paymentComplete: 'Payment is complete and the order entered fulfillment.',
  shipped: 'The order has shipped.',
  cancelled: 'The order was cancelled.',
  unknownPrefix: 'Unknown order status: ',
  refreshSuggestion: 'Refresh the order before trying again.',
}

const paymentRefreshLabels = {
  available: 'Refresh from payment ',
  fromOrderSnapshot: 'Payment status comes from order snapshot.',
  missingPaymentNo: 'Missing payment number.',
  shipped: 'Shipped orders cannot refresh payment.',
  cancelled: 'Cancelled orders cannot refresh payment.',
  unknownPrefix: 'Unknown order status: ',
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

  it('derives lifecycle timeline nodes for created, paid unshipped, shipped, and cancelled orders', () => {
    expect(createMallOrderLifecycleTimeline(createOrder({ status: 'created' }), lifecycleLabels)).toMatchObject({
      stageLabel: 'Order created',
      currentDescription: 'Waiting for payment.',
      nodes: [
        { key: 'created', title: 'Order created', state: 'current' },
        { key: 'paid', title: 'Payment complete', state: 'pending' },
        { key: 'shipped', title: 'Order shipped', state: 'pending' },
      ],
    })

    expect(createMallOrderLifecycleTimeline(createOrder({
      status: 'paid',
      fulfillmentStatus: 'unshipped',
    }), lifecycleLabels)).toMatchObject({
      stageLabel: 'Payment complete',
      currentDescription: 'Waiting for merchant shipment.',
      nodes: [
        { key: 'created', state: 'complete' },
        { key: 'paid', state: 'current' },
        { key: 'shipped', state: 'pending' },
      ],
    })

    expect(createMallOrderLifecycleTimeline(createOrder({
      status: 'paid',
      fulfillmentStatus: 'shipped',
    }), lifecycleLabels)).toMatchObject({
      stageLabel: 'Order shipped',
      currentDescription: 'Carrier and tracking number are shown here only.',
      nodes: [
        { key: 'created', state: 'complete' },
        { key: 'paid', state: 'complete' },
        { key: 'shipped', state: 'current' },
      ],
    })

    expect(createMallOrderLifecycleTimeline(createOrder({ status: 'cancelled' }), lifecycleLabels)).toMatchObject({
      stageLabel: 'Order cancelled',
      currentDescription: 'Payment and shipment actions are unavailable.',
      nodes: [
        { key: 'cancelled', title: 'Order cancelled', state: 'current' },
      ],
    })
  })

  it('keeps lifecycle timeline usable for unknown backend statuses', () => {
    const timeline = createMallOrderLifecycleTimeline(createOrder({ status: 'reviewing' }), lifecycleLabels)

    expect(timeline.stageLabel).toBe('reviewing')
    expect(timeline.currentDescription).toBe('Backend status reviewing. Refresh order for the latest result.')
    expect(timeline.nodes).toEqual([
      {
        key: 'unknown',
        title: 'reviewing',
        description: 'Backend status reviewing. Refresh order for the latest result.',
        state: 'current',
      },
    ])
  })

  it('derives action button copy and disabled reasons by lifecycle phase', () => {
    expect(createMallOrderActionView(createOrder({ status: 'created' }), actionLabels)).toEqual({
      payLabel: 'Pay',
      canPay: true,
      payDisabledReason: null,
      cancelLabel: 'Cancel order',
      canCancel: true,
      cancelDisabledReason: null,
      actionHint: 'You can pay or cancel this unpaid order.',
    })

    const paid = createMallOrderActionView(createOrder({
      status: 'paid',
      fulfillmentStatus: 'unshipped',
    }), actionLabels)
    expect(paid.canPay).toBe(false)
    expect(paid.canCancel).toBe(false)
    expect(paid.payDisabledReason).toBe('Payment is complete and the order entered fulfillment.')
    expect(paid.cancelDisabledReason).toBe('Payment is complete and the order entered fulfillment.')

    const shipped = createMallOrderActionView(createOrder({
      status: 'paid',
      fulfillmentStatus: 'shipped',
    }), actionLabels)
    expect(shipped.payDisabledReason).toBe('The order has shipped.')
    expect(shipped.cancelDisabledReason).toBe('The order has shipped.')

    const cancelled = createMallOrderActionView(createOrder({ status: 'cancelled' }), actionLabels)
    expect(cancelled.payDisabledReason).toBe('The order was cancelled.')
    expect(cancelled.cancelDisabledReason).toBe('The order was cancelled.')

    const unknown = createMallOrderActionView(createOrder({ status: 'reviewing' }), actionLabels)
    expect(unknown.payDisabledReason).toBe('Unknown order status: reviewing. Refresh the order before trying again.')
    expect(unknown.cancelDisabledReason).toBe('Unknown order status: reviewing. Refresh the order before trying again.')
  })

  it('merges refreshed order detail into the recent order list', () => {
    const original = createOrder({
      orderId: 501,
      status: 'paid',
      fulfillmentStatus: 'unshipped',
      updatedAt: '2026-05-07T10:00:00+08:00',
    })
    const refreshed = createOrder({
      orderId: 501,
      status: 'paid',
      fulfillmentStatus: 'shipped',
      carrier: 'SF Express',
      trackingNo: 'SF999',
      updatedAt: '2026-05-07T11:00:00+08:00',
    })
    const otherOrder = createOrder({ orderId: 502, orderNo: 'ORD-502' })

    expect(mergeOrderIntoList([original, otherOrder], refreshed)).toEqual([refreshed, otherOrder])
    expect(mergeOrderIntoList([otherOrder], refreshed)).toEqual([otherOrder])
  })

  it('explains payment refresh source for payment numbers and paid order snapshots', () => {
    expect(createMallPaymentRefreshView(
      createOrder({ status: 'paid', fulfillmentStatus: 'unshipped' }),
      'PAY-501',
      paymentRefreshLabels,
    )).toEqual({
      canRefresh: true,
      disabledReason: null,
      sourceDescription: 'Refresh from payment PAY-501',
    })

    expect(createMallPaymentRefreshView(
      createOrder({ status: 'paid', fulfillmentStatus: 'unshipped' }),
      '',
      paymentRefreshLabels,
    )).toEqual({
      canRefresh: false,
      disabledReason: 'Payment status comes from order snapshot.',
      sourceDescription: 'Payment status comes from order snapshot.',
    })
  })

  it('returns clear payment refresh disabled reasons for missing, cancelled, shipped, and unknown states', () => {
    expect(createMallPaymentRefreshView(createOrder({ status: 'created' }), '', paymentRefreshLabels).disabledReason)
      .toBe('Missing payment number.')
    expect(createMallPaymentRefreshView(createOrder({ status: 'cancelled' }), 'PAY-501', paymentRefreshLabels).disabledReason)
      .toBe('Cancelled orders cannot refresh payment.')
    expect(createMallPaymentRefreshView(createOrder({
      status: 'paid',
      fulfillmentStatus: 'shipped',
    }), 'PAY-501', paymentRefreshLabels).disabledReason).toBe('Shipped orders cannot refresh payment.')
    expect(createMallPaymentRefreshView(createOrder({ status: 'reviewing' }), '', paymentRefreshLabels).disabledReason)
      .toBe('Unknown order status: reviewing. Missing payment number.')
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

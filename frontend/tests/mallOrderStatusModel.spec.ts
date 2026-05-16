import { describe, expect, it } from 'vitest'
import type { OrderResponse } from '../src/types/api/order'
import type { PaymentResponse } from '../src/types/api/payment'
import {
  applyMallPaymentToOrder,
  applyMallPaymentToOrderList,
  applyMallReviewToOrder,
  applyMallReviewToOrderList,
  createMallOrderEmptyStateView,
  createMallOrderLinkedDetailView,
  createMallOrderDeepLinkRecoveryView,
  createMallOrderActionView,
  createMallOrderFulfillmentView,
  createMallOrderLifecycleTimeline,
  createMallOrderListFilterOptions,
  createMallOrderPaginationView,
  createMallOrderReviewView,
  createMallOrderSearchContinuation,
  createMallPaymentRefreshView,
  describeMallOrderReviewState,
  describeMallOrderListSummary,
  filterMallOrders,
  findLoadedMallOrder,
  getMallOrderStatusLabel,
  mergeOrderIntoList,
  resolveMallOrderListFilter,
  upsertOrderIntoList,
} from '../src/views/mall/mallOrderStatusModel'

const summaryLabels = {
  created: 'Unpaid',
  paid: 'Paid',
  paidAwaitingShipment: 'Paid, awaiting shipment',
  cancelled: 'Cancelled',
  shipped: 'Shipped, view tracking number',
  completed: 'Completed',
  unknown: 'Unknown',
}

const fulfillmentLabels = {
  awaitingShipment: 'Awaiting shipment',
  shipped: 'Shipped',
  completed: 'Completed',
  notReady: 'Unpaid',
  cancelled: 'Cancelled',
  unknown: 'Unknown',
  shippedMessage: 'The order has shipped.',
  completedMessage: 'Receipt has been confirmed.',
  awaitingShipmentMessage: 'The merchant is preparing shipment.',
  notReadyMessage: 'Shipment starts after payment.',
  cancelledMessage: 'This order has no logistics information.',
  unknownMessage: 'The fulfillment status is not recognized.',
  unknownStatusPrefix: 'Unknown fulfillment status: ',
  carrierPending: 'Carrier pending',
  trackingNoPending: 'Tracking number pending',
  orderSnapshotSource: 'Logistics status comes from the order snapshot.',
}

const lifecycleLabels = {
  createdTitle: 'Order created',
  createdDescription: 'Waiting for payment.',
  paidAwaitingShipmentTitle: 'Payment complete',
  paidAwaitingShipmentDescription: 'Waiting for merchant shipment.',
  shippedTitle: 'Order shipped',
  shippedDescription: 'Carrier and tracking number are shown here only.',
  completedTitle: 'Order completed',
  completedDescription: 'Receipt has been confirmed.',
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
  completed: 'The order is completed.',
  cancelled: 'The order was cancelled.',
  unknownPrefix: 'Unknown order status: ',
  refreshSuggestion: 'Refresh the order before trying again.',
  confirmReceipt: 'Confirm receipt',
  confirmingReceipt: 'Confirming',
  receiptReady: 'Confirm receipt after delivery.',
}

const reviewLabels = {
  pending: 'Pending completion',
  ready: 'Ready to review',
  reviewed: 'Reviewed',
  notCompleted: 'Review is available after completion.',
  unknownPrefix: 'Unknown order status: ',
  refreshSuggestion: 'Refresh the order before trying again.',
  submitReview: 'Submit review',
  submittingReview: 'Submitting...',
}

const paymentRefreshLabels = {
  available: 'Refresh from payment ',
  fromOrderSnapshot: 'Payment status comes from order snapshot.',
  missingPaymentNo: 'Missing payment number.',
  shipped: 'Shipped orders cannot refresh payment.',
  completed: 'Completed orders cannot refresh payment.',
  cancelled: 'Cancelled orders cannot refresh payment.',
  unknownPrefix: 'Unknown order status: ',
}

const listFilterLabels = {
  all: 'All',
  created: 'Unpaid',
  paidAwaitingShipment: 'Awaiting shipment',
  shipped: 'Shipped',
  completed: 'Completed',
  cancelled: 'Cancelled',
  unknown: 'Unrecognized',
}

const deepLinkRecoveryLabels = {
  noOrderId: 'No order link',
  invalidOrderId: 'Invalid order link',
  restoreFailedPrefix: 'Unable to restore order ',
  suggestion: 'Recent purchases are still available.',
}

const paginationLabels = {
  summary: 'Page {page} of {totalPages}, {total} total, {size} per page',
}

const emptyStateLabels = {
  noOrders: 'No orders yet.',
  filteredCurrentPage: 'No current-page orders match this filter.',
  filteredStatusChanged: 'The current order status changed and moved out of this filter.',
  searchNoCurrentPage: '{query} was not found on this page.',
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
      sourceDescription: 'Logistics status comes from the order snapshot.',
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
    expect(fulfillment.sourceDescription).toBe('Logistics status comes from the order snapshot.')
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
    expect(unknownFulfillment.sourceDescription).toBe('Logistics status comes from the order snapshot.')
  })

  it('derives lifecycle timeline nodes for created, paid unshipped, shipped, and cancelled orders', () => {
    expect(createMallOrderLifecycleTimeline(createOrder({ status: 'created' }), lifecycleLabels)).toMatchObject({
      stageLabel: 'Order created',
      currentDescription: 'Waiting for payment.',
      nodes: [
        { key: 'created', title: 'Order created', state: 'current' },
        { key: 'paid', title: 'Payment complete', state: 'pending' },
        { key: 'shipped', title: 'Order shipped', state: 'pending' },
        { key: 'completed', title: 'Order completed', state: 'pending' },
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
        { key: 'completed', state: 'pending' },
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
        { key: 'completed', state: 'pending' },
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
      receiptLabel: 'Confirm receipt',
      canConfirmReceipt: false,
      receiptDisabledReason: 'You can pay or cancel this unpaid order.',
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
    expect(shipped.canConfirmReceipt).toBe(true)
    expect(shipped.actionHint).toBe('Confirm receipt after delivery.')

    const completed = createMallOrderActionView(createOrder({
      status: 'completed',
      fulfillmentStatus: 'completed',
    }), actionLabels)
    expect(completed.canConfirmReceipt).toBe(false)
    expect(completed.receiptDisabledReason).toBe('The order is completed.')

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

  it('can upsert linked order detail ahead of the current order page', () => {
    const currentPageOrder = createOrder({ orderId: 502, orderNo: 'ORD-502' })
    const linkedDetail = createOrder({ orderId: 501, orderNo: 'ORD-501' })
    const refreshedLinkedDetail = createOrder({
      orderId: 501,
      orderNo: 'ORD-501',
      status: 'paid',
      fulfillmentStatus: 'unshipped',
    })

    expect(upsertOrderIntoList([currentPageOrder], linkedDetail)).toEqual([linkedDetail, currentPageOrder])
    expect(upsertOrderIntoList([linkedDetail, currentPageOrder], refreshedLinkedDetail))
      .toEqual([refreshedLinkedDetail, currentPageOrder])
  })

  it('classifies loaded orders into order center filters', () => {
    const created = createOrder({ orderId: 501, status: 'created' })
    const paidAwaitingShipment = createOrder({
      orderId: 502,
      status: 'paid',
      fulfillmentStatus: 'unshipped',
    })
    const shipped = createOrder({
      orderId: 503,
      status: 'paid',
      fulfillmentStatus: 'shipped',
    })
    const completed = createOrder({
      orderId: 504,
      status: 'completed',
      fulfillmentStatus: 'completed',
    })
    const cancelled = createOrder({ orderId: 505, status: 'cancelled' })
    const unknown = createOrder({ orderId: 506, status: 'reviewing' })
    const orders = [created, paidAwaitingShipment, shipped, completed, cancelled, unknown]

    expect(resolveMallOrderListFilter(created)).toBe('created')
    expect(resolveMallOrderListFilter(paidAwaitingShipment)).toBe('paidAwaitingShipment')
    expect(resolveMallOrderListFilter(shipped)).toBe('shipped')
    expect(resolveMallOrderListFilter(completed)).toBe('completed')
    expect(resolveMallOrderListFilter(cancelled)).toBe('cancelled')
    expect(resolveMallOrderListFilter(unknown)).toBe('unknown')
    expect(filterMallOrders(orders, 'all')).toEqual(orders)
    expect(filterMallOrders(orders, 'unknown')).toEqual([unknown])

    expect(createMallOrderListFilterOptions(orders, listFilterLabels)).toEqual([
      { key: 'all', label: 'All', count: 6 },
      { key: 'created', label: 'Unpaid', count: 1 },
      { key: 'paidAwaitingShipment', label: 'Awaiting shipment', count: 1 },
      { key: 'shipped', label: 'Shipped', count: 1 },
      { key: 'completed', label: 'Completed', count: 1 },
      { key: 'cancelled', label: 'Cancelled', count: 1 },
      { key: 'unknown', label: 'Unrecognized', count: 1 },
    ])
  })

  it('moves refreshed orders between filters after status changes', () => {
    const original = createOrder({
      orderId: 501,
      status: 'created',
      updatedAt: '2026-05-07T10:00:00+08:00',
    })
    const paid = createOrder({
      orderId: 501,
      status: 'paid',
      fulfillmentStatus: 'unshipped',
      updatedAt: '2026-05-07T11:00:00+08:00',
    })

    const refreshedOrders = mergeOrderIntoList([original], paid)

    expect(filterMallOrders(refreshedOrders, 'created')).toEqual([])
    expect(filterMallOrders(refreshedOrders, 'paidAwaitingShipment')).toEqual([paid])
  })

  it('moves refreshed paid orders from awaiting shipment to shipped filters', () => {
    const awaitingShipment = createOrder({
      orderId: 501,
      status: 'paid',
      fulfillmentStatus: 'unshipped',
      updatedAt: '2026-05-07T10:00:00+08:00',
    })
    const shipped = createOrder({
      orderId: 501,
      status: 'paid',
      fulfillmentStatus: 'shipped',
      carrier: 'SF Express',
      trackingNo: 'SF999',
      updatedAt: '2026-05-07T11:00:00+08:00',
    })

    const refreshedOrders = mergeOrderIntoList([awaitingShipment], shipped)

    expect(filterMallOrders(refreshedOrders, 'paidAwaitingShipment')).toEqual([])
    expect(filterMallOrders(refreshedOrders, 'shipped')).toEqual([shipped])
    expect(createMallOrderEmptyStateView(
      refreshedOrders,
      filterMallOrders(refreshedOrders, 'paidAwaitingShipment'),
      { order: null, query: '', matchReason: null },
      emptyStateLabels,
      {
        currentOrder: shipped,
        filter: 'paidAwaitingShipment',
      },
    )).toEqual({
      kind: 'filteredCurrentPage',
      message: 'The current order status changed and moved out of this filter.',
    })
  })

  it('moves confirmed shipped orders to completed filters', () => {
    const shipped = createOrder({
      orderId: 501,
      status: 'shipped',
      fulfillmentStatus: 'shipped',
      carrier: 'SF Express',
      trackingNo: 'SF999',
    })
    const completed = createOrder({
      orderId: 501,
      status: 'completed',
      fulfillmentStatus: 'completed',
      carrier: 'SF Express',
      trackingNo: 'SF999',
      completedAt: '2026-05-07T13:00:00+08:00',
    })

    const refreshedOrders = mergeOrderIntoList([shipped], completed)

    expect(describeMallOrderListSummary(completed, summaryLabels)).toBe('Completed')
    expect(filterMallOrders(refreshedOrders, 'shipped')).toEqual([])
    expect(filterMallOrders(refreshedOrders, 'completed')).toEqual([completed])
    expect(createMallOrderEmptyStateView(
      refreshedOrders,
      filterMallOrders(refreshedOrders, 'shipped'),
      { order: null, query: '', matchReason: null },
      emptyStateLabels,
      {
        currentOrder: completed,
        filter: 'shipped',
      },
    ).message).toBe('The current order status changed and moved out of this filter.')
  })

  it('derives review state only for completed unreviewed orders', () => {
    expect(createMallOrderReviewView(createOrder({ status: 'created' }), reviewLabels)).toMatchObject({
      statusLabel: 'Pending completion',
      canSubmitReview: false,
      message: 'Review is available after completion.',
    })

    const completed = createMallOrderReviewView(createOrder({
      status: 'completed',
      fulfillmentStatus: 'completed',
    }), reviewLabels)
    expect(completed).toEqual({
      statusLabel: 'Ready to review',
      message: 'Ready to review',
      canSubmitReview: true,
      submitLabel: 'Submit review',
    })

    const pending = createMallOrderReviewView(createOrder({
      status: 'completed',
      fulfillmentStatus: 'completed',
    }), reviewLabels, { isSubmittingReview: true })
    expect(pending.canSubmitReview).toBe(false)
    expect(pending.submitLabel).toBe('Submitting...')

    const reviewed = createMallOrderReviewView(createOrder({
      status: 'completed',
      fulfillmentStatus: 'completed',
      reviewed: true,
    }), reviewLabels)
    expect(reviewed.statusLabel).toBe('Reviewed')
    expect(reviewed.canSubmitReview).toBe(false)
    expect(describeMallOrderReviewState(createOrder({
      status: 'completed',
      fulfillmentStatus: 'completed',
      reviewed: true,
    }), reviewLabels)).toBe('Reviewed')

    const unknown = createMallOrderReviewView(createOrder({ status: 'reviewing' }), reviewLabels)
    expect(unknown.message).toBe('Unknown order status: reviewing. Refresh the order before trying again.')
  })

  it('merges submitted reviews into detail and list while staying completed', () => {
    const completed = createOrder({
      orderId: 501,
      status: 'completed',
      fulfillmentStatus: 'completed',
    })
    const review = createReview()
    const updated = applyMallReviewToOrder(completed, review)

    expect(updated).toMatchObject({
      orderId: 501,
      status: 'completed',
      fulfillmentStatus: 'completed',
      reviewed: true,
      review,
    })
    expect(resolveMallOrderListFilter(updated ?? completed)).toBe('completed')
    expect(applyMallReviewToOrderList([completed], review)[0]).toMatchObject({
      reviewed: true,
      review,
    })
    expect(applyMallReviewToOrder(createOrder({ orderId: 999 }), review)?.review).toBeUndefined()
  })

  it('applies paid payment responses to detail and list as awaiting shipment', () => {
    const unpaid = createOrder({
      orderId: 501,
      status: 'created',
      fulfillmentStatus: null,
    })
    const paidPayment = createPayment({ paymentNo: 'PAY-501' })
    const otherOrder = createOrder({ orderId: 502, orderNo: 'ORD-502' })

    expect(applyMallPaymentToOrder(unpaid, paidPayment)).toMatchObject({
      orderId: 501,
      status: 'paid',
      fulfillmentStatus: 'unshipped',
    })
    expect(applyMallPaymentToOrderList([unpaid, otherOrder], paidPayment)).toEqual([
      {
        ...unpaid,
        status: 'paid',
        fulfillmentStatus: 'unshipped',
      },
      otherOrder,
    ])
    expect(filterMallOrders(applyMallPaymentToOrderList([unpaid], paidPayment), 'paidAwaitingShipment'))
      .toHaveLength(1)
    expect(applyMallPaymentToOrder(unpaid, createPayment({ orderId: 999 }))).toEqual(unpaid)
    expect(applyMallPaymentToOrder(unpaid, createPayment({ status: 'failed' }))).toEqual(unpaid)
  })

  it('preserves shipped order main status and logistics fields after payment refresh returns paid', () => {
    const shipped = createOrder({
      orderId: 501,
      status: 'shipped',
      fulfillmentStatus: 'shipped',
      carrier: 'SF Express',
      trackingNo: 'SF123456789CN',
      shippedAt: '2026-05-07T11:30:00+08:00',
    })
    const paidPayment = createPayment({ paymentNo: 'PAY-501' })
    const otherOrder = createOrder({ orderId: 502, orderNo: 'ORD-502' })

    expect(applyMallPaymentToOrder(shipped, paidPayment)).toEqual(shipped)
    expect(applyMallPaymentToOrderList([shipped, otherOrder], paidPayment)).toEqual([shipped, otherOrder])
    expect(filterMallOrders(applyMallPaymentToOrderList([shipped], paidPayment), 'shipped')).toHaveLength(1)
    expect(filterMallOrders(applyMallPaymentToOrderList([shipped], paidPayment), 'paidAwaitingShipment')).toEqual([])
  })

  it('preserves completed order main status and review snapshot after payment refresh returns paid', () => {
    const completed = createOrder({
      orderId: 501,
      status: 'completed',
      fulfillmentStatus: 'completed',
      carrier: 'SF Express',
      trackingNo: 'SF123456789CN',
      completedAt: '2026-05-07T13:00:00+08:00',
      reviewed: true,
    })
    const paidPayment = createPayment({ paymentNo: 'PAY-501' })
    const otherOrder = createOrder({ orderId: 502, orderNo: 'ORD-502' })

    const detail = applyMallPaymentToOrder(completed, paidPayment)
    expect(detail).toMatchObject({
      orderId: 501,
      status: 'completed',
      fulfillmentStatus: 'completed',
      completedAt: '2026-05-07T13:00:00+08:00',
      reviewed: true,
    })
    const listResult = applyMallPaymentToOrderList([completed, otherOrder], paidPayment)
    expect(listResult[0]).toMatchObject({
      orderId: 501,
      status: 'completed',
      fulfillmentStatus: 'completed',
    })
    expect(filterMallOrders(listResult, 'completed')).toHaveLength(1)
    expect(filterMallOrders(listResult, 'paidAwaitingShipment')).toEqual([])
  })

  it('preserves cancelled order main status after payment refresh returns paid', () => {
    const cancelled = createOrder({ orderId: 501, status: 'cancelled' })
    const paidPayment = createPayment({ paymentNo: 'PAY-501' })
    const otherOrder = createOrder({ orderId: 502, orderNo: 'ORD-502' })

    expect(applyMallPaymentToOrder(cancelled, paidPayment)).toEqual(cancelled)
    expect(applyMallPaymentToOrderList([cancelled, otherOrder], paidPayment)).toEqual([cancelled, otherOrder])
    expect(filterMallOrders(applyMallPaymentToOrderList([cancelled], paidPayment), 'cancelled')).toHaveLength(1)
    expect(filterMallOrders(applyMallPaymentToOrderList([cancelled], paidPayment), 'paidAwaitingShipment')).toEqual([])
  })

  it('preserves unknown order status after payment refresh returns paid', () => {
    const unknown = createOrder({ orderId: 501, status: 'refunding' })
    const paidPayment = createPayment({ paymentNo: 'PAY-501' })
    const otherOrder = createOrder({ orderId: 502, orderNo: 'ORD-502' })

    expect(applyMallPaymentToOrder(unknown, paidPayment)).toEqual(unknown)
    const listResult = applyMallPaymentToOrderList([unknown, otherOrder], paidPayment)
    expect(listResult).toEqual([unknown, otherOrder])
    expect(filterMallOrders(listResult, 'unknown')).toHaveLength(1)
    expect(filterMallOrders(listResult, 'paidAwaitingShipment')).toEqual([])
  })

  it('finds loaded orders by order number or exact order id only on the current page', () => {
    const orders = [
      createOrder({ orderId: 501, orderNo: 'ORD-20260507-501' }),
      createOrder({ orderId: 502, orderNo: 'ORD-20260507-502' }),
    ]

    expect(findLoadedMallOrder(orders, '0507-501')).toEqual({
      order: orders[0],
      query: '0507-501',
      matchReason: 'orderNo',
    })
    expect(findLoadedMallOrder(orders, '502')).toEqual({
      order: orders[1],
      query: '502',
      matchReason: 'orderId',
    })
    expect(findLoadedMallOrder(orders, '503')).toEqual({
      order: null,
      query: '503',
      matchReason: null,
    })
    expect(findLoadedMallOrder(orders, '   ')).toEqual({
      order: null,
      query: '',
      matchReason: null,
    })
  })

  it('builds pagination summary and current-page search continuation hints', () => {
    const pagination = createMallOrderPaginationView({
      page: 2,
      size: 5,
      total: 12,
    }, paginationLabels)

    expect(pagination).toEqual({
      page: 2,
      totalPages: 3,
      size: 5,
      total: 12,
      summary: 'Page 2 of 3, 12 total, 5 per page',
      canGoPrev: true,
      canGoNext: true,
    })

    const miss = findLoadedMallOrder([
      createOrder({ orderId: 501, orderNo: 'ORD-501' }),
    ], 'ORD-999')
    expect(createMallOrderSearchContinuation(miss, pagination)).toEqual({
      canSearchPreviousPage: true,
      canSearchNextPage: true,
    })

    const hit = findLoadedMallOrder([
      createOrder({ orderId: 501, orderNo: 'ORD-501' }),
    ], '501')
    expect(createMallOrderSearchContinuation(hit, pagination)).toEqual({
      canSearchPreviousPage: false,
      canSearchNextPage: false,
    })
  })

  it('keeps filter state meaningful while paging current order history', () => {
    const paidPage = [
      createOrder({ orderId: 601, status: 'paid', fulfillmentStatus: 'unshipped' }),
    ]
    const shippedPage = [
      createOrder({ orderId: 701, status: 'paid', fulfillmentStatus: 'shipped' }),
    ]

    expect(filterMallOrders(paidPage, 'paidAwaitingShipment')).toEqual(paidPage)
    expect(filterMallOrders(shippedPage, 'paidAwaitingShipment')).toEqual([])
    expect(createMallOrderEmptyStateView(
      shippedPage,
      filterMallOrders(shippedPage, 'paidAwaitingShipment'),
      { order: null, query: '', matchReason: null },
      emptyStateLabels,
    )).toEqual({
      kind: 'filteredCurrentPage',
      message: 'No current-page orders match this filter.',
    })
  })

  it('distinguishes no orders, filter empty, and current-page search misses', () => {
    const orders = [createOrder({ orderId: 501, orderNo: 'ORD-501' })]
    const searchMiss = findLoadedMallOrder(orders, 'ORD-999')

    expect(createMallOrderEmptyStateView([], [], searchMiss, emptyStateLabels)).toEqual({
      kind: 'noOrders',
      message: 'No orders yet.',
    })
    expect(createMallOrderEmptyStateView(orders, [], {
      order: null,
      query: '',
      matchReason: null,
    }, emptyStateLabels)).toEqual({
      kind: 'filteredCurrentPage',
      message: 'No current-page orders match this filter.',
    })
    expect(createMallOrderEmptyStateView(orders, orders, searchMiss, emptyStateLabels)).toEqual({
      kind: 'searchNoCurrentPage',
      message: 'ORD-999 was not found on this page.',
    })
  })

  it('labels deep-link detail that is not part of the current backend page', () => {
    const linkedOrder = createOrder({ orderId: 501 })
    const currentPage = [createOrder({ orderId: 502 })]

    expect(createMallOrderLinkedDetailView(linkedOrder, currentPage, 'From order link')).toEqual({
      isLinkedOnly: true,
      label: 'From order link',
    })
    expect(createMallOrderLinkedDetailView(linkedOrder, [linkedOrder], 'From order link')).toEqual({
      isLinkedOnly: false,
      label: 'From order link',
    })
    expect(createMallOrderLinkedDetailView(null, currentPage, 'From order link')).toEqual({
      isLinkedOnly: false,
      label: '',
    })
  })

  it('explains restored shipped deep-link logistics as an order snapshot', () => {
    const linkedOrder = createOrder({
      orderId: 501,
      status: 'paid',
      fulfillmentStatus: 'shipped',
      carrier: 'SF Express',
      trackingNo: 'SF999',
    })

    expect(createMallOrderLinkedDetailView(linkedOrder, [], 'From order link')).toEqual({
      isLinkedOnly: true,
      label: 'From order link',
    })
    expect(createMallOrderFulfillmentView(linkedOrder, fulfillmentLabels)).toMatchObject({
      statusLabel: 'Shipped',
      carrier: 'SF Express',
      trackingNo: 'SF999',
      showShipmentFields: true,
      sourceDescription: 'Logistics status comes from the order snapshot.',
    })
  })

  it('explains restored completed deep-link orders as order snapshots', () => {
    const linkedOrder = createOrder({
      orderId: 501,
      status: 'completed',
      fulfillmentStatus: 'completed',
      carrier: 'SF Express',
      trackingNo: 'SF999',
      completedAt: '2026-05-07T13:00:00+08:00',
    })

    expect(createMallOrderFulfillmentView(linkedOrder, fulfillmentLabels)).toMatchObject({
      statusLabel: 'Completed',
      message: 'Receipt has been confirmed.',
      carrier: 'SF Express',
      trackingNo: 'SF999',
      showShipmentFields: true,
      sourceDescription: 'Logistics status comes from the order snapshot.',
    })
    expect(createMallOrderActionView(linkedOrder, actionLabels).canConfirmReceipt).toBe(false)
  })

  it('describes deep-link failures without treating recent purchases as empty', () => {
    expect(createMallOrderDeepLinkRecoveryView('abc', '', deepLinkRecoveryLabels)).toEqual({
      isLinkIssue: true,
      title: 'Invalid order link',
      message: 'Invalid order link: abc. Recent purchases are still available.',
      canClearLink: true,
    })

    expect(createMallOrderDeepLinkRecoveryView('501', 'AUTH_FORBIDDEN: denied', deepLinkRecoveryLabels)).toEqual({
      isLinkIssue: true,
      title: 'Unable to restore order ',
      message: 'AUTH_FORBIDDEN: denied Recent purchases are still available.',
      canClearLink: true,
    })

    expect(createMallOrderDeepLinkRecoveryView(null, '', deepLinkRecoveryLabels)).toEqual({
      isLinkIssue: false,
      title: 'No order link',
      message: 'Recent purchases are still available.',
      canClearLink: false,
    })
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
    expect(createMallPaymentRefreshView(createOrder({
      status: 'completed',
      fulfillmentStatus: 'completed',
    }), 'PAY-501', paymentRefreshLabels).disabledReason).toBe('Completed orders cannot refresh payment.')
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

function createPayment(patch: Partial<PaymentResponse> = {}): PaymentResponse {
  return {
    paymentId: 701,
    paymentNo: 'PAY-501',
    orderId: 501,
    orderNo: 'ORD-501',
    shopId: 1,
    userId: '10001',
    channel: 'mock',
    status: 'paid',
    amountCent: 59900,
    ...patch,
  }
}

function createReview(patch: Partial<NonNullable<OrderResponse['review']>> = {}): NonNullable<OrderResponse['review']> {
  return {
    orderReviewId: 9001,
    shopId: 1,
    orderId: 501,
    orderNo: 'ORD-501',
    userId: '10001',
    rating: 5,
    content: 'good',
    imageUrls: [],
    requestId: 'review-001',
    traceId: 'trace-review',
    createdAt: '2026-05-08T10:00:00+08:00',
    ...patch,
  }
}

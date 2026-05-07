import type { OrderResponse } from '../../types/api/order'

export interface MallOrderSummaryLabels {
  created: string
  paid: string
  paidAwaitingShipment: string
  cancelled: string
  shipped: string
  unknown: string
}

export interface MallOrderFulfillmentLabels {
  awaitingShipment: string
  shipped: string
  notReady: string
  cancelled: string
  unknown: string
  shippedMessage: string
  awaitingShipmentMessage: string
  notReadyMessage: string
  cancelledMessage: string
  unknownMessage: string
  unknownStatusPrefix: string
  carrierPending: string
  trackingNoPending: string
}

export interface MallOrderFulfillmentView {
  statusLabel: string
  message: string
  carrier: string
  trackingNo: string
  shippedAt: string | null
  showShipmentFields: boolean
}

export function describeMallOrderListSummary(order: OrderResponse, labels: MallOrderSummaryLabels): string {
  const orderStatus = normalizeText(order.status)
  const fulfillmentStatus = normalizeText(order.fulfillmentStatus)

  if (orderStatus === 'cancelled') {
    return labels.cancelled
  }
  if (orderStatus === 'created') {
    return labels.created
  }
  if (isShipped(order)) {
    return labels.shipped
  }
  if (orderStatus === 'paid') {
    if (!fulfillmentStatus || fulfillmentStatus === 'unshipped') {
      return labels.paidAwaitingShipment
    }
    return `${labels.paid}, ${fulfillmentStatus}`
  }

  return orderStatus ?? labels.unknown
}

export function createMallOrderFulfillmentView(
  order: OrderResponse | null,
  labels: MallOrderFulfillmentLabels,
): MallOrderFulfillmentView {
  if (!order) {
    return createPlaceholderFulfillment(labels.unknown, labels.unknownMessage)
  }

  const orderStatus = normalizeText(order.status)
  const fulfillmentStatus = normalizeText(order.fulfillmentStatus)

  if (isShipped(order)) {
    return {
      statusLabel: labels.shipped,
      message: labels.shippedMessage,
      carrier: normalizeText(order.carrier) ?? labels.carrierPending,
      trackingNo: normalizeText(order.trackingNo) ?? labels.trackingNoPending,
      shippedAt: normalizeText(order.shippedAt),
      showShipmentFields: true,
    }
  }
  if (orderStatus === 'cancelled') {
    return createPlaceholderFulfillment(labels.cancelled, labels.cancelledMessage)
  }
  if (orderStatus === 'created') {
    return createPlaceholderFulfillment(labels.notReady, labels.notReadyMessage)
  }
  if (orderStatus === 'paid' && (!fulfillmentStatus || fulfillmentStatus === 'unshipped')) {
    return createPlaceholderFulfillment(labels.awaitingShipment, labels.awaitingShipmentMessage)
  }
  if (fulfillmentStatus && fulfillmentStatus !== 'all') {
    return createPlaceholderFulfillment(
      fulfillmentStatus,
      `${labels.unknownStatusPrefix}${fulfillmentStatus}`,
    )
  }

  return createPlaceholderFulfillment(orderStatus ?? labels.unknown, labels.unknownMessage)
}

export function getMallOrderStatusLabel(status: string, labels: MallOrderSummaryLabels): string {
  const normalizedStatus = normalizeText(status)
  if (normalizedStatus === 'created') {
    return labels.created
  }
  if (normalizedStatus === 'paid') {
    return labels.paid
  }
  if (normalizedStatus === 'cancelled') {
    return labels.cancelled
  }
  if (normalizedStatus === 'shipped') {
    return labels.shipped
  }
  return normalizedStatus ?? labels.unknown
}

function isShipped(order: OrderResponse): boolean {
  return normalizeText(order.status) === 'shipped' || normalizeText(order.fulfillmentStatus) === 'shipped'
}

function createPlaceholderFulfillment(statusLabel: string, message: string): MallOrderFulfillmentView {
  return {
    statusLabel,
    message,
    carrier: '',
    trackingNo: '',
    shippedAt: null,
    showShipmentFields: false,
  }
}

function normalizeText(value: string | null | undefined): string | null {
  const trimmed = value?.trim()
  return trimmed ? trimmed : null
}

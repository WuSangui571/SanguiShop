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

export interface MallOrderLifecycleLabels {
  createdTitle: string
  createdDescription: string
  paidAwaitingShipmentTitle: string
  paidAwaitingShipmentDescription: string
  shippedTitle: string
  shippedDescription: string
  cancelledTitle: string
  cancelledDescription: string
  unknownTitle: string
  unknownDescriptionPrefix: string
  refreshSuggestion: string
}

export type MallOrderLifecycleNodeKey = 'created' | 'paid' | 'shipped' | 'cancelled' | 'unknown'
export type MallOrderLifecycleNodeState = 'complete' | 'current' | 'pending'

export interface MallOrderLifecycleNode {
  key: MallOrderLifecycleNodeKey
  title: string
  description: string
  state: MallOrderLifecycleNodeState
}

export interface MallOrderLifecycleTimeline {
  stageLabel: string
  currentDescription: string
  nodes: MallOrderLifecycleNode[]
}

export interface MallOrderActionLabels {
  pay: string
  paid: string
  cancel: string
  actionReady: string
  paymentComplete: string
  shipped: string
  cancelled: string
  unknownPrefix: string
  refreshSuggestion: string
}

export interface MallOrderActionOptions {
  hasPayment?: boolean
  isSubmittingPayment?: boolean
  isCancelling?: boolean
}

export interface MallOrderActionView {
  payLabel: string
  canPay: boolean
  payDisabledReason: string | null
  cancelLabel: string
  canCancel: boolean
  cancelDisabledReason: string | null
  actionHint: string
}

type MallOrderLifecyclePhase = 'created' | 'paidAwaitingShipment' | 'shipped' | 'cancelled' | 'unknown'

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

export function createMallOrderLifecycleTimeline(
  order: OrderResponse | null,
  labels: MallOrderLifecycleLabels,
): MallOrderLifecycleTimeline {
  const phase = resolveLifecyclePhase(order)

  if (phase === 'created') {
    return {
      stageLabel: labels.createdTitle,
      currentDescription: labels.createdDescription,
      nodes: [
        createLifecycleNode('created', labels.createdTitle, labels.createdDescription, 'current'),
        createLifecycleNode('paid', labels.paidAwaitingShipmentTitle, labels.paidAwaitingShipmentDescription, 'pending'),
        createLifecycleNode('shipped', labels.shippedTitle, labels.shippedDescription, 'pending'),
      ],
    }
  }

  if (phase === 'paidAwaitingShipment') {
    return {
      stageLabel: labels.paidAwaitingShipmentTitle,
      currentDescription: labels.paidAwaitingShipmentDescription,
      nodes: [
        createLifecycleNode('created', labels.createdTitle, labels.createdDescription, 'complete'),
        createLifecycleNode('paid', labels.paidAwaitingShipmentTitle, labels.paidAwaitingShipmentDescription, 'current'),
        createLifecycleNode('shipped', labels.shippedTitle, labels.shippedDescription, 'pending'),
      ],
    }
  }

  if (phase === 'shipped') {
    return {
      stageLabel: labels.shippedTitle,
      currentDescription: labels.shippedDescription,
      nodes: [
        createLifecycleNode('created', labels.createdTitle, labels.createdDescription, 'complete'),
        createLifecycleNode('paid', labels.paidAwaitingShipmentTitle, labels.paidAwaitingShipmentDescription, 'complete'),
        createLifecycleNode('shipped', labels.shippedTitle, labels.shippedDescription, 'current'),
      ],
    }
  }

  if (phase === 'cancelled') {
    return {
      stageLabel: labels.cancelledTitle,
      currentDescription: labels.cancelledDescription,
      nodes: [
        createLifecycleNode('cancelled', labels.cancelledTitle, labels.cancelledDescription, 'current'),
      ],
    }
  }

  const rawStatus = resolveRawUnknownStatus(order) ?? labels.unknownTitle
  const description = `${labels.unknownDescriptionPrefix}${rawStatus}. ${labels.refreshSuggestion}`
  return {
    stageLabel: rawStatus,
    currentDescription: description,
    nodes: [
      createLifecycleNode('unknown', rawStatus, description, 'current'),
    ],
  }
}

export function createMallOrderActionView(
  order: OrderResponse | null,
  labels: MallOrderActionLabels,
  options: MallOrderActionOptions = {},
): MallOrderActionView {
  const phase = resolveLifecyclePhase(order)

  if (phase === 'created') {
    const paymentExists = Boolean(options.hasPayment)
    const disabledReason = paymentExists ? labels.paymentComplete : null
    return {
      payLabel: paymentExists ? labels.paid : labels.pay,
      canPay: !paymentExists && !options.isSubmittingPayment,
      payDisabledReason: disabledReason,
      cancelLabel: labels.cancel,
      canCancel: !options.isCancelling,
      cancelDisabledReason: null,
      actionHint: labels.actionReady,
    }
  }

  const reason = resolveActionDisabledReason(order, labels, phase)
  return {
    payLabel: phase === 'paidAwaitingShipment' || phase === 'shipped' ? labels.paid : labels.pay,
    canPay: false,
    payDisabledReason: reason,
    cancelLabel: labels.cancel,
    canCancel: false,
    cancelDisabledReason: reason,
    actionHint: reason,
  }
}

function isShipped(order: OrderResponse): boolean {
  return normalizeText(order.status) === 'shipped' || normalizeText(order.fulfillmentStatus) === 'shipped'
}

function resolveLifecyclePhase(order: OrderResponse | null): MallOrderLifecyclePhase {
  if (!order) {
    return 'unknown'
  }

  const orderStatus = normalizeText(order.status)
  const fulfillmentStatus = normalizeText(order.fulfillmentStatus)

  if (isShipped(order)) {
    return 'shipped'
  }
  if (orderStatus === 'cancelled') {
    return 'cancelled'
  }
  if (orderStatus === 'created') {
    return 'created'
  }
  if (orderStatus === 'paid' && (!fulfillmentStatus || fulfillmentStatus === 'unshipped')) {
    return 'paidAwaitingShipment'
  }

  return 'unknown'
}

function createLifecycleNode(
  key: MallOrderLifecycleNodeKey,
  title: string,
  description: string,
  state: MallOrderLifecycleNodeState,
): MallOrderLifecycleNode {
  return {
    key,
    title,
    description,
    state,
  }
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

function resolveActionDisabledReason(
  order: OrderResponse | null,
  labels: MallOrderActionLabels,
  phase: MallOrderLifecyclePhase,
): string {
  if (phase === 'paidAwaitingShipment') {
    return labels.paymentComplete
  }
  if (phase === 'shipped') {
    return labels.shipped
  }
  if (phase === 'cancelled') {
    return labels.cancelled
  }

  const rawStatus = resolveRawUnknownStatus(order)
  if (rawStatus) {
    return `${labels.unknownPrefix}${rawStatus}. ${labels.refreshSuggestion}`
  }

  return `${labels.unknownPrefix}${labels.refreshSuggestion}`
}

function resolveRawUnknownStatus(order: OrderResponse | null): string | null {
  if (!order) {
    return null
  }

  const orderStatus = normalizeText(order.status)
  const fulfillmentStatus = normalizeText(order.fulfillmentStatus)
  if (
    orderStatus === 'paid'
    && fulfillmentStatus
    && fulfillmentStatus !== 'unshipped'
    && fulfillmentStatus !== 'shipped'
    && fulfillmentStatus !== 'all'
  ) {
    return fulfillmentStatus
  }

  return orderStatus ?? fulfillmentStatus
}

function normalizeText(value: string | null | undefined): string | null {
  const trimmed = value?.trim()
  return trimmed ? trimmed : null
}

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

export interface MallPaymentRefreshLabels {
  available: string
  fromOrderSnapshot: string
  missingPaymentNo: string
  shipped: string
  cancelled: string
  unknownPrefix: string
}

export interface MallPaymentRefreshView {
  canRefresh: boolean
  disabledReason: string | null
  sourceDescription: string
}

export type MallOrderListFilter = 'all' | 'created' | 'paidAwaitingShipment' | 'shipped' | 'cancelled' | 'unknown'

export interface MallOrderListFilterOption {
  key: MallOrderListFilter
  label: string
  count: number
}

export interface MallOrderListFilterLabels {
  all: string
  created: string
  paidAwaitingShipment: string
  shipped: string
  cancelled: string
  unknown: string
}

export type MallOrderSearchMatchReason = 'orderNo' | 'orderId'

export interface MallOrderSearchResult {
  order: OrderResponse | null
  query: string
  matchReason: MallOrderSearchMatchReason | null
}

export interface MallOrderSearchContinuation {
  canSearchPreviousPage: boolean
  canSearchNextPage: boolean
}

export interface MallOrderPaginationState {
  page: number
  size: number
  total: number
}

export interface MallOrderPaginationLabels {
  summary: string
}

export interface MallOrderPaginationView {
  page: number
  totalPages: number
  size: number
  total: number
  summary: string
  canGoPrev: boolean
  canGoNext: boolean
}

export interface MallOrderEmptyStateLabels {
  noOrders: string
  filteredCurrentPage: string
  searchNoCurrentPage: string
}

export interface MallOrderEmptyStateView {
  kind: 'none' | 'noOrders' | 'filteredCurrentPage' | 'searchNoCurrentPage'
  message: string
}

export interface MallOrderLinkedDetailView {
  isLinkedOnly: boolean
  label: string
}

export interface MallOrderDeepLinkRecoveryLabels {
  noOrderId: string
  invalidOrderId: string
  restoreFailedPrefix: string
  suggestion: string
}

export interface MallOrderDeepLinkRecoveryView {
  isLinkIssue: boolean
  title: string
  message: string
  canClearLink: boolean
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

export function createMallPaymentRefreshView(
  order: OrderResponse | null,
  paymentNo: string,
  labels: MallPaymentRefreshLabels,
  options: { isRefreshing?: boolean } = {},
): MallPaymentRefreshView {
  const normalizedPaymentNo = normalizeText(paymentNo)
  const phase = resolveLifecyclePhase(order)

  if (phase === 'cancelled') {
    return {
      canRefresh: false,
      disabledReason: labels.cancelled,
      sourceDescription: labels.cancelled,
    }
  }
  if (phase === 'shipped') {
    return {
      canRefresh: false,
      disabledReason: labels.shipped,
      sourceDescription: labels.shipped,
    }
  }
  if (normalizedPaymentNo) {
    return {
      canRefresh: !options.isRefreshing,
      disabledReason: null,
      sourceDescription: `${labels.available}${normalizedPaymentNo}`,
    }
  }
  if (normalizeText(order?.status) === 'paid') {
    return {
      canRefresh: false,
      disabledReason: labels.fromOrderSnapshot,
      sourceDescription: labels.fromOrderSnapshot,
    }
  }
  if (phase === 'created') {
    return {
      canRefresh: false,
      disabledReason: labels.missingPaymentNo,
      sourceDescription: labels.missingPaymentNo,
    }
  }

  const rawStatus = resolveRawUnknownStatus(order)
  const missingReason = rawStatus
    ? `${labels.unknownPrefix}${rawStatus}. ${labels.missingPaymentNo}`
    : labels.missingPaymentNo

  return {
    canRefresh: false,
    disabledReason: missingReason,
    sourceDescription: missingReason,
  }
}

export function mergeOrderIntoList(orders: OrderResponse[], updatedOrder: OrderResponse): OrderResponse[] {
  let didMerge = false
  const nextOrders = orders.map((order) => {
    if (order.orderId !== updatedOrder.orderId) {
      return order
    }

    didMerge = true
    return {
      ...order,
      ...updatedOrder,
    }
  })

  return didMerge ? nextOrders : orders
}

export function upsertOrderIntoList(orders: OrderResponse[], updatedOrder: OrderResponse): OrderResponse[] {
  const merged = mergeOrderIntoList(orders, updatedOrder)
  return merged === orders ? [updatedOrder, ...orders] : merged
}

export function resolveMallOrderListFilter(order: OrderResponse): MallOrderListFilter {
  return resolveLifecyclePhase(order)
}

export function filterMallOrders(
  orders: OrderResponse[],
  filter: MallOrderListFilter,
): OrderResponse[] {
  if (filter === 'all') {
    return orders
  }

  return orders.filter((order) => resolveMallOrderListFilter(order) === filter)
}

export function createMallOrderListFilterOptions(
  orders: OrderResponse[],
  labels: MallOrderListFilterLabels,
): MallOrderListFilterOption[] {
  const counts: Record<MallOrderListFilter, number> = {
    all: orders.length,
    created: 0,
    paidAwaitingShipment: 0,
    shipped: 0,
    cancelled: 0,
    unknown: 0,
  }

  for (const order of orders) {
    counts[resolveMallOrderListFilter(order)] += 1
  }

  return [
    { key: 'all', label: labels.all, count: counts.all },
    { key: 'created', label: labels.created, count: counts.created },
    { key: 'paidAwaitingShipment', label: labels.paidAwaitingShipment, count: counts.paidAwaitingShipment },
    { key: 'shipped', label: labels.shipped, count: counts.shipped },
    { key: 'cancelled', label: labels.cancelled, count: counts.cancelled },
    { key: 'unknown', label: labels.unknown, count: counts.unknown },
  ]
}

export function createMallOrderPaginationView(
  state: MallOrderPaginationState,
  labels: MallOrderPaginationLabels,
): MallOrderPaginationView {
  const size = normalizePositiveInteger(state.size, 1)
  const total = Math.max(0, Math.floor(state.total))
  const totalPages = Math.max(1, Math.ceil(total / size))
  const page = Math.min(Math.max(1, Math.floor(state.page)), totalPages)

  return {
    page,
    totalPages,
    size,
    total,
    summary: interpolate(labels.summary, {
      page,
      totalPages,
      size,
      total,
    }),
    canGoPrev: page > 1,
    canGoNext: page < totalPages,
  }
}

export function findLoadedMallOrder(
  orders: OrderResponse[],
  rawQuery: string,
): MallOrderSearchResult {
  const query = normalizeText(rawQuery) ?? ''
  if (!query) {
    return {
      order: null,
      query,
      matchReason: null,
    }
  }

  const normalizedQuery = query.toLowerCase()
  const orderIdMatch = orders.find((order) => String(order.orderId) === query)
  if (orderIdMatch) {
    return {
      order: orderIdMatch,
      query,
      matchReason: 'orderId',
    }
  }

  const orderNoMatch = orders.find((order) => order.orderNo.toLowerCase().includes(normalizedQuery))
  if (orderNoMatch) {
    return {
      order: orderNoMatch,
      query,
      matchReason: 'orderNo',
    }
  }

  return {
    order: null,
    query,
    matchReason: null,
  }
}

export function createMallOrderSearchContinuation(
  result: MallOrderSearchResult,
  pagination: MallOrderPaginationView,
): MallOrderSearchContinuation {
  if (!result.query || result.order) {
    return {
      canSearchPreviousPage: false,
      canSearchNextPage: false,
    }
  }

  return {
    canSearchPreviousPage: pagination.canGoPrev,
    canSearchNextPage: pagination.canGoNext,
  }
}

export function createMallOrderEmptyStateView(
  orders: OrderResponse[],
  visibleOrders: OrderResponse[],
  searchResult: MallOrderSearchResult,
  labels: MallOrderEmptyStateLabels,
): MallOrderEmptyStateView {
  if (orders.length === 0) {
    return {
      kind: 'noOrders',
      message: labels.noOrders,
    }
  }

  if (searchResult.query && !searchResult.order) {
    return {
      kind: 'searchNoCurrentPage',
      message: interpolate(labels.searchNoCurrentPage, { query: searchResult.query }),
    }
  }

  if (visibleOrders.length === 0) {
    return {
      kind: 'filteredCurrentPage',
      message: labels.filteredCurrentPage,
    }
  }

  return {
    kind: 'none',
    message: '',
  }
}

export function createMallOrderLinkedDetailView(
  order: OrderResponse | null,
  currentPageOrders: OrderResponse[],
  label: string,
): MallOrderLinkedDetailView {
  if (!order) {
    return {
      isLinkedOnly: false,
      label: '',
    }
  }

  return {
    isLinkedOnly: !currentPageOrders.some((item) => item.orderId === order.orderId),
    label,
  }
}

export function createMallOrderDeepLinkRecoveryView(
  rawOrderId: string | null,
  errorMessage: string,
  labels: MallOrderDeepLinkRecoveryLabels,
): MallOrderDeepLinkRecoveryView {
  const orderId = normalizeText(rawOrderId)

  if (!orderId) {
    return {
      isLinkIssue: false,
      title: labels.noOrderId,
      message: labels.suggestion,
      canClearLink: false,
    }
  }

  const parsedOrderId = Number(orderId)
  if (!Number.isFinite(parsedOrderId) || parsedOrderId <= 0) {
    return {
      isLinkIssue: true,
      title: labels.invalidOrderId,
      message: `${labels.invalidOrderId}: ${orderId}. ${labels.suggestion}`,
      canClearLink: true,
    }
  }

  const fallback = `${labels.restoreFailedPrefix}${orderId}. ${labels.suggestion}`
  return {
    isLinkIssue: true,
    title: labels.restoreFailedPrefix,
    message: errorMessage ? `${errorMessage} ${labels.suggestion}` : fallback,
    canClearLink: true,
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

function normalizePositiveInteger(value: number, fallback: number): number {
  return Number.isFinite(value) && value > 0 ? Math.floor(value) : fallback
}

function interpolate(template: string, params: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (_match, key: string) => String(params[key] ?? ''))
}

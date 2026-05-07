<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useAppPreferences } from '../../composables/useAppPreferences'
import { getProduct, listProducts } from '../../services/productApi'
import { HttpClientError } from '../../services/httpClient'
import { useMallCart } from '../../composables/useMallCart'
import { useMallOrderStatus } from '../../composables/useMallOrderStatus'
import { useMallSession } from '../../composables/useMallSession'
import type { OrderResponse } from '../../types/api/order'
import type { ProductDetailResponse, ProductSummaryResponse } from '../../types/api/product'
import { formatDateTime, formatMoney } from '../../utils/format'
import type { CartItemInput } from './mallCartModel'
import {
  createMallOrderActionView,
  createMallOrderDeepLinkRecoveryView,
  createMallOrderEmptyStateView,
  createMallOrderFulfillmentView,
  createMallOrderLifecycleTimeline,
  createMallOrderLinkedDetailView,
  createMallPaymentRefreshView,
  createMallOrderPaginationView,
  createMallOrderSearchContinuation,
  describeMallOrderListSummary,
  filterMallOrders,
  findLoadedMallOrder,
  createMallOrderListFilterOptions,
  getMallOrderStatusLabel,
} from './mallOrderStatusModel'
import type { MallOrderListFilter, MallOrderSearchResult } from './mallOrderStatusModel'
import ProductCheckoutPanel from './ProductCheckoutPanel.vue'

const DEFAULT_PAGE_SIZE = 12

const { t } = useAppPreferences()
const mallSession = useMallSession()
const orderStatus = useMallOrderStatus()
const cart = useMallCart({
  session: computed(() => mallSession.state.session),
})
const products = ref<ProductSummaryResponse[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(DEFAULT_PAGE_SIZE)
const isLoadingProducts = ref(false)
const productError = ref('')
const selectedProduct = ref<ProductDetailResponse | null>(null)
const isLoadingDetail = ref(false)
const detailError = ref('')
const isRestoringOrderFromUrl = ref(false)
const orderListFilter = ref<MallOrderListFilter>('all')
const orderSearchQuery = ref('')
const orderSearchFeedback = ref('')
const orderSearchResult = ref<MallOrderSearchResult>({
  order: null,
  query: '',
  matchReason: null,
})
const deepLinkOrderId = ref<string | null>(null)
const deepLinkFailureMessage = ref('')
const linkedDetailOrderId = ref<number | null>(null)
const loginForm = reactive({
  shopId: resolveDefaultShopId(),
  usernameOrMobile: '',
  password: '',
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const canGoPrev = computed(() => page.value > 1)
const canGoNext = computed(() => page.value < totalPages.value)
const orderPagination = computed(() => createMallOrderPaginationView(
  {
    page: orderStatus.page.value,
    size: orderStatus.size.value,
    total: orderStatus.total.value,
  },
  getOrderPaginationLabels(),
))
const currentFulfillment = computed(() => createMallOrderFulfillmentView(
  orderStatus.order.value,
  getFulfillmentLabels(),
))
const currentLifecycle = computed(() => createMallOrderLifecycleTimeline(
  orderStatus.order.value,
  getLifecycleLabels(),
))
const currentAction = computed(() => createMallOrderActionView(
  orderStatus.order.value,
  getActionLabels(),
  {
    hasPayment: Boolean(orderStatus.payment.value),
    isSubmittingPayment: orderStatus.isSubmittingPayment.value,
    isCancelling: orderStatus.isCancelling.value,
  },
))
const currentPaymentRefresh = computed(() => createMallPaymentRefreshView(
  orderStatus.order.value,
  orderStatus.paymentNo.value,
  getPaymentRefreshLabels(),
  {
    isRefreshing: orderStatus.isRefreshingPayment.value,
  },
))
const orderFilterOptions = computed(() => createMallOrderListFilterOptions(
  orderStatus.orders.value,
  getOrderListFilterLabels(),
))
const visibleOrders = computed(() => filterMallOrders(orderStatus.orders.value, orderListFilter.value))
const orderSearchContinuation = computed(() => createMallOrderSearchContinuation(
  orderSearchResult.value,
  orderPagination.value,
))
const orderEmptyState = computed(() => createMallOrderEmptyStateView(
  orderStatus.orders.value,
  visibleOrders.value,
  orderSearchResult.value,
  getOrderEmptyStateLabels(),
))
const linkedDetail = computed(() => createMallOrderLinkedDetailView(
  orderStatus.order.value,
  linkedDetailOrderId.value === orderStatus.order.value?.orderId ? [] : orderStatus.orders.value,
  t('mall.orders.fromOrderLink'),
))
const deepLinkRecovery = computed(() => createMallOrderDeepLinkRecoveryView(
  deepLinkOrderId.value,
  deepLinkFailureMessage.value,
  getDeepLinkRecoveryLabels(),
))
const cartRestoreMessage = computed(() => describeCartRestore())
const cartCheckoutGuidance = computed(() => describeCartCheckoutFailure())
const paymentFailureGuidance = computed(() => describePaymentFailure())

onMounted(() => {
  mallSession.bootstrap()
  void fetchProducts()
  if (mallSession.isAuthenticated.value) {
    void loadOrderPage()
    void restoreOrderFromUrl()
  }
})

async function fetchProducts(nextPage = page.value) {
  isLoadingProducts.value = true
  productError.value = ''

  try {
    const result = await listProducts({ page: nextPage, size: size.value })
    products.value = result.data.items
    total.value = result.data.total
    page.value = result.data.page
    size.value = result.data.size
    if (!selectedProduct.value && result.data.items[0]) {
      await openProduct(result.data.items[0].productId)
    }
  } catch (caught) {
    products.value = []
    total.value = 0
    productError.value = describeError(caught, t('mall.catalog.loadFallback'))
  } finally {
    isLoadingProducts.value = false
  }
}

async function openProduct(productId: number) {
  isLoadingDetail.value = true
  detailError.value = ''

  try {
    const result = await getProduct(productId)
    selectedProduct.value = result.data
  } catch (caught) {
    selectedProduct.value = null
    detailError.value = describeError(caught, t('mall.catalog.detailFallback'))
  } finally {
    isLoadingDetail.value = false
  }
}

async function submitLogin() {
  await mallSession.login({
    shopId: loginForm.shopId,
    usernameOrMobile: loginForm.usernameOrMobile.trim(),
    password: loginForm.password,
  })
  loginForm.password = ''
  if (mallSession.isAuthenticated.value) {
    await loadOrderPage()
    await restoreOrderFromUrl()
  }
}

async function handleOrderCreated(order: OrderResponse) {
  orderStatus.acceptCreatedOrder(order)
  replaceOrderUrl(order.orderId, '')
  linkedDetailOrderId.value = null
  await loadOrderPage(1)
}

async function selectOrder(orderId: number) {
  replaceOrderUrl(orderId, '')
  deepLinkOrderId.value = null
  deepLinkFailureMessage.value = ''
  linkedDetailOrderId.value = null
  await orderStatus.loadOrder(orderId, '')
}

async function refreshSelectedOrder() {
  await orderStatus.refreshCurrentOrder()
}

async function cancelCurrentOrder() {
  const cancelled = await orderStatus.cancelCurrentOrder()
  if (cancelled) {
    replaceOrderUrl(cancelled.orderId, '')
    await loadOrderPage()
  }
}

function handleAddToCart(item: CartItemInput) {
  cart.addItem(item)
}

async function checkoutCart() {
  const order = await cart.submitCheckout()
  if (order) {
    await handleOrderCreated(order)
  }
}

async function submitPaymentForCurrentOrder() {
  const session = mallSession.state.session
  if (!session) {
    return
  }

  const payment = await orderStatus.submitPayment(session)
  if (payment) {
    replaceOrderUrl(payment.orderId, payment.paymentNo)
    await orderStatus.loadOrder(payment.orderId, payment.paymentNo)
    await loadOrderPage()
  }
}

async function loadOrderPage(nextPage = orderStatus.page.value) {
  const response = await orderStatus.loadOrders(nextPage)
  if (response && orderSearchResult.value.query) {
    const nextResult = findLoadedMallOrder(orderStatus.orders.value, orderSearchResult.value.query)
    orderSearchResult.value = nextResult
    orderSearchFeedback.value = describeOrderSearchResult(nextResult)
  }
  return response
}

async function continueOrderSearch(nextPage: number) {
  const response = await loadOrderPage(nextPage)
  if (!response || !orderSearchResult.value.query) {
    return
  }
  if (orderSearchResult.value.order) {
    orderListFilter.value = 'all'
    await selectOrder(orderSearchResult.value.order.orderId)
  }
}

async function restoreOrderFromUrl() {
  if (typeof window === 'undefined') {
    return
  }
  const params = new URLSearchParams(window.location.search)
  const rawOrderId = params.get('orderId')
  if (!rawOrderId) {
    deepLinkOrderId.value = null
    deepLinkFailureMessage.value = ''
    return
  }

  deepLinkOrderId.value = rawOrderId
  deepLinkFailureMessage.value = ''
  const orderId = Number(rawOrderId)
  const paymentNo = params.get('paymentNo') ?? ''
  if (Number.isFinite(orderId) && orderId > 0) {
    isRestoringOrderFromUrl.value = true
    try {
      const wasLoadedOnCurrentPage = orderStatus.orders.value.some((item) => item.orderId === orderId)
      const restored = await orderStatus.loadOrder(orderId, paymentNo)
      if (restored) {
        deepLinkOrderId.value = null
        linkedDetailOrderId.value = wasLoadedOnCurrentPage ? null : restored.orderId
      } else {
        deepLinkFailureMessage.value = orderStatus.errorMessage.value
      }
    } finally {
      isRestoringOrderFromUrl.value = false
    }
  }
}

function replaceOrderUrl(orderId: number, paymentNo: string) {
  if (typeof window === 'undefined') {
    return
  }
  const params = new URLSearchParams(window.location.search)
  params.set('orderId', String(orderId))
  if (paymentNo) {
    params.set('paymentNo', paymentNo)
  } else {
    params.delete('paymentNo')
  }
  const nextSearch = params.toString()
  const nextUrl = `${window.location.pathname}${nextSearch ? `?${nextSearch}` : ''}${window.location.hash}`
  window.history.replaceState(null, '', nextUrl)
}

async function clearOrderUrl() {
  if (typeof window === 'undefined') {
    return
  }
  const params = new URLSearchParams(window.location.search)
  params.delete('orderId')
  params.delete('paymentNo')
  const nextSearch = params.toString()
  const nextUrl = `${window.location.pathname}${nextSearch ? `?${nextSearch}` : ''}${window.location.hash}`
  window.history.replaceState(null, '', nextUrl)
  deepLinkOrderId.value = null
  deepLinkFailureMessage.value = ''
  linkedDetailOrderId.value = null
  await loadOrderPage()
}

async function searchLoadedOrder() {
  const result = findLoadedMallOrder(orderStatus.orders.value, orderSearchQuery.value)
  orderSearchResult.value = result
  orderSearchFeedback.value = ''

  if (!result.query) {
    return
  }

  if (!result.order) {
    orderSearchFeedback.value = describeOrderSearchResult(result)
    return
  }

  orderSearchFeedback.value = describeOrderSearchResult(result)
  orderListFilter.value = 'all'
  await selectOrder(result.order.orderId)
}

function clearOrderSearch() {
  orderSearchQuery.value = ''
  orderSearchFeedback.value = ''
  orderSearchResult.value = {
    order: null,
    query: '',
    matchReason: null,
  }
}

function describeOrderSearchResult(result: MallOrderSearchResult): string {
  if (!result.query) {
    return ''
  }
  if (!result.order) {
    return t('mall.orders.searchNoCurrentPage', { query: result.query })
  }
  return result.matchReason === 'orderNo'
    ? t('mall.orders.searchMatchedOrderNo', { query: result.query })
    : t('mall.orders.searchMatchedOrderId', { query: result.query })
}

function describePriceRange(product: ProductSummaryResponse): string {
  if (product.minPriceCent === product.maxPriceCent) {
    return formatMoney(product.minPriceCent)
  }

  return `${formatMoney(product.minPriceCent)} - ${formatMoney(product.maxPriceCent)}`
}

function describeError(caught: unknown, fallback: string): string {
  if (caught instanceof HttpClientError) {
    const trace = caught.traceId ? ` ${t('common.traceId')} ${caught.traceId}.` : ''
    return `${caught.code}: ${caught.message}${trace}`
  }

  return fallback
}

function describeOrderSummary(order: OrderResponse): string {
  return describeMallOrderListSummary(order, getOrderSummaryLabels())
}

function describeOrderStage(order: OrderResponse): string {
  return createMallOrderLifecycleTimeline(order, getLifecycleLabels()).stageLabel
}

function describeOrderStatus(status: string): string {
  return getMallOrderStatusLabel(status, getOrderStatusLabels())
}

function describeCartRestore(): string {
  const scope = cart.sessionScope.value
  if (!scope) {
    return t('mall.cart.restoreSignedOut')
  }

  const params = {
    shopId: scope.shopId,
    userId: scope.userId,
  }

  switch (cart.restoreResult.value.status) {
    case 'restored':
      return t('mall.cart.restoreRestored', {
        ...params,
        count: cart.items.value.length,
      })
    case 'invalid':
      return t('mall.cart.restoreInvalid', params)
    case 'unavailable':
      return t('mall.cart.restoreUnavailable', params)
    case 'empty':
      return t('mall.cart.restoreEmpty', params)
    case 'signedOut':
    default:
      return t('mall.cart.restoreSignedOut')
  }
}

function describeCartCheckoutFailure(): string {
  const failure = cart.checkoutFailure.value
  if (!failure) {
    return ''
  }

  switch (failure.kind) {
    case 'stock':
      return t('mall.cart.failureStock')
    case 'skuUnavailable':
      return t('mall.cart.failureSkuUnavailable')
    case 'auth':
      return t('mall.cart.failureAuth')
    case 'validation':
      return t('mall.cart.failureValidation')
    case 'system':
      return t('mall.cart.failureSystem')
    case 'unknown':
    default:
      return t('mall.cart.failureUnknown')
  }
}

function describePaymentFailure(): string {
  const failure = orderStatus.paymentFailure.value
  if (!failure) {
    return ''
  }

  switch (failure.kind) {
    case 'auth':
      return t('mall.orders.paymentFailureAuth')
    case 'notPayable':
      return t('mall.orders.paymentFailureNotPayable')
    case 'duplicatePayment':
      return t('mall.orders.paymentFailureDuplicate')
    case 'validation':
      return t('mall.orders.paymentFailureValidation')
    case 'system':
      return t('mall.orders.paymentFailureSystem')
    case 'unknown':
    default:
      return t('mall.orders.paymentFailureUnknown')
  }
}

function getOrderSummaryLabels() {
  return {
    created: t('mall.orders.statusCreated'),
    paid: t('mall.orders.statusPaid'),
    paidAwaitingShipment: t('mall.orders.statusPaidAwaitingShipment'),
    cancelled: t('mall.orders.statusCancelled'),
    shipped: t('mall.orders.statusShippedSummary'),
    unknown: t('common.unknown'),
  }
}

function getOrderStatusLabels() {
  return {
    created: t('mall.orders.statusCreated'),
    paid: t('mall.orders.statusPaid'),
    paidAwaitingShipment: t('mall.orders.statusPaidAwaitingShipment'),
    cancelled: t('mall.orders.statusCancelled'),
    shipped: t('mall.orders.statusShipped'),
    unknown: t('common.unknown'),
  }
}

function getOrderListFilterLabels() {
  return {
    all: t('mall.orders.filterAll'),
    created: t('mall.orders.filterCreated'),
    paidAwaitingShipment: t('mall.orders.filterPaidAwaitingShipment'),
    shipped: t('mall.orders.filterShipped'),
    cancelled: t('mall.orders.filterCancelled'),
    unknown: t('mall.orders.filterUnknown'),
  }
}

function getOrderPaginationLabels() {
  return {
    summary: t('mall.orders.pageSummary', {
      page: '{page}',
      totalPages: '{totalPages}',
      total: '{total}',
      size: '{size}',
    }),
  }
}

function getOrderEmptyStateLabels() {
  return {
    noOrders: t('mall.orders.empty'),
    filteredCurrentPage: t('mall.orders.filterEmptyCurrentPage'),
    searchNoCurrentPage: t('mall.orders.searchNoCurrentPage', { query: '{query}' }),
  }
}

function getFulfillmentLabels() {
  return {
    awaitingShipment: t('mall.orders.awaitingShipment'),
    shipped: t('mall.orders.statusShipped'),
    notReady: t('mall.orders.statusCreated'),
    cancelled: t('mall.orders.statusCancelled'),
    unknown: t('common.unknown'),
    shippedMessage: t('mall.orders.logisticsShippedMessage'),
    awaitingShipmentMessage: t('mall.orders.logisticsAwaitingShipmentMessage'),
    notReadyMessage: t('mall.orders.logisticsNotReadyMessage'),
    cancelledMessage: t('mall.orders.logisticsCancelledMessage'),
    unknownMessage: t('mall.orders.logisticsUnknownMessage'),
    unknownStatusPrefix: t('mall.orders.logisticsUnknownStatusPrefix'),
    carrierPending: t('mall.orders.carrierPending'),
    trackingNoPending: t('mall.orders.trackingNoPending'),
  }
}

function getLifecycleLabels() {
  return {
    createdTitle: t('mall.orders.lifecycleCreatedTitle'),
    createdDescription: t('mall.orders.lifecycleCreatedDescription'),
    paidAwaitingShipmentTitle: t('mall.orders.lifecyclePaidTitle'),
    paidAwaitingShipmentDescription: t('mall.orders.lifecyclePaidDescription'),
    shippedTitle: t('mall.orders.lifecycleShippedTitle'),
    shippedDescription: t('mall.orders.lifecycleShippedDescription'),
    cancelledTitle: t('mall.orders.lifecycleCancelledTitle'),
    cancelledDescription: t('mall.orders.lifecycleCancelledDescription'),
    unknownTitle: t('mall.orders.lifecycleUnknownTitle'),
    unknownDescriptionPrefix: t('mall.orders.lifecycleUnknownPrefix'),
    refreshSuggestion: t('mall.orders.lifecycleRefreshSuggestion'),
  }
}

function getActionLabels() {
  return {
    pay: t('mall.orders.pay'),
    paid: t('mall.orders.paid'),
    cancel: t('mall.orders.cancel'),
    actionReady: t('mall.orders.actionReady'),
    paymentComplete: t('mall.orders.actionPaymentComplete'),
    shipped: t('mall.orders.actionShipped'),
    cancelled: t('mall.orders.actionCancelled'),
    unknownPrefix: t('mall.orders.actionUnknownPrefix'),
    refreshSuggestion: t('mall.orders.actionRefreshSuggestion'),
  }
}

function getPaymentRefreshLabels() {
  return {
    available: t('mall.orders.paymentRefreshAvailable'),
    fromOrderSnapshot: t('mall.orders.paymentFromOrderSnapshot'),
    missingPaymentNo: t('mall.orders.paymentNoMissing'),
    shipped: t('mall.orders.paymentRefreshDisabledShipped'),
    cancelled: t('mall.orders.paymentRefreshDisabledCancelled'),
    unknownPrefix: t('mall.orders.paymentRefreshUnknownPrefix'),
  }
}

function getDeepLinkRecoveryLabels() {
  return {
    noOrderId: t('mall.orders.noOrderLink'),
    invalidOrderId: t('mall.orders.invalidOrderLink'),
    restoreFailedPrefix: t('mall.orders.restoreFailedPrefix'),
    suggestion: t('mall.orders.restoreRecentSuggestion'),
  }
}

function resolveDefaultShopId(): number {
  const configured = Number(import.meta.env.VITE_DEFAULT_SHOP_ID ?? 1)
  return Number.isFinite(configured) && configured > 0 ? configured : 1
}
</script>

<template>
  <main class="mall-shell">
    <section class="mall-hero">
      <div class="hero-copy">
        <p class="eyebrow">SanguiShop</p>
        <h1>{{ t('mall.title') }}</h1>
        <p class="hero-subtitle">{{ t('mall.subtitle') }}</p>
      </div>

      <form v-if="!mallSession.isAuthenticated.value" class="login-strip" @submit.prevent="submitLogin">
        <input v-model.number="loginForm.shopId" type="number" min="1" :aria-label="t('common.shopId')">
        <input v-model.trim="loginForm.usernameOrMobile" type="text" autocomplete="username" :placeholder="t('common.usernameOrMobile')" :aria-label="t('common.usernameOrMobile')">
        <input v-model="loginForm.password" type="password" autocomplete="current-password" :placeholder="t('common.password')" :aria-label="t('common.password')">
        <button type="submit" :disabled="mallSession.state.isSubmitting">
          {{ mallSession.state.isSubmitting ? t('common.signingIn') : t('common.signIn') }}
        </button>
        <p v-if="mallSession.state.error" class="auth-error">
          {{ describeError(mallSession.state.error, t('mall.loginFallback')) }}
        </p>
      </form>

      <div v-else class="session-strip">
        <span>{{ t('mall.signedInAs', { userId: mallSession.state.session?.userId ?? '--', shopId: mallSession.state.session?.shopId ?? '--' }) }}</span>
        <button type="button" @click="mallSession.signOut()">{{ t('common.signOut') }}</button>
      </div>
    </section>

    <section v-if="mallSession.isAuthenticated.value" class="order-band">
      <div class="order-grid">
        <aside class="order-list">
          <div class="list-header">
            <div>
              <p class="eyebrow">{{ t('mall.orders.kicker') }}</p>
              <h2>{{ t('mall.orders.title') }}</h2>
            </div>
            <button type="button" class="text-action" :disabled="orderStatus.isLoadingOrders.value" @click="loadOrderPage()">
              {{ orderStatus.isLoadingOrders.value ? t('common.refreshing') : t('common.refresh') }}
            </button>
          </div>

          <div class="order-tools" :aria-label="t('mall.orders.filterLabel')">
            <div class="order-segments">
              <button
                v-for="option in orderFilterOptions"
                :key="option.key"
                type="button"
                :class="orderListFilter === option.key ? 'segment active' : 'segment'"
                @click="orderListFilter = option.key"
              >
                <span>{{ option.label }}</span>
                <strong>{{ option.count }}</strong>
              </button>
            </div>

            <form class="order-search" @submit.prevent="searchLoadedOrder">
              <input
                v-model.trim="orderSearchQuery"
                type="search"
                :placeholder="t('mall.orders.searchPlaceholder')"
                :aria-label="t('mall.orders.searchLabel')"
              >
              <button type="submit" class="text-action">{{ t('mall.orders.searchAction') }}</button>
              <button type="button" class="text-action subtle" @click="clearOrderSearch">{{ t('common.dismiss') }}</button>
            </form>
            <div v-if="orderSearchFeedback" class="order-search-feedback">
              <p>{{ orderSearchFeedback }}</p>
              <div v-if="orderSearchContinuation.canSearchPreviousPage || orderSearchContinuation.canSearchNextPage" class="order-search-actions">
                <button
                  type="button"
                  class="text-action subtle"
                  :disabled="!orderSearchContinuation.canSearchPreviousPage || orderStatus.isLoadingOrders.value"
                  @click="continueOrderSearch(orderPagination.page - 1)"
                >
                  {{ t('mall.orders.searchPrevPage') }}
                </button>
                <button
                  type="button"
                  class="text-action subtle"
                  :disabled="!orderSearchContinuation.canSearchNextPage || orderStatus.isLoadingOrders.value"
                  @click="continueOrderSearch(orderPagination.page + 1)"
                >
                  {{ t('mall.orders.searchNextPage') }}
                </button>
              </div>
            </div>
          </div>

          <div v-if="orderStatus.isLoadingOrders.value" class="status-block">{{ t('mall.orders.loading') }}</div>
          <div v-else-if="orderEmptyState.kind !== 'none'" class="status-block">
            {{ orderEmptyState.message }}
          </div>
          <div v-else class="order-cards">
            <button
              v-for="order in visibleOrders"
              :key="order.orderId"
              type="button"
              :class="orderStatus.order.value?.orderId === order.orderId ? 'order-card active' : 'order-card'"
              @click="selectOrder(order.orderId)"
            >
              <span>{{ order.orderNo }}</span>
              <strong>{{ formatMoney(order.totalAmountCent) }}</strong>
              <small class="order-card-summary">{{ describeOrderSummary(order) }}</small>
              <small class="order-stage-row">
                <span class="order-stage-badge">{{ describeOrderStage(order) }}</span>
                <span>{{ formatDateTime(order.createdAt) }}</span>
              </small>
              <small v-if="orderStatus.order.value?.orderId === order.orderId" class="order-card-updated">
                {{ t('mall.orders.lastUpdated', { time: formatDateTime(order.updatedAt) }) }}
              </small>
            </button>
          </div>

          <div class="pager">
            <button type="button" :disabled="!orderPagination.canGoPrev" @click="loadOrderPage(orderPagination.page - 1)">{{ t('common.prev') }}</button>
            <span>{{ orderPagination.summary }}</span>
            <button type="button" :disabled="!orderPagination.canGoNext" @click="loadOrderPage(orderPagination.page + 1)">{{ t('common.next') }}</button>
          </div>
        </aside>

        <section class="order-detail-panel">
          <div class="list-header">
            <div>
              <p class="eyebrow">{{ t('mall.orders.resultKicker') }}</p>
              <h2>{{ t('mall.orders.resultTitle') }}</h2>
            </div>
            <div class="refresh-control">
              <button
                type="button"
                class="text-action"
                :disabled="!orderStatus.order.value || orderStatus.isLoadingOrder.value"
                @click="refreshSelectedOrder()"
              >
                {{ orderStatus.isLoadingOrder.value ? t('common.refreshing') : t('mall.orders.refreshOrder') }}
              </button>
              <small>{{ t('mall.orders.refreshOrderHint') }}</small>
            </div>
          </div>

          <div v-if="deepLinkRecovery.isLinkIssue && !orderStatus.order.value" class="status-block danger">
            <strong>{{ deepLinkRecovery.title }}</strong>
            <p>{{ deepLinkRecovery.message }}</p>
            <small>{{ t('mall.orders.restoreErrorSuggestion') }}</small>
            <button type="button" @click="loadOrderPage()">{{ t('mall.orders.backToRecent') }}</button>
            <button v-if="deepLinkRecovery.canClearLink" type="button" class="text-action" @click="clearOrderUrl">
              {{ t('mall.orders.clearOrderLink') }}
            </button>
          </div>
          <div v-else-if="orderStatus.errorMessage.value && !orderStatus.order.value" class="status-block danger">
            <p>{{ orderStatus.errorMessage }}</p>
            <button type="button" @click="loadOrderPage()">{{ t('mall.orders.backToRecent') }}</button>
          </div>
          <div v-else-if="orderStatus.isLoadingOrder.value && isRestoringOrderFromUrl && !orderStatus.order.value" class="status-block">{{ t('mall.orders.restoringDetail') }}</div>
          <div v-else-if="orderStatus.isLoadingOrder.value && !orderStatus.order.value" class="status-block">{{ t('mall.orders.loadingDetail') }}</div>
          <div v-else-if="!orderStatus.order.value" class="status-block">{{ t('mall.orders.emptyDetail') }}</div>
          <div v-else class="order-detail">
            <div v-if="orderStatus.orderRefreshResult.value === 'success'" class="inline-feedback success">
              {{ t('mall.orders.refreshSuccess') }}
            </div>
            <div v-if="orderStatus.paymentFailure.value" class="inline-feedback danger">
              <strong>{{ paymentFailureGuidance }}</strong>
              <span>{{ orderStatus.errorMessage }}</span>
              <small v-if="orderStatus.paymentNo.value">{{ currentPaymentRefresh.sourceDescription }}</small>
            </div>
            <div v-else-if="orderStatus.errorMessage.value" class="inline-feedback danger">
              <strong>{{ t('mall.orders.refreshFailedKeepDetail') }}</strong>
              <span>{{ orderStatus.errorMessage }}</span>
            </div>

            <div class="detail-facts">
              <span v-if="linkedDetail.isLinkedOnly">{{ linkedDetail.label }}</span>
              <span>{{ describeOrderStatus(orderStatus.order.value.status) }}</span>
              <span>{{ t('mall.orders.paymentStatus', { status: orderStatus.paymentStatus.value }) }}</span>
              <span>{{ t('mall.orders.fulfillmentStatus', { status: currentFulfillment.statusLabel }) }}</span>
              <span>{{ t('mall.orders.lastUpdated', { time: formatDateTime(orderStatus.order.value.updatedAt) }) }}</span>
            </div>

            <div class="order-headline">
              <div>
                <p class="eyebrow">{{ t('mall.orders.orderKicker', { orderId: orderStatus.order.value.orderId }) }}</p>
                <h3>{{ orderStatus.order.value.orderNo }}</h3>
              </div>
              <strong>{{ formatMoney(orderStatus.order.value.totalAmountCent) }}</strong>
            </div>

            <section class="lifecycle-panel" :aria-label="t('mall.orders.lifecycleTitle')">
              <div class="lifecycle-heading">
                <div>
                  <p class="eyebrow">{{ t('mall.orders.lifecycleKicker') }}</p>
                  <h4>{{ currentLifecycle.stageLabel }}</h4>
                </div>
                <p>{{ currentLifecycle.currentDescription }}</p>
              </div>
              <ol class="lifecycle-list">
                <li
                  v-for="node in currentLifecycle.nodes"
                  :key="node.key"
                  :class="`lifecycle-node ${node.state}`"
                >
                  <span class="lifecycle-marker" aria-hidden="true"></span>
                  <div>
                    <strong>{{ node.title }}</strong>
                    <p>{{ node.description }}</p>
                  </div>
                </li>
              </ol>
            </section>

            <section class="logistics-panel" :aria-label="t('mall.orders.logisticsTitle')">
              <div class="logistics-heading">
                <div>
                  <p class="eyebrow">{{ t('mall.orders.logisticsKicker') }}</p>
                  <h4>{{ t('mall.orders.logisticsTitle') }}</h4>
                </div>
                <span>{{ currentFulfillment.statusLabel }}</span>
              </div>
              <p>{{ currentFulfillment.message }}</p>
              <dl v-if="currentFulfillment.showShipmentFields" class="logistics-grid">
                <div>
                  <dt>{{ t('mall.orders.carrier') }}</dt>
                  <dd>{{ currentFulfillment.carrier }}</dd>
                </div>
                <div>
                  <dt>{{ t('mall.orders.trackingNo') }}</dt>
                  <dd>{{ currentFulfillment.trackingNo }}</dd>
                </div>
                <div>
                  <dt>{{ t('mall.orders.shippedAt') }}</dt>
                  <dd>{{ currentFulfillment.shippedAt ? formatDateTime(currentFulfillment.shippedAt) : t('mall.orders.shippedAtPending') }}</dd>
                </div>
              </dl>
            </section>

            <div class="order-items">
              <div v-for="item in orderStatus.order.value.items" :key="`${item.productId}-${item.skuId}`" class="order-item">
                <span>{{ item.skuName }}</span>
                <small>SKU {{ item.skuId }} x {{ item.quantity }}</small>
                <strong>{{ formatMoney(item.lineAmountCent) }}</strong>
              </div>
            </div>

            <div class="checkout-actions">
              <button
                type="button"
                class="primary-action"
                :disabled="!currentAction.canPay || !orderStatus.canPay.value"
                @click="submitPaymentForCurrentOrder()"
              >
                {{ orderStatus.isSubmittingPayment.value ? t('mall.orders.paying') : currentAction.payLabel }}
              </button>
              <button
                type="button"
                class="secondary-action"
                :disabled="!currentPaymentRefresh.canRefresh"
                :title="currentPaymentRefresh.disabledReason ?? currentPaymentRefresh.sourceDescription"
                @click="orderStatus.refreshPayment()"
              >
                {{ orderStatus.isRefreshingPayment.value ? t('common.refreshing') : t('mall.orders.refreshPayment') }}
              </button>
              <button
                type="button"
                class="danger-action"
                :disabled="!currentAction.canCancel || !orderStatus.canCancel.value"
                @click="cancelCurrentOrder()"
              >
                {{ orderStatus.isCancelling.value ? t('mall.orders.cancelling') : currentAction.cancelLabel }}
              </button>
            </div>
            <p class="payment-source">{{ currentPaymentRefresh.sourceDescription }}</p>
            <p class="action-boundary">{{ currentAction.actionHint }}</p>
          </div>
        </section>
      </div>
    </section>

    <section v-if="mallSession.isAuthenticated.value" class="cart-band">
      <div class="cart-panel">
        <div class="list-header">
          <div>
            <p class="eyebrow">{{ t('mall.cart.kicker') }}</p>
            <h2>{{ t('mall.cart.title') }}</h2>
          </div>
          <div class="cart-summary">
            <span>{{ t('common.items', { count: cart.itemCount.value }) }}</span>
            <strong>{{ formatMoney(cart.totalPreviewCent.value) }}</strong>
          </div>
        </div>
        <p class="cart-note">{{ cartRestoreMessage }}</p>
        <p class="cart-note">{{ t('mall.cart.stockSnapshotBoundary') }}</p>

        <div v-if="cart.errorMessage.value" class="status-block danger">
          <p>{{ cartCheckoutGuidance }}</p>
          <small>{{ cart.errorMessage }}</small>
        </div>
        <div v-else-if="cart.items.value.length === 0" class="status-block">{{ t('mall.cart.empty') }}</div>
        <div v-else class="cart-content">
          <div class="cart-items">
            <article v-for="item in cart.items.value" :key="item.skuId" class="cart-item">
              <div>
                <strong>{{ item.productName }}</strong>
                <small>{{ t('mall.cart.stockSnapshot', { skuName: item.skuName, stock: item.availableStock }) }}</small>
              </div>
              <span>{{ formatMoney(item.priceCent) }}</span>
              <div class="cart-stepper">
                <button type="button" :disabled="cart.isCheckingOut.value || item.quantity <= 1" @click="cart.setQuantity(item.skuId, item.quantity - 1)">-</button>
                <output>{{ item.quantity }}</output>
                <button
                  type="button"
                  :disabled="cart.isCheckingOut.value || item.quantity >= item.availableStock"
                  @click="cart.setQuantity(item.skuId, item.quantity + 1)"
                >
                  +
                </button>
              </div>
              <strong>{{ formatMoney(item.priceCent * item.quantity) }}</strong>
              <button type="button" class="text-action subtle" :disabled="cart.isCheckingOut.value" @click="cart.removeItem(item.skuId)">{{ t('mall.cart.remove') }}</button>
            </article>
          </div>

          <div class="cart-actions">
            <button type="button" class="secondary-action" :disabled="cart.isCheckingOut.value" @click="cart.clearCart()">{{ t('mall.cart.clear') }}</button>
            <button
              type="button"
              class="primary-action"
              :disabled="!cart.canCheckout.value"
              @click="checkoutCart()"
            >
              {{ cart.isCheckingOut.value ? t('mall.cart.checkingOut') : t('mall.cart.checkout') }}
            </button>
          </div>
          <p class="cart-note">{{ t('mall.cart.requestIdHint', { requestId: cart.orderRequestId.value }) }}</p>
        </div>
      </div>
    </section>

    <section class="catalog-band">
      <div class="catalog-grid">
        <aside class="product-list">
          <div class="list-header">
            <div>
              <p class="eyebrow">{{ t('mall.catalog.kicker') }}</p>
              <h2>{{ t('mall.catalog.title') }}</h2>
            </div>
            <span>{{ t('common.items', { count: total }) }}</span>
          </div>

          <div v-if="isLoadingProducts" class="status-block">{{ t('mall.catalog.loading') }}</div>
          <div v-else-if="productError" class="status-block danger">
            <p>{{ productError }}</p>
            <button type="button" @click="fetchProducts()">{{ t('common.retry') }}</button>
          </div>
          <div v-else-if="products.length === 0" class="status-block">{{ t('mall.catalog.empty') }}</div>
          <div v-else class="product-cards">
            <button
              v-for="product in products"
              :key="product.productId"
              type="button"
              :class="selectedProduct?.productId === product.productId ? 'product-card active' : 'product-card'"
              @click="openProduct(product.productId)"
            >
              <span class="product-name">{{ product.productName }}</span>
              <strong>{{ describePriceRange(product) }}</strong>
              <small>{{ t('mall.catalog.stockOnDetail', { status: product.status }) }}</small>
            </button>
          </div>

          <div class="pager">
            <button type="button" :disabled="!canGoPrev" @click="fetchProducts(page - 1)">{{ t('common.prev') }}</button>
            <span>{{ page }} / {{ totalPages }}</span>
            <button type="button" :disabled="!canGoNext" @click="fetchProducts(page + 1)">{{ t('common.next') }}</button>
          </div>
        </aside>

        <section class="product-detail">
          <div v-if="isLoadingDetail" class="status-block">{{ t('common.loading') }}</div>
          <div v-else-if="detailError" class="status-block danger">
            <p>{{ detailError }}</p>
          </div>
          <div v-else-if="selectedProduct" class="detail-layout">
            <div class="detail-copy">
              <p class="eyebrow">{{ t('mall.catalog.productKicker', { productId: selectedProduct.productId }) }}</p>
              <h2>{{ selectedProduct.productName }}</h2>
              <p>{{ selectedProduct.productDescription }}</p>
              <div class="detail-facts">
                <span>{{ selectedProduct.status }}</span>
                <span>{{ t('mall.catalog.skus', { count: selectedProduct.skus.length }) }}</span>
              </div>
            </div>
            <ProductCheckoutPanel
              :key="selectedProduct.productId"
              :product="selectedProduct"
              :session="mallSession.state.session"
              @add-to-cart="handleAddToCart"
              @order-created="handleOrderCreated"
            />
          </div>
          <div v-else class="status-block">{{ t('mall.catalog.selectProduct') }}</div>
        </section>
      </div>
    </section>
  </main>
</template>

<style scoped>
.mall-shell {
  min-height: 100vh;
  background: var(--page-bg);
  color: var(--text-main);
}

.mall-hero,
.catalog-grid,
.order-grid {
  width: min(1220px, calc(100% - 2rem));
  margin: 0 auto;
}

.mall-hero {
  min-height: 15rem;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(18rem, 27rem);
  align-items: end;
  gap: 1.5rem;
  padding: 4.5rem 0 1.25rem;
}

.hero-copy h1 {
  margin: 0;
  font-size: clamp(2.6rem, 8vw, 5.75rem);
  line-height: 0.9;
}

.hero-subtitle {
  max-width: 42rem;
  margin: 0.8rem 0 0;
  color: var(--text-muted);
  font-size: 1.05rem;
}

.eyebrow {
  margin: 0 0 0.4rem;
  color: var(--accent);
  font-size: 0.78rem;
  font-weight: 900;
  letter-spacing: 0;
  text-transform: uppercase;
}

.login-strip,
.session-strip {
  display: grid;
  gap: 0.65rem;
  padding: 1rem;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: var(--bg-panel);
  box-shadow: var(--shadow-soft);
}

.login-strip input {
  min-height: 2.65rem;
  width: 100%;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  padding: 0.65rem 0.8rem;
  background: var(--input-bg);
  color: var(--text-main);
}

.login-strip button,
.session-strip button,
.status-block button,
.pager button,
.text-action {
  min-height: 2.5rem;
  border: 0;
  border-radius: 8px;
  padding: 0 0.9rem;
  background: var(--button-primary-bg);
  color: var(--button-primary-text);
  font-weight: 900;
}

.login-strip button:disabled,
.pager button:disabled,
.text-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.auth-error {
  margin: 0;
  color: var(--danger-text);
  font-weight: 700;
}

.session-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.catalog-band {
  padding: 1rem 0 2rem;
}

.order-band,
.cart-band {
  padding: 0 0 1rem;
}

.cart-panel {
  width: min(1220px, calc(100% - 2rem));
  margin: 0 auto;
  padding: 1rem;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: var(--bg-panel);
  box-shadow: var(--shadow-soft);
}

.catalog-grid,
.order-grid {
  display: grid;
  grid-template-columns: minmax(18rem, 25rem) minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
}

.product-list,
.product-detail,
.order-list,
.order-detail-panel {
  min-width: 0;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: var(--bg-panel);
  box-shadow: var(--shadow-soft);
}

.product-list,
.order-list {
  padding: 1rem;
}

.product-detail,
.order-detail-panel {
  padding: 1.25rem;
}

.list-header,
.pager,
.detail-facts {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}

.refresh-control {
  display: grid;
  justify-items: end;
  gap: 0.3rem;
  min-width: min(100%, 13rem);
}

.refresh-control small {
  max-width: 13rem;
  color: var(--text-muted);
  font-weight: 800;
  overflow-wrap: anywhere;
  text-align: right;
}

.order-tools {
  display: grid;
  gap: 0.75rem;
  margin-top: 1rem;
}

.order-segments {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.segment {
  min-height: 2.4rem;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.35rem 0.65rem;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: var(--card-bg);
  color: var(--text-main);
  font-weight: 900;
}

.segment.active {
  border-color: var(--accent);
  background: var(--active-bg);
}

.segment strong {
  min-width: 1.35rem;
  padding: 0.08rem 0.35rem;
  border-radius: 999px;
  background: var(--chip-bg);
  color: var(--chip-text);
  text-align: center;
}

.order-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 0.45rem;
  align-items: center;
}

.order-search input {
  min-width: 0;
  width: 100%;
  padding: 0.7rem 0.8rem;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: var(--input-bg);
  color: var(--text-main);
}

.order-search-feedback {
  display: grid;
  gap: 0.45rem;
  margin: 0;
  color: var(--text-muted);
  font-size: 0.88rem;
  font-weight: 800;
  overflow-wrap: anywhere;
}

.order-search-feedback p {
  margin: 0;
}

.order-search-actions {
  display: flex;
  gap: 0.45rem;
  flex-wrap: wrap;
}

h2 {
  margin: 0;
  font-size: 1.55rem;
}

.product-cards {
  display: grid;
  gap: 0.65rem;
  margin-top: 1rem;
}

.product-card,
.order-card {
  min-height: 6.25rem;
  display: grid;
  gap: 0.25rem;
  justify-items: start;
  width: 100%;
  padding: 0.9rem;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: var(--card-bg);
  text-align: left;
}

.product-card.active,
.order-card.active {
  border-color: var(--accent);
  background: var(--active-bg);
}

.product-name {
  font-weight: 900;
}

.product-card small,
.order-card small,
.detail-copy p {
  color: var(--text-muted);
}

.order-card-summary {
  color: var(--text-main);
  font-weight: 900;
}

.order-card-updated {
  font-weight: 800;
}

.order-stage-row {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  flex-wrap: wrap;
}

.order-stage-badge {
  max-width: 100%;
  padding: 0.18rem 0.5rem;
  border-radius: 999px;
  background: var(--chip-bg);
  color: var(--chip-text);
  font-weight: 900;
  overflow-wrap: anywhere;
}

.order-card > span:first-child {
  max-width: 100%;
  overflow-wrap: anywhere;
}

.order-cards {
  display: grid;
  gap: 0.65rem;
  margin-top: 1rem;
}

.cart-summary {
  display: grid;
  gap: 0.15rem;
  justify-items: end;
}

.cart-summary span {
  color: var(--text-muted);
  font-weight: 800;
}

.cart-note {
  margin: 0.5rem 0 0;
  color: var(--text-muted);
  font-size: 0.88rem;
  font-weight: 800;
  overflow-wrap: anywhere;
}

.cart-content,
.cart-items {
  display: grid;
  gap: 0.75rem;
}

.cart-content {
  margin-top: 1rem;
}

.cart-item {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) auto auto auto auto;
  gap: 0.75rem;
  align-items: center;
  padding: 0.85rem;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: var(--card-bg);
}

.cart-item div:first-child {
  display: grid;
  gap: 0.2rem;
}

.cart-item small {
  color: var(--text-muted);
}

.cart-stepper {
  display: grid;
  grid-template-columns: 2rem 2.75rem 2rem;
  align-items: center;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  overflow: hidden;
}

.cart-stepper button {
  min-height: 2.25rem;
  border: 0;
  background: var(--surface-subtle);
  font-weight: 900;
}

.cart-stepper button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.cart-stepper output {
  text-align: center;
  font-weight: 900;
}

.cart-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.order-detail {
  display: grid;
  gap: 1rem;
}

.inline-feedback {
  display: grid;
  gap: 0.25rem;
  padding: 0.75rem 0.9rem;
  border-radius: 8px;
  font-weight: 800;
  overflow-wrap: anywhere;
}

.inline-feedback.success {
  background: var(--success-bg);
  color: var(--success-text);
}

.inline-feedback.danger {
  background: var(--danger-bg);
  color: var(--danger-text);
}

.lifecycle-panel {
  display: grid;
  gap: 0.9rem;
  padding: 1rem;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: var(--card-bg);
}

.lifecycle-heading {
  display: grid;
  grid-template-columns: minmax(0, 0.85fr) minmax(12rem, 1fr);
  gap: 1rem;
  align-items: start;
}

.lifecycle-heading h4 {
  margin: 0;
  font-size: 1.15rem;
  overflow-wrap: anywhere;
}

.lifecycle-heading p,
.lifecycle-node p,
.action-boundary {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
}

.lifecycle-list {
  display: grid;
  gap: 0.7rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.lifecycle-node {
  display: grid;
  grid-template-columns: 1rem minmax(0, 1fr);
  gap: 0.7rem;
  align-items: start;
}

.lifecycle-marker {
  width: 0.72rem;
  height: 0.72rem;
  margin-top: 0.28rem;
  border: 2px solid var(--border-soft);
  border-radius: 999px;
  background: var(--bg-panel);
}

.lifecycle-node.complete .lifecycle-marker,
.lifecycle-node.current .lifecycle-marker {
  border-color: var(--accent);
  background: var(--accent);
}

.lifecycle-node.current strong {
  color: var(--accent);
}

.lifecycle-node.pending {
  opacity: 0.72;
}

.lifecycle-node strong,
.lifecycle-node p {
  overflow-wrap: anywhere;
}

.logistics-panel {
  display: grid;
  gap: 0.85rem;
  padding: 1rem;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: var(--surface-subtle);
}

.logistics-heading {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 1rem;
}

.logistics-heading h4 {
  margin: 0;
  font-size: 1.05rem;
}

.logistics-heading span {
  flex: 0 0 auto;
  padding: 0.35rem 0.65rem;
  border-radius: 999px;
  background: var(--chip-bg);
  color: var(--chip-text);
  font-weight: 900;
}

.logistics-panel p {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
}

.logistics-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
  margin: 0;
}

.logistics-grid div {
  min-width: 0;
}

.logistics-grid dt {
  color: var(--text-muted);
  font-size: 0.78rem;
  font-weight: 900;
}

.logistics-grid dd {
  margin: 0.15rem 0 0;
  color: var(--text-main);
  font-weight: 900;
  overflow-wrap: anywhere;
}

.order-headline {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: end;
}

.order-headline h3 {
  margin: 0;
  font-size: clamp(1.6rem, 4vw, 2.75rem);
  line-height: 0.95;
  overflow-wrap: anywhere;
}

.order-headline strong {
  font-size: clamp(1.35rem, 3vw, 2rem);
}

.order-items {
  display: grid;
  gap: 0.65rem;
}

.order-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 0.75rem;
  align-items: center;
  padding: 0.75rem;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: var(--card-bg);
}

.order-item span {
  font-weight: 900;
}

.order-item small {
  color: var(--text-muted);
}

.checkout-actions {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.action-boundary {
  padding: 0 0.15rem;
  overflow-wrap: anywhere;
}

.payment-source {
  margin: -0.2rem 0 0;
  color: var(--text-muted);
  font-weight: 800;
  overflow-wrap: anywhere;
}

.primary-action,
.secondary-action,
.danger-action {
  min-height: 2.75rem;
  flex: 1;
  border-radius: 8px;
  border: 1px solid transparent;
  padding: 0 1rem;
  font-weight: 900;
}

.primary-action {
  background: var(--button-primary-bg);
  color: var(--button-primary-text);
}

.secondary-action {
  background: var(--button-secondary-warm-bg);
  color: var(--button-secondary-warm-text);
  border-color: var(--button-secondary-warm-border);
}

.danger-action {
  background: var(--danger-bg);
  color: var(--danger-text);
  border-color: var(--danger-border);
}

.primary-action:disabled,
.secondary-action:disabled,
.danger-action:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.text-action.subtle {
  background: var(--button-secondary-bg);
  color: var(--button-secondary-text);
}

.status-block {
  display: grid;
  gap: 0.75rem;
  place-items: start;
  min-height: 8rem;
  padding: 1rem;
  border-radius: 8px;
  background: var(--surface-subtle);
  color: var(--text-muted);
  font-weight: 700;
}

.status-block p,
.status-block small {
  margin: 0;
  overflow-wrap: anywhere;
}

.status-block.danger {
  background: var(--danger-bg);
  color: var(--danger-text);
}

.pager {
  margin-top: 1rem;
}

.pager span {
  font-weight: 900;
}

.detail-layout {
  display: grid;
  gap: 1rem;
}

.detail-copy {
  display: grid;
  gap: 0.65rem;
}

.detail-copy h2 {
  font-size: clamp(2rem, 5vw, 3.5rem);
  line-height: 0.95;
}

.detail-copy p {
  max-width: 48rem;
  margin: 0;
}

.detail-facts {
  justify-content: start;
}

.detail-facts span {
  padding: 0.35rem 0.65rem;
  border-radius: 999px;
  background: var(--chip-bg);
  color: var(--chip-text);
  font-weight: 800;
}

@media (max-width: 900px) {
  .mall-hero,
  .catalog-grid,
  .order-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .mall-hero,
  .catalog-grid,
  .order-grid,
  .cart-panel {
    width: min(100% - 1rem, 1220px);
  }

  .session-strip,
  .list-header,
  .pager {
    display: grid;
  }

  .refresh-control {
    justify-items: start;
  }

  .refresh-control small {
    text-align: left;
  }

  .order-headline,
  .lifecycle-heading,
  .logistics-heading,
  .logistics-grid,
  .order-item,
  .order-search,
  .cart-item,
  .checkout-actions {
    display: grid;
  }

  .cart-summary {
    justify-items: start;
  }

  .logistics-grid {
    grid-template-columns: 1fr;
  }

  .order-search {
    grid-template-columns: 1fr;
  }
}
</style>


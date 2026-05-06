<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getProduct, listProducts } from '../../services/productApi'
import { HttpClientError } from '../../services/httpClient'
import { useMallOrderStatus } from '../../composables/useMallOrderStatus'
import { useMallSession } from '../../composables/useMallSession'
import type { ProductDetailResponse, ProductSummaryResponse } from '../../types/api/product'
import { formatDateTime, formatMoney } from '../../utils/format'
import ProductCheckoutPanel from './ProductCheckoutPanel.vue'

const DEFAULT_PAGE_SIZE = 12

const mallSession = useMallSession()
const orderStatus = useMallOrderStatus()
const products = ref<ProductSummaryResponse[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(DEFAULT_PAGE_SIZE)
const isLoadingProducts = ref(false)
const productError = ref('')
const selectedProduct = ref<ProductDetailResponse | null>(null)
const isLoadingDetail = ref(false)
const detailError = ref('')
const loginForm = reactive({
  shopId: resolveDefaultShopId(),
  usernameOrMobile: '',
  password: '',
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const canGoPrev = computed(() => page.value > 1)
const canGoNext = computed(() => page.value < totalPages.value)
const orderTotalPages = computed(() => Math.max(1, Math.ceil(orderStatus.total.value / orderStatus.size.value)))
const canGoPrevOrderPage = computed(() => orderStatus.page.value > 1)
const canGoNextOrderPage = computed(() => orderStatus.page.value < orderTotalPages.value)

onMounted(() => {
  mallSession.bootstrap()
  void fetchProducts()
  if (mallSession.isAuthenticated.value) {
    void orderStatus.loadOrders()
    restoreOrderFromUrl()
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
    productError.value = describeError(caught, 'Unable to load products.')
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
    detailError.value = describeError(caught, 'Unable to load product detail.')
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
    await orderStatus.loadOrders()
    restoreOrderFromUrl()
  }
}

async function handleOrderCreated(orderId: number) {
  replaceOrderUrl(orderId, '')
  await orderStatus.loadOrder(orderId, '')
  await orderStatus.loadOrders()
}

async function handlePaymentCreated(orderId: number, paymentNo: string) {
  replaceOrderUrl(orderId, paymentNo)
  await orderStatus.loadOrder(orderId, paymentNo)
  await orderStatus.loadOrders()
}

async function selectOrder(orderId: number) {
  replaceOrderUrl(orderId, '')
  await orderStatus.loadOrder(orderId, '')
}

async function cancelCurrentOrder() {
  const cancelled = await orderStatus.cancelCurrentOrder()
  if (cancelled) {
    replaceOrderUrl(cancelled.orderId, '')
    await orderStatus.loadOrders()
  }
}

function restoreOrderFromUrl() {
  if (typeof window === 'undefined') {
    return
  }
  const params = new URLSearchParams(window.location.search)
  const orderId = Number(params.get('orderId'))
  const paymentNo = params.get('paymentNo') ?? ''
  if (Number.isFinite(orderId) && orderId > 0) {
    void orderStatus.loadOrder(orderId, paymentNo)
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

function describePriceRange(product: ProductSummaryResponse): string {
  if (product.minPriceCent === product.maxPriceCent) {
    return formatMoney(product.minPriceCent)
  }

  return `${formatMoney(product.minPriceCent)} - ${formatMoney(product.maxPriceCent)}`
}

function describeError(caught: unknown, fallback: string): string {
  if (caught instanceof HttpClientError) {
    const trace = caught.traceId ? ` Trace ID ${caught.traceId}.` : ''
    return `${caught.code}: ${caught.message}${trace}`
  }

  return fallback
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
        <h1>Storefront</h1>
        <p class="hero-subtitle">Browse active catalog items, reserve stock through order creation, then enter mock payment.</p>
      </div>

      <form v-if="!mallSession.isAuthenticated.value" class="login-strip" @submit.prevent="submitLogin">
        <input v-model.number="loginForm.shopId" type="number" min="1" aria-label="Shop ID">
        <input v-model.trim="loginForm.usernameOrMobile" type="text" autocomplete="username" placeholder="Username or mobile" aria-label="Username or mobile">
        <input v-model="loginForm.password" type="password" autocomplete="current-password" placeholder="Password" aria-label="Password">
        <button type="submit" :disabled="mallSession.state.isSubmitting">
          {{ mallSession.state.isSubmitting ? 'Signing in...' : 'Sign in' }}
        </button>
        <p v-if="mallSession.state.error" class="auth-error">
          {{ describeError(mallSession.state.error, 'Unable to sign in.') }}
        </p>
      </form>

      <div v-else class="session-strip">
        <span>User {{ mallSession.state.session?.userId }} · shop {{ mallSession.state.session?.shopId }}</span>
        <button type="button" @click="mallSession.signOut()">Sign out</button>
      </div>
    </section>

    <section v-if="mallSession.isAuthenticated.value" class="order-band">
      <div class="order-grid">
        <aside class="order-list">
          <div class="list-header">
            <div>
              <p class="eyebrow">Orders</p>
              <h2>Recent purchases</h2>
            </div>
            <button type="button" class="text-action" :disabled="orderStatus.isLoadingOrders.value" @click="orderStatus.loadOrders()">
              {{ orderStatus.isLoadingOrders.value ? 'Refreshing...' : 'Refresh' }}
            </button>
          </div>

          <div v-if="orderStatus.isLoadingOrders.value" class="status-block">Loading orders...</div>
          <div v-else-if="orderStatus.orders.value.length === 0" class="status-block">No orders yet.</div>
          <div v-else class="order-cards">
            <button
              v-for="order in orderStatus.orders.value"
              :key="order.orderId"
              type="button"
              :class="orderStatus.order.value?.orderId === order.orderId ? 'order-card active' : 'order-card'"
              @click="selectOrder(order.orderId)"
            >
              <span>{{ order.orderNo }}</span>
              <strong>{{ formatMoney(order.totalAmountCent) }}</strong>
              <small>{{ order.status }} · {{ formatDateTime(order.createdAt) }}</small>
            </button>
          </div>

          <div class="pager">
            <button type="button" :disabled="!canGoPrevOrderPage" @click="orderStatus.loadOrders(orderStatus.page.value - 1)">Prev</button>
            <span>{{ orderStatus.page }} / {{ orderTotalPages }}</span>
            <button type="button" :disabled="!canGoNextOrderPage" @click="orderStatus.loadOrders(orderStatus.page.value + 1)">Next</button>
          </div>
        </aside>

        <section class="order-detail-panel">
          <div class="list-header">
            <div>
              <p class="eyebrow">Status</p>
              <h2>Order result</h2>
            </div>
            <button
              type="button"
              class="text-action"
              :disabled="!orderStatus.order.value || orderStatus.isLoadingOrder.value"
              @click="orderStatus.order.value && orderStatus.loadOrder(orderStatus.order.value.orderId, orderStatus.paymentNo.value)"
            >
              {{ orderStatus.isLoadingOrder.value ? 'Refreshing...' : 'Refresh order' }}
            </button>
          </div>

          <div v-if="orderStatus.errorMessage.value" class="status-block danger">
            {{ orderStatus.errorMessage }}
          </div>
          <div v-else-if="orderStatus.isLoadingOrder.value" class="status-block">Loading order detail...</div>
          <div v-else-if="!orderStatus.order.value" class="status-block">Create or select an order.</div>
          <div v-else class="order-detail">
            <div class="detail-facts">
              <span>{{ orderStatus.order.value.status }}</span>
              <span>Payment {{ orderStatus.paymentStatus.value }}</span>
              <span>{{ formatDateTime(orderStatus.order.value.updatedAt) }}</span>
            </div>

            <div class="order-headline">
              <div>
                <p class="eyebrow">Order {{ orderStatus.order.value.orderId }}</p>
                <h3>{{ orderStatus.order.value.orderNo }}</h3>
              </div>
              <strong>{{ formatMoney(orderStatus.order.value.totalAmountCent) }}</strong>
            </div>

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
                class="secondary-action"
                :disabled="!orderStatus.paymentNo.value || orderStatus.isRefreshingPayment.value"
                @click="orderStatus.refreshPayment()"
              >
                {{ orderStatus.isRefreshingPayment.value ? 'Refreshing...' : 'Refresh payment' }}
              </button>
              <button
                type="button"
                class="danger-action"
                :disabled="!orderStatus.canCancel.value"
                @click="cancelCurrentOrder()"
              >
                {{ orderStatus.isCancelling.value ? 'Cancelling...' : 'Cancel unpaid order' }}
              </button>
            </div>
          </div>
        </section>
      </div>
    </section>

    <section class="catalog-band">
      <div class="catalog-grid">
        <aside class="product-list">
          <div class="list-header">
            <div>
              <p class="eyebrow">Catalog</p>
              <h2>Products</h2>
            </div>
            <span>{{ total }} items</span>
          </div>

          <div v-if="isLoadingProducts" class="status-block">Loading products...</div>
          <div v-else-if="productError" class="status-block danger">
            <p>{{ productError }}</p>
            <button type="button" @click="fetchProducts()">Retry</button>
          </div>
          <div v-else-if="products.length === 0" class="status-block">No active products.</div>
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
              <small>{{ product.status }} · stock shown on detail</small>
            </button>
          </div>

          <div class="pager">
            <button type="button" :disabled="!canGoPrev" @click="fetchProducts(page - 1)">Prev</button>
            <span>{{ page }} / {{ totalPages }}</span>
            <button type="button" :disabled="!canGoNext" @click="fetchProducts(page + 1)">Next</button>
          </div>
        </aside>

        <section class="product-detail">
          <div v-if="isLoadingDetail" class="status-block">Loading detail...</div>
          <div v-else-if="detailError" class="status-block danger">
            <p>{{ detailError }}</p>
          </div>
          <div v-else-if="selectedProduct" class="detail-layout">
            <div class="detail-copy">
              <p class="eyebrow">Product {{ selectedProduct.productId }}</p>
              <h2>{{ selectedProduct.productName }}</h2>
              <p>{{ selectedProduct.productDescription }}</p>
              <div class="detail-facts">
                <span>{{ selectedProduct.status }}</span>
                <span>{{ selectedProduct.skus.length }} SKUs</span>
              </div>
            </div>
            <ProductCheckoutPanel
              :key="selectedProduct.productId"
              :product="selectedProduct"
              :session="mallSession.state.session"
              @order-created="handleOrderCreated"
              @payment-created="handlePaymentCreated"
            />
          </div>
          <div v-else class="status-block">Select a product.</div>
        </section>
      </div>
    </section>
  </main>
</template>

<style scoped>
.mall-shell {
  min-height: 100vh;
  background:
    linear-gradient(135deg, rgba(15, 118, 110, 0.12), transparent 30%),
    linear-gradient(225deg, rgba(234, 88, 12, 0.12), transparent 28%),
    #f8fafc;
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
  padding: 2rem 0 1.25rem;
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
  color: #0f766e;
  font-size: 0.78rem;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.login-strip,
.session-strip {
  display: grid;
  gap: 0.65rem;
  padding: 1rem;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow-soft);
}

.login-strip input {
  min-height: 2.65rem;
  width: 100%;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  padding: 0.65rem 0.8rem;
  background: #ffffff;
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
  background: #0f766e;
  color: #ffffff;
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
  color: #991b1b;
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

.order-band {
  padding: 0 0 1rem;
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
  background: rgba(255, 255, 255, 0.92);
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
  background: #ffffff;
  text-align: left;
}

.product-card.active,
.order-card.active {
  border-color: #0f766e;
  background: #ecfdf5;
}

.product-name {
  font-weight: 900;
}

.product-card small,
.order-card small,
.detail-copy p {
  color: var(--text-muted);
}

.order-cards {
  display: grid;
  gap: 0.65rem;
  margin-top: 1rem;
}

.order-detail {
  display: grid;
  gap: 1rem;
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
  background: #ffffff;
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

.secondary-action,
.danger-action {
  min-height: 2.75rem;
  flex: 1;
  border-radius: 8px;
  border: 1px solid transparent;
  padding: 0 1rem;
  font-weight: 900;
}

.secondary-action {
  background: #fffbeb;
  color: #92400e;
  border-color: #fde68a;
}

.danger-action {
  background: #fef2f2;
  color: #991b1b;
  border-color: #fecaca;
}

.secondary-action:disabled,
.danger-action:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.status-block {
  display: grid;
  gap: 0.75rem;
  place-items: start;
  min-height: 8rem;
  padding: 1rem;
  border-radius: 8px;
  background: #f8fafc;
  color: var(--text-muted);
  font-weight: 700;
}

.status-block.danger {
  background: #fef2f2;
  color: #991b1b;
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
  background: #f1f5f9;
  color: #334155;
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
  .order-grid {
    width: min(100% - 1rem, 1220px);
  }

  .session-strip,
  .list-header,
  .pager {
    display: grid;
  }

  .order-headline,
  .order-item,
  .checkout-actions {
    display: grid;
  }
}
</style>

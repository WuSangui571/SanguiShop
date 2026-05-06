<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getProduct, listProducts } from '../../services/productApi'
import { HttpClientError } from '../../services/httpClient'
import { useMallSession } from '../../composables/useMallSession'
import type { ProductDetailResponse, ProductSummaryResponse } from '../../types/api/product'
import { formatMoney } from '../../utils/format'
import ProductCheckoutPanel from './ProductCheckoutPanel.vue'

const DEFAULT_PAGE_SIZE = 12

const mallSession = useMallSession()
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

onMounted(() => {
  mallSession.bootstrap()
  void fetchProducts()
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
.catalog-grid {
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
.pager button {
  min-height: 2.5rem;
  border: 0;
  border-radius: 8px;
  padding: 0 0.9rem;
  background: #0f766e;
  color: #ffffff;
  font-weight: 900;
}

.login-strip button:disabled,
.pager button:disabled {
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

.catalog-grid {
  display: grid;
  grid-template-columns: minmax(18rem, 25rem) minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
}

.product-list,
.product-detail {
  min-width: 0;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow-soft);
}

.product-list {
  padding: 1rem;
}

.product-detail {
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

.product-card {
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

.product-card.active {
  border-color: #0f766e;
  background: #ecfdf5;
}

.product-name {
  font-weight: 900;
}

.product-card small,
.detail-copy p {
  color: var(--text-muted);
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
  .catalog-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .mall-hero,
  .catalog-grid {
    width: min(100% - 1rem, 1220px);
  }

  .session-strip,
  .list-header,
  .pager {
    display: grid;
  }
}
</style>

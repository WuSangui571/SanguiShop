<script setup lang="ts">
import { computed } from 'vue'
import { useAppPreferences } from '../../composables/useAppPreferences'
import { useMallCheckout } from '../../composables/useMallCheckout'
import type { MallSession } from '../../types/api/auth'
import type { OrderResponse } from '../../types/api/order'
import type { ProductDetailResponse, ProductSkuResponse } from '../../types/api/product'
import { formatMoney } from '../../utils/format'
import type { CartItemInput } from './mallCartModel'

const props = defineProps<{
  product: ProductDetailResponse
  session: MallSession | null
}>()
const { t } = useAppPreferences()

const emit = defineEmits<{
  orderCreated: [order: OrderResponse]
  addToCart: [item: CartItemInput]
}>()

const checkout = useMallCheckout({
  product: computed(() => props.product),
  session: computed(() => props.session),
})

const stockLabel = computed(() => {
  if (!checkout.selectedSku.value) {
    return t('checkout.noSku')
  }
  if (checkout.selectedSku.value.availableStock <= 0) {
    return t('checkout.outOfStock')
  }
  return t('checkout.available', { count: checkout.selectedSku.value.availableStock })
})

function skuTone(sku: ProductSkuResponse): string {
  if (sku.availableStock <= 0) {
    return 'sku-option disabled'
  }
  return sku.skuId === checkout.selectedSkuId.value ? 'sku-option active' : 'sku-option'
}

async function submitOrder() {
  const order = await checkout.submitOrder()
  if (order) {
    emit('orderCreated', order)
  }
}

function addSelectedToCart() {
  const sku = checkout.selectedSku.value
  if (!sku || !props.session || !checkout.canSubmit.value) {
    return
  }

  emit('addToCart', {
    productId: props.product.productId,
    productName: props.product.productName,
    skuId: sku.skuId,
    skuName: sku.skuName,
    priceCent: sku.priceCent,
    availableStock: sku.availableStock,
    quantity: checkout.quantity.value,
  })
}
</script>

<template>
  <section class="checkout-panel">
    <div class="sku-header">
      <div>
        <p class="section-kicker">{{ t('checkout.sku') }}</p>
        <h3>{{ t('checkout.chooseVariant') }}</h3>
      </div>
      <span class="stock-badge">{{ stockLabel }}</span>
    </div>

    <div class="sku-grid" role="list">
      <button
        v-for="sku in product.skus"
        :key="sku.skuId"
        type="button"
        :class="skuTone(sku)"
        :disabled="sku.availableStock <= 0"
        @click="checkout.selectSku(sku.skuId)"
      >
        <span>{{ sku.skuName }}</span>
        <strong>{{ formatMoney(sku.priceCent) }}</strong>
        <small>{{ t('checkout.left', { count: sku.availableStock }) }}</small>
      </button>
    </div>

    <div class="quantity-row">
      <span>{{ t('checkout.quantity') }}</span>
      <div class="stepper">
        <button type="button" :disabled="checkout.quantity.value <= 1" @click="checkout.setQuantity(checkout.quantity.value - 1)">
          -
        </button>
        <output>{{ checkout.quantity }}</output>
        <button
          type="button"
          :disabled="!checkout.selectedSku.value || checkout.quantity.value >= checkout.selectedSku.value.availableStock"
          @click="checkout.setQuantity(checkout.quantity.value + 1)"
        >
          +
        </button>
      </div>
    </div>

    <div class="checkout-total">
      <span>{{ t('checkout.estimatedTotal') }}</span>
      <strong>{{ formatMoney(checkout.orderTotalPreviewCent.value) }}</strong>
    </div>

    <div v-if="!session" class="inline-warning">
      {{ t('checkout.signInRequired') }}
    </div>

    <div v-if="checkout.errorMessage.value" class="inline-error">
      {{ checkout.errorMessage }}
    </div>

    <div v-if="checkout.order.value" class="result-strip">
      <span>{{ t('checkout.orderCreated', { orderNo: checkout.order.value.orderNo }) }}</span>
      <strong>{{ formatMoney(checkout.order.value.totalAmountCent) }}</strong>
    </div>

    <div class="checkout-actions">
      <button
        type="button"
        class="secondary-action"
        :disabled="!session || !checkout.canSubmit.value"
        @click="addSelectedToCart()"
      >
        {{ t('checkout.addToCart') }}
      </button>
      <button
        type="button"
        class="primary-action"
        :disabled="!session || !checkout.canSubmit.value"
        @click="submitOrder()"
      >
        {{ checkout.isSubmittingOrder.value ? t('checkout.creating') : checkout.order.value ? t('checkout.created') : t('checkout.buyNow') }}
      </button>
    </div>
  </section>
</template>

<style scoped>
.checkout-panel {
  display: grid;
  gap: 1rem;
  padding: 1rem;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: var(--card-bg);
}

.sku-header,
.quantity-row,
.checkout-total,
.checkout-actions,
.result-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.section-kicker {
  margin: 0 0 0.2rem;
  color: var(--accent);
  font-size: 0.74rem;
  font-weight: 800;
  text-transform: uppercase;
}

h3 {
  margin: 0;
  font-size: 1.15rem;
}

.stock-badge {
  padding: 0.35rem 0.65rem;
  border-radius: 999px;
  background: var(--info-bg);
  color: var(--info-text);
  font-weight: 800;
  font-size: 0.8rem;
}

.sku-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(8.5rem, 1fr));
  gap: 0.65rem;
}

.sku-option {
  min-height: 5.5rem;
  display: grid;
  gap: 0.25rem;
  justify-items: start;
  padding: 0.8rem;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: var(--surface-subtle);
  color: var(--text-main);
  text-align: left;
}

.sku-option.active {
  border-color: var(--accent);
  background: var(--active-bg);
}

.sku-option.disabled {
  color: var(--text-muted);
  background: var(--disabled-bg);
  cursor: not-allowed;
}

.sku-option strong {
  font-size: 1rem;
}

.sku-option small {
  color: var(--text-muted);
}

.stepper {
  display: grid;
  grid-template-columns: 2.25rem 3rem 2.25rem;
  align-items: center;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  overflow: hidden;
}

.stepper button {
  min-height: 2.25rem;
  border: 0;
  background: var(--surface-subtle);
  font-weight: 900;
}

.stepper button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.stepper output {
  text-align: center;
  font-weight: 800;
}

.checkout-total strong {
  font-size: 1.35rem;
}

.inline-warning,
.inline-error {
  padding: 0.75rem;
  border-radius: 8px;
  font-weight: 700;
}

.inline-warning {
  background: var(--warning-bg);
  color: var(--warning-text);
}

.inline-error {
  background: var(--danger-bg);
  color: var(--danger-text);
}

.result-strip {
  padding: 0.75rem;
  border-radius: 8px;
  background: var(--info-bg);
  color: var(--info-text);
}

.result-strip.paid {
  background: var(--success-bg);
  color: var(--success-text);
}

.primary-action,
.secondary-action {
  min-height: 2.75rem;
  flex: 1;
  border-radius: 8px;
  border: 1px solid transparent;
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

.primary-action:disabled,
.secondary-action:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

@media (max-width: 640px) {
  .sku-header,
  .quantity-row,
  .checkout-total,
  .checkout-actions,
  .result-strip {
    display: grid;
  }
}
</style>

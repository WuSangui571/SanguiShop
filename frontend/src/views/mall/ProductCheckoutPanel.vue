<script setup lang="ts">
import { computed } from 'vue'
import { useMallCheckout } from '../../composables/useMallCheckout'
import type { MallSession } from '../../types/api/auth'
import type { ProductDetailResponse, ProductSkuResponse } from '../../types/api/product'
import { formatMoney } from '../../utils/format'
import type { CartItemInput } from './mallCartModel'

const props = defineProps<{
  product: ProductDetailResponse
  session: MallSession | null
}>()

const emit = defineEmits<{
  orderCreated: [orderId: number]
  addToCart: [item: CartItemInput]
}>()

const checkout = useMallCheckout({
  product: computed(() => props.product),
  session: computed(() => props.session),
})

const stockLabel = computed(() => {
  if (!checkout.selectedSku.value) {
    return 'No SKU selected'
  }
  if (checkout.selectedSku.value.availableStock <= 0) {
    return 'Out of stock'
  }
  return `${checkout.selectedSku.value.availableStock} available`
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
    emit('orderCreated', order.orderId)
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
        <p class="section-kicker">SKU</p>
        <h3>Choose variant</h3>
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
        <small>{{ sku.availableStock }} left</small>
      </button>
    </div>

    <div class="quantity-row">
      <span>Quantity</span>
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
      <span>Estimated item total</span>
      <strong>{{ formatMoney(checkout.orderTotalPreviewCent.value) }}</strong>
    </div>

    <div v-if="!session" class="inline-warning">
      Sign in before creating an order.
    </div>

    <div v-if="checkout.errorMessage.value" class="inline-error">
      {{ checkout.errorMessage }}
    </div>

    <div v-if="checkout.order.value" class="result-strip">
      <span>Order {{ checkout.order.value.orderNo }}</span>
      <strong>{{ formatMoney(checkout.order.value.totalAmountCent) }}</strong>
    </div>

    <div class="checkout-actions">
      <button
        type="button"
        class="secondary-action"
        :disabled="!session || !checkout.canSubmit.value"
        @click="addSelectedToCart()"
      >
        Add to cart
      </button>
      <button
        type="button"
        class="primary-action"
        :disabled="!session || !checkout.canSubmit.value"
        @click="submitOrder()"
      >
        {{ checkout.isSubmittingOrder.value ? 'Creating...' : checkout.order.value ? 'Order created' : 'Buy now' }}
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
  background: #ffffff;
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
  color: #0f766e;
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
  background: #ecfeff;
  color: #155e75;
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
  background: #f8fafc;
  color: var(--text-main);
  text-align: left;
}

.sku-option.active {
  border-color: #0f766e;
  background: #ecfdf5;
}

.sku-option.disabled {
  color: var(--text-muted);
  background: #f1f5f9;
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
  background: #f8fafc;
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
  background: #fff7ed;
  color: #9a3412;
}

.inline-error {
  background: #fef2f2;
  color: #991b1b;
}

.result-strip {
  padding: 0.75rem;
  border-radius: 8px;
  background: #eff6ff;
  color: #1d4ed8;
}

.result-strip.paid {
  background: #ecfdf5;
  color: #166534;
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
  background: #0f766e;
  color: #ffffff;
}

.secondary-action {
  background: #fffbeb;
  color: #92400e;
  border-color: #fde68a;
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

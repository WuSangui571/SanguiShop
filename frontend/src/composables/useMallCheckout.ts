import { computed, ref, unref, type Ref } from 'vue'
import { createOrder as createOrderApi } from '../services/orderApi'
import { createPayment as createPaymentApi } from '../services/paymentApi'
import type { MallSession } from '../types/api/auth'
import type { CreateOrderRequest, OrderResponse } from '../types/api/order'
import type { CreatePaymentRequest, PaymentResponse } from '../types/api/payment'
import type { ProductDetailResponse } from '../types/api/product'
import {
  buildCreateOrderRequest,
  buildCreatePaymentRequest,
  canSubmitOrder,
  createOrderRequestId,
  createPaymentNo,
  describeMallApiError,
  selectInitialSku,
} from '../views/mall/mallCheckoutModel'

type MaybeRef<T> = T | Ref<T>

interface UseMallCheckoutOptions {
  product: MaybeRef<ProductDetailResponse>
  session: MaybeRef<MallSession | null>
  createOrder?: (payload: CreateOrderRequest) => Promise<OrderResponse>
  createPayment?: (payload: CreatePaymentRequest) => Promise<PaymentResponse>
  createRequestId?: () => string
  createPaymentNo?: () => string
}

export function useMallCheckout(options: UseMallCheckoutOptions) {
  const initialSku = selectInitialSku(unref(options.product))
  const selectedSkuId = ref<number | null>(initialSku?.skuId ?? null)
  const quantity = ref(1)
  const orderRequestId = ref((options.createRequestId ?? createOrderRequestId)())
  const paymentNo = ref((options.createPaymentNo ?? createPaymentNo)())
  const order = ref<OrderResponse | null>(null)
  const payment = ref<PaymentResponse | null>(null)
  const errorMessage = ref('')
  const isSubmittingOrder = ref(false)
  const isSubmittingPayment = ref(false)

  const selectedSku = computed(() => {
    const product = unref(options.product)
    return product.skus.find((sku) => sku.skuId === selectedSkuId.value) ?? null
  })
  const orderTotalPreviewCent = computed(() => (selectedSku.value?.priceCent ?? 0) * quantity.value)
  const canSubmit = computed(() => canSubmitOrder({
    selectedSku: selectedSku.value,
    quantity: quantity.value,
    isSubmitting: isSubmittingOrder.value,
  }))
  const canPay = computed(() => Boolean(order.value) && !isSubmittingPayment.value && !payment.value)

  function selectSku(skuId: number) {
    selectedSkuId.value = skuId
    order.value = null
    payment.value = null
    orderRequestId.value = (options.createRequestId ?? createOrderRequestId)()
    paymentNo.value = (options.createPaymentNo ?? createPaymentNo)()
  }

  function setQuantity(nextQuantity: number) {
    const stock = selectedSku.value?.availableStock ?? 1
    quantity.value = Math.min(Math.max(1, nextQuantity), Math.max(1, stock))
  }

  async function submitOrder(): Promise<OrderResponse | null> {
    const session = unref(options.session)
    if (!session || !selectedSku.value || !canSubmit.value) {
      return null
    }

    isSubmittingOrder.value = true
    errorMessage.value = ''

    try {
      const createOrder = options.createOrder ?? defaultCreateOrder
      const response = await createOrder(buildCreateOrderRequest({
        session,
        requestId: orderRequestId.value,
        skuId: selectedSku.value.skuId,
        quantity: quantity.value,
      }))
      order.value = response
      return response
    } catch (caught) {
      errorMessage.value = describeMallApiError(caught)
      return null
    } finally {
      isSubmittingOrder.value = false
    }
  }

  async function submitPayment(): Promise<PaymentResponse | null> {
    const session = unref(options.session)
    if (!session || !order.value || isSubmittingPayment.value) {
      return null
    }

    isSubmittingPayment.value = true
    errorMessage.value = ''

    try {
      const createPayment = options.createPayment ?? defaultCreatePayment
      const response = await createPayment(buildCreatePaymentRequest({
        session,
        orderId: order.value.orderId,
        paymentNo: paymentNo.value,
      }))
      payment.value = response
      return response
    } catch (caught) {
      errorMessage.value = describeMallApiError(caught)
      return null
    } finally {
      isSubmittingPayment.value = false
    }
  }

  return {
    selectedSkuId,
    selectedSku,
    quantity,
    orderRequestId,
    paymentNo,
    order,
    payment,
    orderTotalPreviewCent,
    canSubmit,
    canPay,
    errorMessage,
    isSubmittingOrder,
    isSubmittingPayment,
    selectSku,
    setQuantity,
    submitOrder,
    submitPayment,
  }
}

async function defaultCreateOrder(payload: CreateOrderRequest): Promise<OrderResponse> {
  const result = await createOrderApi(payload)
  return result.data
}

async function defaultCreatePayment(payload: CreatePaymentRequest): Promise<PaymentResponse> {
  const result = await createPaymentApi(payload)
  return result.data
}

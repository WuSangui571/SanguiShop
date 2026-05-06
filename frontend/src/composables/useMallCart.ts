import { computed, ref, unref, watch, type Ref } from 'vue'
import { createOrder as createOrderApi } from '../services/orderApi'
import type { MallSession } from '../types/api/auth'
import type { CreateOrderRequest, OrderResponse } from '../types/api/order'
import {
  addMallCartItem,
  buildCartCreateOrderRequest,
  calculateMallCartItemCount,
  calculateMallCartTotalCent,
  canCheckoutMallCart,
  clearSubmittedMallCartItems,
  createMallCartStorageKey,
  deserializeMallCart,
  removeMallCartItem,
  serializeMallCart,
  setMallCartItemQuantity,
  type CartItemInput,
} from '../views/mall/mallCartModel'
import {
  createOrderRequestId,
  describeMallApiError,
} from '../views/mall/mallCheckoutModel'

type MaybeRef<T> = T | Ref<T>

interface MallCartStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}

interface UseMallCartOptions {
  session: MaybeRef<MallSession | null>
  createOrder?: (payload: CreateOrderRequest) => Promise<OrderResponse>
  createRequestId?: () => string
  storage?: MallCartStorage
}

export function useMallCart(options: UseMallCartOptions) {
  const items = ref<ReturnType<typeof deserializeMallCart>>([])
  const storageKey = ref('')
  const orderRequestId = ref((options.createRequestId ?? createOrderRequestId)())
  const errorMessage = ref('')
  const isCheckingOut = ref(false)

  const itemCount = computed(() => calculateMallCartItemCount(items.value))
  const totalPreviewCent = computed(() => calculateMallCartTotalCent(items.value))
  const canCheckout = computed(() => canCheckoutMallCart(items.value, isCheckingOut.value))

  watch(
    () => resolveSessionKey(unref(options.session)),
    () => {
      loadCartForSession()
    },
    { immediate: true },
  )

  function addItem(input: CartItemInput) {
    const session = unref(options.session)
    if (!session) {
      errorMessage.value = 'AUTH_TOKEN_MISSING: Sign in before adding items to cart.'
      return
    }

    items.value = addMallCartItem(items.value, session, input)
    errorMessage.value = ''
    resetCheckoutRequest()
    persist()
  }

  function setQuantity(skuId: number, quantity: number) {
    items.value = setMallCartItemQuantity(items.value, skuId, quantity)
    errorMessage.value = ''
    resetCheckoutRequest()
    persist()
  }

  function removeItem(skuId: number) {
    items.value = removeMallCartItem(items.value, skuId)
    errorMessage.value = ''
    resetCheckoutRequest()
    persist()
  }

  function clearCart() {
    items.value = []
    errorMessage.value = ''
    resetCheckoutRequest()
    persist()
  }

  async function submitCheckout(): Promise<OrderResponse | null> {
    const session = unref(options.session)
    if (!session || !canCheckout.value) {
      return null
    }

    isCheckingOut.value = true
    errorMessage.value = ''

    try {
      const createOrder = options.createOrder ?? defaultCreateOrder
      const response = await createOrder(buildCartCreateOrderRequest({
        session,
        requestId: orderRequestId.value,
        items: items.value,
      }))
      items.value = clearSubmittedMallCartItems(items.value, response)
      resetCheckoutRequest()
      persist()
      return response
    } catch (caught) {
      errorMessage.value = describeMallApiError(caught)
      return null
    } finally {
      isCheckingOut.value = false
    }
  }

  function loadCartForSession() {
    const session = unref(options.session)
    if (!session) {
      storageKey.value = ''
      items.value = []
      errorMessage.value = ''
      return
    }

    const key = createMallCartStorageKey(session)
    storageKey.value = key
    items.value = deserializeMallCart(resolveStorage()?.getItem(key) ?? null, session)
    errorMessage.value = ''
    resetCheckoutRequest()
  }

  function persist() {
    const session = unref(options.session)
    const storage = resolveStorage()
    if (!session || !storageKey.value || !storage) {
      return
    }

    if (items.value.length === 0) {
      storage.removeItem(storageKey.value)
      return
    }

    storage.setItem(storageKey.value, serializeMallCart(session, items.value))
  }

  function resetCheckoutRequest() {
    orderRequestId.value = (options.createRequestId ?? createOrderRequestId)()
  }

  function resolveStorage(): MallCartStorage | null {
    if (options.storage) {
      return options.storage
    }
    if (typeof window === 'undefined') {
      return null
    }
    return window.localStorage
  }

  return {
    items,
    storageKey,
    orderRequestId,
    itemCount,
    totalPreviewCent,
    canCheckout,
    errorMessage,
    isCheckingOut,
    addItem,
    setQuantity,
    removeItem,
    clearCart,
    submitCheckout,
    loadCartForSession,
  }
}

function resolveSessionKey(session: MallSession | null): string {
  if (!session) {
    return ''
  }

  return `${session.shopId}:${String(session.userId)}`
}

async function defaultCreateOrder(payload: CreateOrderRequest): Promise<OrderResponse> {
  const result = await createOrderApi(payload)
  return result.data
}

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
  classifyMallCartCheckoutFailure,
  clearSubmittedMallCartItems,
  createSignedOutMallCartRestoreResult,
  createUnavailableMallCartRestoreResult,
  createMallCartStorageKey,
  deserializeMallCart,
  removeMallCartItem,
  restoreMallCartDraft,
  serializeMallCart,
  setMallCartItemQuantity,
  type CartItemInput,
  type MallCartCheckoutFailure,
  type MallCartRestoreResult,
} from '../views/mall/mallCartModel'
import {
  createOrderRequestId,
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
  const checkoutFailure = ref<MallCartCheckoutFailure | null>(null)
  const restoreResult = ref<MallCartRestoreResult>(createSignedOutMallCartRestoreResult())
  const isCheckingOut = ref(false)

  const itemCount = computed(() => calculateMallCartItemCount(items.value))
  const totalPreviewCent = computed(() => calculateMallCartTotalCent(items.value))
  const canCheckout = computed(() => canCheckoutMallCart(items.value, isCheckingOut.value))
  const sessionScope = computed(() => {
    const session = unref(options.session)
    if (!session) {
      return null
    }

    return {
      shopId: session.shopId,
      userId: String(session.userId),
    }
  })

  watch(
    () => resolveSessionKey(unref(options.session)),
    () => {
      loadCartForSession()
    },
    { immediate: true },
  )

  function addItem(input: CartItemInput) {
    const session = unref(options.session)
    if (isCheckingOut.value) {
      return
    }
    if (!session) {
      errorMessage.value = 'AUTH_TOKEN_MISSING: Sign in before adding items to cart.'
      return
    }

    items.value = addMallCartItem(items.value, session, input)
    clearCheckoutFailure()
    resetCheckoutRequest()
    persist()
  }

  function setQuantity(skuId: number, quantity: number) {
    if (isCheckingOut.value) {
      return
    }
    items.value = setMallCartItemQuantity(items.value, skuId, quantity)
    clearCheckoutFailure()
    resetCheckoutRequest()
    persist()
  }

  function removeItem(skuId: number) {
    if (isCheckingOut.value) {
      return
    }
    items.value = removeMallCartItem(items.value, skuId)
    clearCheckoutFailure()
    resetCheckoutRequest()
    persist()
  }

  function clearCart() {
    if (isCheckingOut.value) {
      return
    }
    items.value = []
    clearCheckoutFailure()
    resetCheckoutRequest()
    persist()
  }

  async function submitCheckout(): Promise<OrderResponse | null> {
    const session = unref(options.session)
    if (!session || !canCheckout.value) {
      return null
    }

    isCheckingOut.value = true
    clearCheckoutFailure()
    const checkoutItems = [...items.value]

    try {
      const createOrder = options.createOrder ?? defaultCreateOrder
      const response = await createOrder(buildCartCreateOrderRequest({
        session,
        requestId: orderRequestId.value,
        items: checkoutItems,
      }))
      items.value = clearSubmittedMallCartItems(items.value, response)
      resetCheckoutRequest()
      persist()
      return response
    } catch (caught) {
      const failure = classifyMallCartCheckoutFailure(caught)
      checkoutFailure.value = failure
      errorMessage.value = failure.detail
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
      checkoutFailure.value = null
      restoreResult.value = createSignedOutMallCartRestoreResult()
      return
    }

    const key = createMallCartStorageKey(session)
    storageKey.value = key
    const storage = resolveStorage()
    if (!storage) {
      const unavailable = createUnavailableMallCartRestoreResult()
      restoreResult.value = unavailable
      items.value = unavailable.items
      clearCheckoutFailure()
      resetCheckoutRequest()
      return
    }

    let serialized: string | null
    try {
      serialized = storage.getItem(key)
    } catch {
      const unavailable = createUnavailableMallCartRestoreResult()
      restoreResult.value = unavailable
      items.value = unavailable.items
      clearCheckoutFailure()
      resetCheckoutRequest()
      return
    }

    const restored = restoreMallCartDraft(serialized, session)
    restoreResult.value = restored
    items.value = restored.items
    errorMessage.value = ''
    checkoutFailure.value = null
    resetCheckoutRequest()
  }

  function persist() {
    const session = unref(options.session)
    const storage = resolveStorage()
    if (!session || !storageKey.value || !storage) {
      return
    }

    try {
      if (items.value.length === 0) {
        storage.removeItem(storageKey.value)
        return
      }

      storage.setItem(storageKey.value, serializeMallCart(session, items.value))
    } catch {
      restoreResult.value = createUnavailableMallCartRestoreResult()
    }
  }

  function resetCheckoutRequest() {
    orderRequestId.value = (options.createRequestId ?? createOrderRequestId)()
  }

  function clearCheckoutFailure() {
    errorMessage.value = ''
    checkoutFailure.value = null
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
    sessionScope,
    restoreResult,
    itemCount,
    totalPreviewCent,
    canCheckout,
    errorMessage,
    checkoutFailure,
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

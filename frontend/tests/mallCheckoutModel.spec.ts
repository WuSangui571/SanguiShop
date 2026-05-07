import { afterEach, describe, expect, it, vi } from 'vitest'
import { HttpClientError } from '../src/services/httpClient'
import {
  buildCreateOrderRequest,
  buildCreatePaymentRequest,
  canSubmitOrder,
  describeMallApiError,
  selectInitialSku,
} from '../src/views/mall/mallCheckoutModel'
import { useMallCheckout } from '../src/composables/useMallCheckout'
import { useMallCart } from '../src/composables/useMallCart'
import { useMallOrderStatus } from '../src/composables/useMallOrderStatus'
import type { ProductDetailResponse } from '../src/types/api/product'
import type { MallSession } from '../src/types/api/auth'
import type { OrderResponse } from '../src/types/api/order'
import type { PaymentResponse } from '../src/types/api/payment'

const product: ProductDetailResponse = {
  productId: 301,
  productName: 'Daily trainer',
  productDescription: 'Stable shoe for everyday walking.',
  status: 'active',
  skus: [
    {
      skuId: 401,
      skuCode: 'shoe-42',
      skuName: '42',
      priceCent: 59900,
      availableStock: 2,
      reservedStock: 0,
    },
    {
      skuId: 402,
      skuCode: 'shoe-43',
      skuName: '43',
      priceCent: 59900,
      availableStock: 0,
      reservedStock: 1,
    },
  ],
}

const session: MallSession = {
  userId: 10001,
  shopId: 1,
  accessToken: 'jwt-user',
  tokenType: 'Bearer',
  expiresAt: '2026-05-06T10:00:00.000Z',
  roles: ['USER'],
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('mall checkout model', () => {
  it('selects the first available SKU and builds typed order/payment requests', () => {
    const selectedSku = selectInitialSku(product)

    expect(selectedSku?.skuId).toBe(401)
    expect(canSubmitOrder({ selectedSku, quantity: 2, isSubmitting: false })).toBe(true)
    expect(buildCreateOrderRequest({
      session,
      requestId: 'req-001',
      skuId: selectedSku?.skuId ?? 0,
      quantity: 2,
    })).toEqual({
      shopId: 1,
      userId: '10001',
      requestId: 'req-001',
      items: [{ skuId: 401, quantity: 2 }],
    })
    expect(buildCreatePaymentRequest({
      session,
      orderId: 501,
      paymentNo: 'PAY-001',
    })).toEqual({
      shopId: 1,
      userId: '10001',
      orderId: 501,
      paymentNo: 'PAY-001',
      channel: 'mock',
    })
  })

  it('blocks out-of-stock and duplicate pending order submits', async () => {
    const outOfStockSku = product.skus[1]
    expect(canSubmitOrder({ selectedSku: outOfStockSku, quantity: 1, isSubmitting: false })).toBe(false)

    const deferredOrder = createDeferred<OrderResponse>()
    const createOrder = vi.fn(() => deferredOrder.promise)
    const createPayment = vi.fn(async () => createPaymentResponse())
    const checkout = useMallCheckout({
      product,
      session,
      createOrder,
      createPayment,
      createRequestId: () => 'req-duplicate',
      createPaymentNo: () => 'PAY-duplicate',
    })

    checkout.quantity.value = 1
    const firstSubmit = checkout.submitOrder()
    const secondSubmit = checkout.submitOrder()

    expect(createOrder).toHaveBeenCalledOnce()
    await expect(secondSubmit).resolves.toBeNull()

    deferredOrder.resolve(createOrderResponse())
    await firstSubmit
  })

  it('describes backend errors with traceId for customer support handoff', () => {
    const error = new HttpClientError('Stock is not enough.', {
      code: 'ORDER_STOCK_NOT_ENOUGH',
      status: 409,
      traceId: 'trace-stock-001',
    })

    expect(describeMallApiError(error)).toBe('ORDER_STOCK_NOT_ENOUGH: Stock is not enough. Trace ID trace-stock-001.')
  })

  it('loads order detail and refreshes known payment status', async () => {
    const getOrder = vi.fn(async () => createOrderResponse())
    const getPayment = vi.fn(async () => createPaymentResponse())
    const orderStatus = useMallOrderStatus({
      getOrder,
      getPayment,
    })

    await orderStatus.loadOrder(501, 'PAY-duplicate')

    expect(getOrder).toHaveBeenCalledWith(501)
    expect(getPayment).toHaveBeenCalledWith('PAY-duplicate')
    expect(orderStatus.order.value?.orderNo).toBe('ORD-501')
    expect(orderStatus.paymentStatus.value).toBe('paid')
  })

  it('merges refreshed order detail into recent purchases', async () => {
    const listOrders = vi.fn(async () => ({
      page: 1,
      size: 5,
      total: 1,
      items: [createOrderResponse({
        status: 'paid',
        fulfillmentStatus: 'unshipped',
      })],
    }))
    const getOrder = vi.fn(async () => createOrderResponse({
      status: 'paid',
      fulfillmentStatus: 'shipped',
      carrier: 'SF Express',
      trackingNo: 'SF999',
      updatedAt: '2026-05-07T12:00:00+08:00',
    }))
    const orderStatus = useMallOrderStatus({
      getOrder,
      listOrders,
    })

    await orderStatus.loadOrders()
    await orderStatus.loadOrder(501, '')

    expect(orderStatus.orders.value[0]).toMatchObject({
      orderId: 501,
      fulfillmentStatus: 'shipped',
      carrier: 'SF Express',
      trackingNo: 'SF999',
    })
  })

  it('keeps current order detail visible when refresh fails', async () => {
    const getOrder = vi.fn(async () => {
      throw new HttpClientError('Order refresh failed.', {
        code: 'ORDER_REFRESH_FAILED',
        status: 503,
        traceId: 'trace-refresh-503',
      })
    })
    const orderStatus = useMallOrderStatus({ getOrder })
    orderStatus.acceptCreatedOrder(createOrderResponse({
      status: 'paid',
      fulfillmentStatus: 'unshipped',
    }))

    await orderStatus.refreshCurrentOrder()

    expect(orderStatus.order.value).toMatchObject({
      orderId: 501,
      status: 'paid',
      fulfillmentStatus: 'unshipped',
    })
    expect(orderStatus.orderRefreshResult.value).toBe('error')
    expect(orderStatus.errorMessage.value).toBe(
      'ORDER_REFRESH_FAILED: Order refresh failed. Trace ID trace-refresh-503.',
    )
  })

  it('keeps order status when payment refresh fails', async () => {
    const getPayment = vi.fn(async () => {
      throw new HttpClientError('Payment service unavailable.', {
        code: 'PAYMENT_REFRESH_FAILED',
        status: 503,
        traceId: 'trace-payment-503',
      })
    })
    const orderStatus = useMallOrderStatus({ getPayment })
    orderStatus.acceptCreatedOrder(createOrderResponse({
      status: 'paid',
      fulfillmentStatus: 'unshipped',
    }))
    orderStatus.paymentNo.value = 'PAY-501'

    await orderStatus.refreshPayment()

    expect(orderStatus.order.value?.status).toBe('paid')
    expect(orderStatus.paymentStatus.value).toBe('paid')
    expect(orderStatus.errorMessage.value).toBe(
      'PAYMENT_REFRESH_FAILED: Payment service unavailable. Trace ID trace-payment-503.',
    )
  })

  it('keeps no payment response for paid orders without payment numbers', async () => {
    const orderStatus = useMallOrderStatus()
    orderStatus.acceptCreatedOrder(createOrderResponse({
      status: 'paid',
      fulfillmentStatus: 'unshipped',
    }))

    await orderStatus.refreshPayment()

    expect(orderStatus.paymentNo.value).toBe('')
    expect(orderStatus.payment.value).toBeNull()
    expect(orderStatus.paymentStatus.value).toBe('paid')
  })

  it('blocks duplicate pending cancellation attempts', async () => {
    const deferredCancel = createDeferred<OrderResponse>()
    const cancelOrder = vi.fn(() => deferredCancel.promise)
    const orderStatus = useMallOrderStatus({
      cancelOrder,
    })
    orderStatus.acceptCreatedOrder(createOrderResponse())

    const firstCancel = orderStatus.cancelCurrentOrder()
    const secondCancel = orderStatus.cancelCurrentOrder()

    expect(cancelOrder).toHaveBeenCalledOnce()
    await expect(secondCancel).resolves.toBeNull()

    deferredCancel.resolve({
      ...createOrderResponse(),
      status: 'cancelled',
    })
    await firstCancel

    expect(orderStatus.order.value?.status).toBe('cancelled')
    expect(orderStatus.canCancel.value).toBe(false)
  })

  it('shows traceId when order detail loading fails', async () => {
    const getOrder = vi.fn(async () => {
      throw new HttpClientError('Order missing.', {
        code: 'ORDER_NOT_FOUND',
        status: 404,
        traceId: 'trace-order-404',
      })
    })
    const orderStatus = useMallOrderStatus({ getOrder })

    await orderStatus.loadOrder(404)

    expect(orderStatus.errorMessage.value).toBe('ORDER_NOT_FOUND: Order missing. Trace ID trace-order-404.')
    expect(orderStatus.order.value).toBeNull()
  })

  it('persists cart drafts by shop and user and builds a multi-item checkout payload', async () => {
    const storage = createMemoryStorage()
    const createOrder = vi.fn(async () => createMultiItemOrderResponse())
    const cart = useMallCart({
      session,
      storage,
      createOrder,
      createRequestId: () => 'req-cart-001',
    })

    cart.addItem({
      productId: 301,
      productName: 'Daily trainer',
      skuId: 401,
      skuName: '42',
      priceCent: 59900,
      availableStock: 5,
      quantity: 1,
    })
    cart.addItem({
      productId: 301,
      productName: 'Daily trainer',
      skuId: 402,
      skuName: '43',
      priceCent: 64500,
      availableStock: 4,
      quantity: 2,
    })
    cart.setQuantity(401, 3)

    expect(cart.itemCount.value).toBe(5)
    expect(cart.totalPreviewCent.value).toBe(308700)

    const restored = useMallCart({
      session,
      storage,
      createOrder,
      createRequestId: () => 'req-cart-001',
    })

    expect(restored.items.value.map((item) => [item.skuId, item.quantity])).toEqual([[401, 3], [402, 2]])

    await restored.submitCheckout()

    expect(createOrder).toHaveBeenCalledWith({
      shopId: 1,
      userId: '10001',
      requestId: 'req-cart-001',
      items: [
        { skuId: 401, quantity: 3 },
        { skuId: 402, quantity: 2 },
      ],
    })
    expect(restored.items.value).toEqual([])

    const otherUserCart = useMallCart({
      session: { ...session, userId: 10002 },
      storage,
      createOrder,
    })
    expect(otherUserCart.items.value).toEqual([])
  })

  it('guards duplicate cart checkout and keeps traceId errors visible', async () => {
    const storage = createMemoryStorage()
    const deferredOrder = createDeferred<OrderResponse>()
    const createOrder = vi.fn(() => deferredOrder.promise)
    const cart = useMallCart({
      session,
      storage,
      createOrder,
      createRequestId: () => 'req-cart-duplicate',
    })
    cart.addItem({
      productId: 301,
      productName: 'Daily trainer',
      skuId: 401,
      skuName: '42',
      priceCent: 59900,
      availableStock: 2,
      quantity: 1,
    })

    const firstCheckout = cart.submitCheckout()
    const secondCheckout = cart.submitCheckout()

    expect(createOrder).toHaveBeenCalledOnce()
    await expect(secondCheckout).resolves.toBeNull()

    deferredOrder.resolve(createOrderResponse())
    await firstCheckout

    const failedCart = useMallCart({
      session,
      storage,
      createOrder: vi.fn(async () => {
        throw new HttpClientError('Stock is not enough.', {
          code: 'ORDER_STOCK_NOT_ENOUGH',
          status: 409,
          traceId: 'trace-cart-stock',
        })
      }),
    })
    failedCart.addItem({
      productId: 301,
      productName: 'Daily trainer',
      skuId: 401,
      skuName: '42',
      priceCent: 59900,
      availableStock: 2,
      quantity: 1,
    })

    await failedCart.submitCheckout()

    expect(failedCart.errorMessage.value).toBe('ORDER_STOCK_NOT_ENOUGH: Stock is not enough. Trace ID trace-cart-stock.')
    expect(failedCart.items.value).toHaveLength(1)
  })

  it('creates mock payment from the shared order status panel', async () => {
    const createPayment = vi.fn(async () => createPaymentResponse())
    const orderStatus = useMallOrderStatus({
      createPayment,
      createPaymentNo: () => 'PAY-shared-result',
    })
    orderStatus.acceptCreatedOrder(createOrderResponse())

    await orderStatus.submitPayment(session)

    expect(createPayment).toHaveBeenCalledWith({
      shopId: 1,
      userId: '10001',
      orderId: 501,
      paymentNo: 'PAY-shared-result',
      channel: 'mock',
    })
    expect(orderStatus.paymentStatus.value).toBe('paid')
    expect(orderStatus.order.value?.status).toBe('paid')
  })
})

function createOrderResponse(patch: Partial<OrderResponse> = {}): OrderResponse {
  return {
    orderId: 501,
    orderNo: 'ORD-501',
    shopId: 1,
    userId: '10001',
    requestId: 'req-duplicate',
    status: 'created',
    totalAmountCent: 59900,
    items: [
      {
        productId: 301,
        skuId: 401,
        skuName: '42',
        priceCent: 59900,
        quantity: 1,
        lineAmountCent: 59900,
      },
    ],
    ...patch,
  }
}

function createMultiItemOrderResponse(): OrderResponse {
  return {
    ...createOrderResponse(),
    requestId: 'req-cart-001',
    totalAmountCent: 308700,
    items: [
      {
        productId: 301,
        skuId: 401,
        skuName: '42',
        priceCent: 59900,
        quantity: 3,
        lineAmountCent: 179700,
      },
      {
        productId: 301,
        skuId: 402,
        skuName: '43',
        priceCent: 64500,
        quantity: 2,
        lineAmountCent: 129000,
      },
    ],
  }
}

function createDeferred<T>() {
  let resolve: (value: T) => void = () => {
    throw new Error('Deferred promise was resolved before initialization.')
  }
  const promise = new Promise<T>((nextResolve) => {
    resolve = nextResolve
  })
  return { promise, resolve }
}

function createPaymentResponse(): PaymentResponse {
  return {
    paymentId: 701,
    paymentNo: 'PAY-duplicate',
    orderId: 501,
    orderNo: 'ORD-501',
    shopId: 1,
    userId: '10001',
    channel: 'mock',
    status: 'paid',
    amountCent: 59900,
  }
}

function createMemoryStorage() {
  const data = new Map<string, string>()
  return {
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => {
      data.set(key, value)
    },
    removeItem: (key: string) => {
      data.delete(key)
    },
  }
}

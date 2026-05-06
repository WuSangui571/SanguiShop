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
    expect(secondSubmit).resolves.toBeNull()

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
})

function createOrderResponse(): OrderResponse {
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

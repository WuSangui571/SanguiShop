import { test, expect, type Page, type Route } from '@playwright/test'
import { createServer, type ViteDevServer } from 'vite'
import {
  apiEnvelope,
  apiErrorEnvelope,
  createCompletedFromShippedOrder,
  createCreatedOrder,
  createLoginResponse,
  createShippedOrder,
  createCompletedOrder,
  createCancelledOrder,
  createUnknownStatusOrder,
  createPaidUnshippedOrder,
  createOrderPage,
  createPaymentResponse,
  createProductList,
  createProductDetail,
  createEmptyReviews,
  MALL_SESSION_KEY,
  MOCK_SESSION,
} from './fixtures/mallOrderStatusSmoke'
import type { OrderResponse } from '../src/types/api/order'

const LOCALE_STORAGE_KEY = 'sangui.app.locale.v1'

let viteServer: ViteDevServer | null = null
let pendingPaymentRoute: Route | null = null
let paymentRequestCount = 0
let orderRouteRequestCount = 0
let protectedApiAuthHeaders: string[] = []
let mockOrders: OrderResponse[] = []
let mockOrderById: Record<number, OrderResponse> = {}
let mockPaymentError: { code: string; message: string; traceId: string } | null = null
let deferPaymentResponse = false
let receiptConfirmationRequestCount = 0
let receiptPayloads: { body: unknown; headers: Record<string, string>; path: string }[] = []
let deferReceiptConfirmationResponse = false
let mockReceiptConfirmationError: { status: number; code: string; message: string; traceId: string } | null = null
let pendingReceiptRoute: Route | null = null

function resetMockState() {
  pendingPaymentRoute = null
  paymentRequestCount = 0
  orderRouteRequestCount = 0
  protectedApiAuthHeaders = []
  mockOrders = []
  mockOrderById = {}
  mockPaymentError = null
  deferPaymentResponse = false
  receiptConfirmationRequestCount = 0
  receiptPayloads = []
  deferReceiptConfirmationResponse = false
  mockReceiptConfirmationError = null
  pendingReceiptRoute = null
}

function extractPath(rawUrl: string): string {
  const questionIndex = rawUrl.indexOf('?')
  const baseUrl = questionIndex >= 0 ? rawUrl.substring(0, questionIndex) : rawUrl
  if (baseUrl.startsWith('http://') || baseUrl.startsWith('https://')) {
    const afterProtocol = baseUrl.substring(baseUrl.indexOf('://') + 3)
    const pathStart = afterProtocol.indexOf('/')
    return pathStart >= 0 ? afterProtocol.substring(pathStart) : '/'
  }
  return baseUrl
}

function recordProtectedAuthHeader(route: Route) {
  protectedApiAuthHeaders.push(route.request().headers().authorization ?? '')
}

async function setupDefaultApiRoutes(page: Page) {
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const path = extractPath(request.url())
    const method = request.method()

    if (method === 'GET' && /^\/api\/products\/\d+\/reviews$/.test(path)) {
      const parts = path.split('/')
      const productId = Number(parts[3])
      await route.fulfill({ json: apiEnvelope(createEmptyReviews(productId)) })
      return
    }
    if (method === 'GET' && /^\/api\/products\/\d+$/.test(path)) {
      await route.fulfill({ json: apiEnvelope(createProductDetail()) })
      return
    }
    if (method === 'GET' && path === '/api/products') {
      await route.fulfill({ json: apiEnvelope(createProductList()) })
      return
    }

    if (method === 'POST' && path === '/api/users/login') {
      await route.fulfill({ json: createLoginResponse() })
      return
    }

    if (method === 'GET' && path === '/api/orders') {
      orderRouteRequestCount++
      recordProtectedAuthHeader(route)
      await route.fulfill({ json: apiEnvelope(createOrderPage(mockOrders)) })
      return
    }
    if (method === 'GET' && /^\/api\/orders\/\d+$/.test(path)) {
      orderRouteRequestCount++
      recordProtectedAuthHeader(route)
      const orderId = Number(path.split('/').pop())
      const order = mockOrderById[orderId]
      if (order) {
        await route.fulfill({ json: apiEnvelope(order) })
      } else {
        await route.fulfill({
          status: 404,
          json: apiErrorEnvelope('ORDER_NOT_FOUND', 'Order not found', 'trace-order-404'),
        })
      }
      return
    }

    if (method === 'GET' && /^\/api\/payments\/.+$/.test(path)) {
      paymentRequestCount++
      recordProtectedAuthHeader(route)
      if (deferPaymentResponse) {
        pendingPaymentRoute = route
        return
      }
      if (mockPaymentError) {
        await route.fulfill({
          status: 503,
          json: apiErrorEnvelope(mockPaymentError.code, mockPaymentError.message, mockPaymentError.traceId),
        })
        return
      }
      await route.fulfill({ json: apiEnvelope(createPaymentResponse()) })
      return
    }

    if (method === 'POST' && /^\/api\/orders\/\d+\/receipt-confirmations$/.test(path)) {
      receiptConfirmationRequestCount++
      recordProtectedAuthHeader(route)
      const match = path.match(/^\/api\/orders\/(\d+)\/receipt-confirmations$/)
      const orderId = match ? Number(match[1]) : 0
      const rawBody = request.postData()
      receiptPayloads.push({
        body: rawBody ? JSON.parse(rawBody) : null,
        headers: request.headers(),
        path,
      })
      if (deferReceiptConfirmationResponse) {
        pendingReceiptRoute = route
        return
      }
      if (mockReceiptConfirmationError) {
        await route.fulfill({
          status: mockReceiptConfirmationError.status,
          json: apiErrorEnvelope(mockReceiptConfirmationError.code, mockReceiptConfirmationError.message, mockReceiptConfirmationError.traceId),
        })
        return
      }
      const order = mockOrderById[orderId]
      if (order) {
        const completed = createCompletedFromShippedOrder(order)
        mockOrders = mockOrders.map((o) => (o.orderId === orderId ? completed : o))
        mockOrderById[orderId] = completed
        await route.fulfill({ json: apiEnvelope(completed, 'ORDER_RECEIPT_CONFIRMED') })
      } else {
        await route.fulfill({
          status: 404,
          json: apiErrorEnvelope('ORDER_NOT_FOUND', 'Order not found', 'trace-receipt-404'),
        })
      }
      return
    }

    await route.continue()
  })
}

async function setEnglishLocale(page: Page) {
  await page.addInitScript((key: string) => {
    window.localStorage.setItem(key, 'en')
  }, LOCALE_STORAGE_KEY)
}

async function seedMallSession(page: Page) {
  await page.addInitScript(
    ([key, session]) => {
      window.sessionStorage.setItem(key, JSON.stringify(session))
    },
    [MALL_SESSION_KEY, MOCK_SESSION],
  )
}

function useOrderSet(orders: OrderResponse[]) {
  mockOrders = orders
  mockOrderById = {}
  for (const order of orders) {
    mockOrderById[order.orderId] = order
  }
}

const ALL_STATUS_ORDERS: OrderResponse[] = [
  createPaidUnshippedOrder(),
  createShippedOrder(),
  createCompletedOrder(),
  createCancelledOrder(),
  createUnknownStatusOrder(),
]

test.describe('Mall order status center browser smoke', () => {
  test.beforeAll(async () => {
    viteServer = await createServer({
      logLevel: 'error',
      server: {
        host: '127.0.0.1',
        port: 5173,
        strictPort: true,
      },
    })
    await viteServer.listen()
  })

  test.afterAll(async () => {
    await viteServer?.close()
    viteServer = null
  })

  test.beforeEach(async ({ page }) => {
    resetMockState()
    await setupDefaultApiRoutes(page)
    await setEnglishLocale(page)
  })

  test('shows login form when no mall session exists', async ({ page }) => {
    await page.goto('/')
    await expect(page.locator('.login-strip')).toBeVisible()
    await expect(page.locator('.session-strip')).not.toBeVisible()
    await expect(page.locator('.order-band')).not.toBeVisible()
    expect(orderRouteRequestCount).toBe(0)
    expect(receiptConfirmationRequestCount).toBe(0)
  })

  test('login persists session and loads order list', async ({ page }) => {
    useOrderSet([createPaidUnshippedOrder()])

    await page.goto('/')
    await expect(page.locator('.login-strip')).toBeVisible()

    await page.fill('input[type="number"]', '1')
    await page.fill('input[autocomplete="username"]', 'testuser')
    await page.fill('input[autocomplete="current-password"]', 'testpass')
    await page.locator('.login-strip button[type="submit"]').click()

    await expect(page.locator('.session-strip')).toBeVisible()
    await expect(page.locator('.order-band')).toBeVisible()

    const session = await page.evaluate((key: string) => {
      const raw = window.sessionStorage.getItem(key)
      return raw ? JSON.parse(raw) : null
    }, MALL_SESSION_KEY)
    expect(session).not.toBeNull()
    expect(session.accessToken).toBe('mock-jwt-token')
    expect(session.roles).toEqual(['MALL_USER'])

    await expect(page.locator('.order-card')).toHaveCount(1)
    expect(protectedApiAuthHeaders).toContain('Bearer mock-jwt-token')
  })

  test('loads order detail when an order card is clicked', async ({ page }) => {
    useOrderSet([createPaidUnshippedOrder()])
    await seedMallSession(page)
    await page.goto('/')

    await expect(page.locator('.order-card')).toBeVisible()
    await page.locator('.order-card').click()

    await expect(page.locator('.order-detail')).toBeVisible()
    await expect(page.locator('.order-headline')).toContainText('ORD-SMOKE-501')
    await expect(page.locator('.order-detail .detail-facts')).toContainText('Awaiting shipment')
  })

  test('renders shipped detail without awaiting-shipment regression', async ({ page }) => {
    useOrderSet([createShippedOrder()])
    await seedMallSession(page)
    await page.goto('/')

    await page.locator('.order-card').click()
    await expect(page.locator('.order-detail')).toBeVisible()

    const detailText = await page.locator('.order-detail').textContent()
    expect(detailText).toContain('Shipped')
    expect(detailText).not.toContain('Awaiting shipment')
    expect(detailText).toContain('SF Express')

    const refreshBtn = page.locator('button', { hasText: 'Refresh payment' })
    await expect(refreshBtn).toBeDisabled()
    await expect(refreshBtn).toHaveAttribute('title', /shipped.*payment refresh.*disabled/i)
  })

  test('renders completed detail without awaiting-shipment regression', async ({ page }) => {
    useOrderSet([createCompletedOrder()])
    await seedMallSession(page)
    await page.goto('/')

    await page.locator('.order-card').click()
    await expect(page.locator('.order-detail')).toBeVisible()

    const detailText = await page.locator('.order-detail').textContent()
    expect(detailText).toContain('Completed')
    expect(detailText).not.toContain('Awaiting shipment')
    expect(detailText).toContain('Reviewed')

    const refreshBtn = page.locator('button', { hasText: 'Refresh payment' })
    await expect(refreshBtn).toBeDisabled()
  })

  test('renders cancelled detail without awaiting-shipment regression', async ({ page }) => {
    useOrderSet([createCancelledOrder()])
    await seedMallSession(page)
    await page.goto('/')

    await page.locator('.order-card').click()
    await expect(page.locator('.order-detail')).toBeVisible()

    const detailText = await page.locator('.order-detail').textContent()
    expect(detailText).toContain('Cancelled')
    expect(detailText).not.toContain('Awaiting shipment')

    const refreshBtn = page.locator('button', { hasText: 'Refresh payment' })
    await expect(refreshBtn).toBeDisabled()
  })

  test('renders unknown status without crash or awaiting-shipment regression', async ({ page }) => {
    useOrderSet([createUnknownStatusOrder()])
    await seedMallSession(page)
    await page.goto('/')

    await page.locator('.order-card').click()
    await expect(page.locator('.order-detail')).toBeVisible()

    const detailText = await page.locator('.order-detail').textContent()
    expect(detailText).toContain('refunding')
    expect(detailText).not.toContain('Awaiting shipment')
  })

  test('shows payment refresh loading state and guards duplicate clicks', async ({ page }) => {
    useOrderSet([createPaidUnshippedOrder()])
    await seedMallSession(page)
    await page.goto('/?orderId=501&paymentNo=PAY-SMOKE-501')

    await expect(page.locator('.order-detail')).toBeVisible()
    await expect(page.locator('button', { hasText: 'Refresh payment' })).toBeVisible()

    // Snapshot baseline: if future app behavior introduces an automatic payment
    // refresh on detail load, this captures it so the deferred route is only
    // consumed by the manual click.
    const paymentCountBaseline = paymentRequestCount

    deferPaymentResponse = true

    try {
      await page.locator('button:has-text("Refresh payment")').click()
      await expect(page.locator('button:has-text("Refreshing")')).toBeVisible()
      await expect(page.locator('button:has-text("Refreshing")')).toBeDisabled()

      const countAfterClick = paymentRequestCount
      expect(countAfterClick).toBe(paymentCountBaseline + 1)

      await page.locator('button:has-text("Refreshing")').dispatchEvent('click')
      expect(paymentRequestCount).toBe(countAfterClick)

      const paymentRoute = pendingPaymentRoute
      expect(paymentRoute).not.toBeNull()
      if (!paymentRoute) {
        throw new Error('Expected deferred payment route to be captured.')
      }
      await paymentRoute.fulfill({ json: apiEnvelope(createPaymentResponse()) })
      pendingPaymentRoute = null

      await expect(page.locator('button', { hasText: 'Refresh payment' })).toBeVisible()
    } finally {
      if (pendingPaymentRoute !== null) {
        await pendingPaymentRoute.abort().catch(() => {})
        pendingPaymentRoute = null
      }
    }
  })

  test('payment refresh failure shows traceId and preserves current detail', async ({ page }) => {
    mockPaymentError = {
      code: 'PAYMENT_REFRESH_FAILED',
      message: 'Payment service unavailable.',
      traceId: 'trace-payment-503',
    }
    const paid = createPaidUnshippedOrder()
    useOrderSet([paid])
    await seedMallSession(page)
    await page.goto('/?orderId=501&paymentNo=PAY-SMOKE-501')

    await expect(page.locator('.order-detail')).toBeVisible()

    await page.locator('button:has-text("Refresh payment")').click()

    await expect(page.locator('.inline-feedback.danger')).toBeVisible()
    const errorText = await page.locator('.inline-feedback.danger').textContent()
    expect(errorText).toContain('PAYMENT_REFRESH_FAILED')
    expect(errorText).toContain('trace-payment-503')

    const detailText = await page.locator('.order-detail').textContent()
    expect(detailText).toContain('Paid')
    expect(detailText).toContain('Awaiting shipment')
  })

  test('restores order detail from deep link URL', async ({ page }) => {
    useOrderSet([createPaidUnshippedOrder()])
    await seedMallSession(page)
    await page.goto('/?orderId=501&paymentNo=PAY-SMOKE-501')

    await expect(page.locator('.order-detail')).toBeVisible()
    await expect(page.locator('.order-headline')).toContainText('ORD-SMOKE-501')
  })

  test('restores order detail after page reload', async ({ page }) => {
    useOrderSet([createPaidUnshippedOrder()])
    await seedMallSession(page)
    await page.goto('/?orderId=501')

    await expect(page.locator('.order-detail')).toBeVisible()
    await expect(page.locator('.order-headline')).toContainText('ORD-SMOKE-501')

    await page.reload()
    await expect(page.locator('.order-detail')).toBeVisible()
    await expect(page.locator('.order-headline')).toContainText('ORD-SMOKE-501')
  })

  test('displays all order status filters with correct counts', async ({ page }) => {
    useOrderSet(ALL_STATUS_ORDERS)
    await seedMallSession(page)
    await page.goto('/')

    await expect(page.locator('.order-cards')).toBeVisible()
    await expect(page.locator('.order-card')).toHaveCount(5)

    const segments = page.locator('.order-segments .segment')
    await expect(segments).toHaveCount(7)

    await expect(segments.nth(0)).toContainText('All')
    await expect(segments.nth(0).locator('strong')).toContainText('5')
    await expect(segments.nth(4)).toContainText('Completed')
    await expect(segments.nth(4).locator('strong')).toContainText('1')
  })

  test('filters orders by status segment', async ({ page }) => {
    useOrderSet(ALL_STATUS_ORDERS)
    await seedMallSession(page)
    await page.goto('/')

    await page.click('.segment:has-text("Shipped")')

    await expect(page.locator('.order-card')).toHaveCount(1)
    await expect(page.locator('.order-card')).toContainText('ORD-SMOKE-502')
  })

  test('selects and switches between orders', async ({ page }) => {
    useOrderSet(ALL_STATUS_ORDERS)
    await seedMallSession(page)
    await page.goto('/')

    await page.click('.order-card:has-text("ORD-SMOKE-501")')
    await expect(page.locator('.order-headline')).toContainText('ORD-SMOKE-501')

    await page.click('.order-card:has-text("ORD-SMOKE-502")')
    await expect(page.locator('.order-headline')).toContainText('ORD-SMOKE-502')
    await expect(page.locator('.order-detail')).toContainText('SF Express')
  })

  test('shipped order detail shows confirm receipt button enabled', async ({ page }) => {
    useOrderSet([createShippedOrder()])
    await seedMallSession(page)
    await page.goto('/')

    await page.locator('.order-card').click()
    await expect(page.locator('.order-detail')).toBeVisible()

    await expect(page.locator('.order-detail')).toContainText('Shipped')
    await expect(page.locator('.order-detail')).toContainText('SF Express')
    await expect(page.locator('.order-detail')).toContainText('SF123456789CN')

    const receiptBtn = page.locator('button', { hasText: 'Confirm receipt' })
    await expect(receiptBtn).toBeVisible()
    await expect(receiptBtn).toBeEnabled()

    expect(receiptConfirmationRequestCount).toBe(0)
  })

  test('non-shipped orders disable confirm receipt and send no POST', async ({ page }) => {
    for (const orders of [
      [createCreatedOrder()],
      [createPaidUnshippedOrder()],
      [createCancelledOrder()],
      [createCompletedOrder()],
      [createUnknownStatusOrder()],
    ]) {
      useOrderSet(orders)
      receiptConfirmationRequestCount = 0
      await seedMallSession(page)
      await page.goto('/')
      await page.locator('.order-card').click()

      await expect(page.locator('.order-detail')).toBeVisible()
      const receiptBtn = page.locator('.checkout-actions button', { hasText: 'Confirm receipt' })
      await expect(receiptBtn).toBeVisible()
      await expect(receiptBtn).toBeDisabled()
      expect(receiptConfirmationRequestCount).toBe(0)
    }
  })

  test('successful receipt confirmation asserts payload and syncs completed state', async ({ page }) => {
    const shipped = createShippedOrder()
    useOrderSet([shipped])
    await seedMallSession(page)
    await page.goto('/')

    await page.click('.order-card:has-text("ORD-SMOKE-502")')
    await expect(page.locator('.order-detail')).toBeVisible()

    await page.locator('button:has-text("Confirm receipt")').click()

    expect(receiptConfirmationRequestCount).toBe(1)
    const lastPayload = receiptPayloads.at(-1)
    expect(lastPayload).toBeDefined()
    if (!lastPayload) {
      throw new Error('Expected receipt confirmation payload to be captured.')
    }
    expect(lastPayload.path).toBe('/api/orders/502/receipt-confirmations')
    expect(lastPayload.headers.authorization).toBe('Bearer mock-jwt-token')
    const body = lastPayload.body as Record<string, unknown>
    expect(body.requestId).toBeTruthy()
    expect(typeof body.requestId).toBe('string')
    expect((body.requestId as string).length).toBeGreaterThan(0)
    expect(body.shopId).toBeUndefined()
    expect(body.userId).toBeUndefined()

    await expect(page.locator('.order-detail')).toContainText('Completed')
    await expect(page.locator('.order-detail')).toContainText('SF Express')
    await expect(page.locator('.order-detail')).toContainText('SF123456789CN')

    await expect(page.locator('button', { hasText: 'Confirm receipt' })).toBeDisabled()

    await expect(page.locator('.order-card:has-text("ORD-SMOKE-502")')).toContainText('Completed')
    await page.click('.segment:has-text("Completed")')
    await expect(page.locator('.order-card')).toHaveCount(1)
    await expect(page.locator('.order-card:has-text("ORD-SMOKE-502")')).toContainText('Completed')
  })

  test('duplicate pending receipt confirmation sends only one POST', async ({ page }) => {
    const shipped = createShippedOrder()
    useOrderSet([shipped])
    await seedMallSession(page)
    await page.goto('/')

    await page.click('.order-card:has-text("ORD-SMOKE-502")')
    await expect(page.locator('.order-detail')).toBeVisible()

    deferReceiptConfirmationResponse = true

    try {
      await page.locator('button:has-text("Confirm receipt")').click()
      await expect(page.locator('button:has-text("Confirming")')).toBeVisible()
      await expect(page.locator('button:has-text("Confirming")')).toBeDisabled()

      const countAfterClick = receiptConfirmationRequestCount

      await page.locator('button:has-text("Confirming")').dispatchEvent('click')
      expect(receiptConfirmationRequestCount).toBe(countAfterClick)

      const receiptRoute = pendingReceiptRoute
      expect(receiptRoute).not.toBeNull()
      if (!receiptRoute) {
        throw new Error('Expected deferred receipt route to be captured.')
      }
      await receiptRoute.fulfill({
        json: apiEnvelope(createCompletedFromShippedOrder(shipped), 'ORDER_RECEIPT_CONFIRMED'),
      })
      pendingReceiptRoute = null

      await expect(page.locator('button:has-text("Confirm receipt")')).toBeDisabled()
    } finally {
      if (pendingReceiptRoute !== null) {
        await pendingReceiptRoute.abort().catch(() => {})
        pendingReceiptRoute = null
      }
    }
  })

  test('receipt confirmation failure shows code message traceId and preserves shipped snapshot', async ({ page }) => {
    mockReceiptConfirmationError = {
      status: 409,
      code: 'ORDER_STATUS_INVALID',
      message: 'Order status does not allow receipt confirmation.',
      traceId: 'trace-receipt-409',
    }
    const shipped = createShippedOrder()
    useOrderSet([shipped])
    await seedMallSession(page)
    await page.goto('/')

    await page.click('.order-card:has-text("ORD-SMOKE-502")')
    await expect(page.locator('.order-detail')).toBeVisible()

    await page.locator('button:has-text("Confirm receipt")').click()

    await expect(page.locator('.inline-feedback.danger')).toBeVisible()
    const errorText = await page.locator('.inline-feedback.danger').textContent()
    expect(errorText).toContain('ORDER_STATUS_INVALID')
    expect(errorText).toContain('trace-receipt-409')

    await expect(page.locator('.order-detail')).toContainText('Shipped')
    await expect(page.locator('.order-detail')).toContainText('SF Express')
    await expect(page.locator('.order-detail')).toContainText('SF123456789CN')

    await expect(page.locator('button:has-text("Confirm receipt")')).toBeEnabled()
  })

  test('confirming receipt in shipped filter shows status-changed empty state', async ({ page }) => {
    const shipped = createShippedOrder()
    useOrderSet([shipped])
    await seedMallSession(page)
    await page.goto('/')

    await page.click('.segment:has-text("Shipped")')
    await expect(page.locator('.order-card')).toHaveCount(1)

    await page.click('.order-card')
    await expect(page.locator('.order-detail')).toBeVisible()

    await page.locator('button:has-text("Confirm receipt")').click()

    expect(receiptConfirmationRequestCount).toBe(1)

    await expect(page.locator('.order-card')).toHaveCount(0)
    await expect(page.locator('.order-list .status-block')).toContainText('status changed')
  })
})

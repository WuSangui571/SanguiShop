import { test, expect, type Page, type Route } from '@playwright/test'
import { createServer, type ViteDevServer } from 'vite'
import {
  apiEnvelope,
  apiErrorEnvelope,
  createOpsSession,
  createOpsSessionResponse,
  createOpsCompensationSession,
  createAdminOrderDetail,
  createAdminOrderPage,
  createAdminPaymentResponse,
  createShippedOrder,
  createCompletedOrder,
  createCancelledOrder,
  createUnknownOrder,
  createFulfillmentSession,
  createAdminFulfillmentResponse,
  OPS_SESSION_KEY,
} from './fixtures/adminOrderPaymentSmoke'
import type { AdminOrderSummaryResponse, AdminOrderDetailResponse, AdminFulfillmentResponse } from '../src/types/api/order'
import type { PaymentResponse } from '../src/types/api/payment'

const LOCALE_STORAGE_KEY = 'sangui.app.locale.v1'

let viteServer: ViteDevServer | null = null
let pendingPaymentRoute: Route | null = null
let adminApiCallCount = 0
let adminPaymentStatusCallCount = 0
let adminApiAuthHeaders: string[] = []
let adminOrderListQueries: URLSearchParams[] = []
let mockOrderSummaries: AdminOrderSummaryResponse[] = []
let mockOrderById: Record<number, AdminOrderDetailResponse> = {}
let mockPaymentStatus: PaymentResponse | null = null
let mockPaymentError: { code: string; message: string; traceId: string } | null = null
let mockListError: { code: string; message: string; traceId: string } | null = null
let mockDetailError: { code: string; message: string; traceId: string } | null = null
let deferPaymentResponse = false
let cancelApiCallCount = 0
let cancelRequestAuthHeaders: string[] = []
let cancelRequestPayloads: string[] = []
let mockCancelSuccess: AdminOrderDetailResponse | null = null
let mockCancelError: { code: string; message: string; traceId: string; httpStatus?: number } | null = null
let deferCancelResponse = false
let pendingCancelRoute: Route | null = null
let shipApiCallCount = 0
let shipApiAuthHeaders: string[] = []
let shipApiPayloads: string[] = []
let mockFulfillments: AdminFulfillmentResponse[] = []
let mockFulfillmentById: Record<number, AdminFulfillmentResponse> = {}
let mockFulfillmentShipSuccess: AdminFulfillmentResponse | null = null
let mockFulfillmentShipError: { code: string; message: string; traceId: string; httpStatus?: number } | null = null
let mockFulfillmentListError: { code: string; message: string; traceId: string } | null = null
let mockFulfillmentDetailError: { code: string; message: string; traceId: string } | null = null
let deferFulfillmentShip = false
let pendingFulfillmentShipRoute: Route | null = null
let mockSessionRefresh: ReturnType<typeof createOpsSessionResponse> | null = null

function resetMockState() {
  pendingPaymentRoute = null
  adminApiCallCount = 0
  adminPaymentStatusCallCount = 0
  adminApiAuthHeaders = []
  adminOrderListQueries = []
  mockOrderSummaries = []
  mockOrderById = {}
  mockPaymentStatus = null
  mockPaymentError = null
  mockListError = null
  mockDetailError = null
  deferPaymentResponse = false
  cancelApiCallCount = 0
  cancelRequestAuthHeaders = []
  cancelRequestPayloads = []
  mockCancelSuccess = null
  mockCancelError = null
  deferCancelResponse = false
  pendingCancelRoute = null
  shipApiCallCount = 0
  shipApiAuthHeaders = []
  shipApiPayloads = []
  mockFulfillments = []
  mockFulfillmentById = {}
  mockFulfillmentShipSuccess = null
  mockFulfillmentShipError = null
  mockFulfillmentListError = null
  mockFulfillmentDetailError = null
  deferFulfillmentShip = false
  pendingFulfillmentShipRoute = null
  mockSessionRefresh = null
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

function recordAdminApiCall(route: Route) {
  adminApiCallCount++
  adminApiAuthHeaders.push(route.request().headers().authorization ?? '')
}

async function setupDefaultApiRoutes(page: Page) {
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const path = extractPath(request.url())
    const method = request.method()

    if (method === 'POST' && path === '/api/users/ops/login') {
      await route.fulfill({
        status: 401,
        json: apiErrorEnvelope('AUTH_CREDENTIALS_INVALID', 'Invalid credentials', 'trace-login-401'),
      })
      return
    }

    if (method === 'POST' && path === '/api/users/ops/session/refresh') {
      await route.fulfill({ json: apiEnvelope(mockSessionRefresh ?? createOpsSessionResponse(), 'OPS_SESSION_REFRESHED') })
      return
    }

    if (method === 'GET' && path === '/api/admin/orders') {
      recordAdminApiCall(route)
      adminOrderListQueries.push(new URL(request.url()).searchParams)
      if (mockListError) {
        await route.fulfill({
          status: 503,
          json: apiErrorEnvelope(mockListError.code, mockListError.message, mockListError.traceId),
        })
        return
      }
      await route.fulfill({ json: apiEnvelope(createAdminOrderPage(mockOrderSummaries), 'ADMIN_ORDER_LIST') })
      return
    }

    if (method === 'GET' && /^\/api\/admin\/orders\/\d+$/.test(path)) {
      recordAdminApiCall(route)
      const orderId = Number(path.split('/').pop())
      if (mockDetailError && orderId === mockOrderById[orderId]?.orderId) {
        await route.fulfill({
          status: 503,
          json: apiErrorEnvelope(mockDetailError.code, mockDetailError.message, mockDetailError.traceId),
        })
        return
      }
      const detail = mockOrderById[orderId]
      if (detail) {
        await route.fulfill({ json: apiEnvelope(detail, 'ADMIN_ORDER_DETAIL') })
      } else {
        await route.fulfill({
          status: 404,
          json: apiErrorEnvelope('ORDER_NOT_FOUND', 'Order not found', 'trace-order-404'),
        })
      }
      return
    }

    if (method === 'GET' && /^\/api\/admin\/payments\/by-order\/\d+$/.test(path)) {
      recordAdminApiCall(route)
      adminPaymentStatusCallCount++
      if (deferPaymentResponse) {
        pendingPaymentRoute = route
        return
      }
      if (mockPaymentError) {
        await route.fulfill({
          status: mockPaymentError.code === 'PAYMENT_NOT_FOUND' ? 404 : 503,
          json: apiErrorEnvelope(mockPaymentError.code, mockPaymentError.message, mockPaymentError.traceId),
        })
        return
      }
      if (mockPaymentStatus) {
        await route.fulfill({ json: apiEnvelope(mockPaymentStatus, 'ADMIN_PAYMENT_STATUS') })
      } else {
        await route.fulfill({
          status: 404,
          json: apiErrorEnvelope('PAYMENT_NOT_FOUND', 'No payment row for this order', 'trace-payment-404'),
        })
      }
      return
    }

    if (method === 'POST' && /^\/api\/admin\/orders\/\d+\/cancel$/.test(path)) {
      recordAdminApiCall(route)
      cancelApiCallCount++
      cancelRequestAuthHeaders.push(request.headers().authorization ?? '')
      try {
        const body = request.postDataJSON()
        cancelRequestPayloads.push(JSON.stringify(body ?? {}))
      } catch {
        cancelRequestPayloads.push('{}')
      }

      const cancelOrderId = Number(path.split('/')[4])

      if (deferCancelResponse) {
        pendingCancelRoute = route
        return
      }

      if (mockCancelError) {
        await route.fulfill({
          status: mockCancelError.httpStatus ?? 409,
          json: apiErrorEnvelope(mockCancelError.code, mockCancelError.message, mockCancelError.traceId),
        })
        return
      }

      if (mockCancelSuccess) {
        applyMockCancelSuccess(cancelOrderId, mockCancelSuccess)
        await route.fulfill({
          status: 200,
          json: apiEnvelope(mockCancelSuccess, 'ADMIN_ORDER_CANCELLED', 'trace-admin-cancel-success'),
        })
        return
      }

      await route.fulfill({
        status: 409,
        json: apiErrorEnvelope('ORDER_STATUS_INVALID', 'Only created orders can be cancelled.', 'trace-order-cancel-invalid'),
      })
      return
    }

    if (method === 'GET' && path === '/api/admin/fulfillments') {
      recordAdminApiCall(route)
      if (mockFulfillmentListError) {
        await route.fulfill({
          status: 503,
          json: apiErrorEnvelope(mockFulfillmentListError.code, mockFulfillmentListError.message, mockFulfillmentListError.traceId),
        })
        return
      }
      await route.fulfill({ json: apiEnvelope({ page: 1, size: 20, total: mockFulfillments.length, items: mockFulfillments }, 'ADMIN_FULFILLMENT_LIST') })
      return
    }

    if (method === 'GET' && /^\/api\/admin\/fulfillments\/\d+$/.test(path)) {
      recordAdminApiCall(route)
      const orderId = Number(path.split('/').pop())
      if (mockFulfillmentDetailError && orderId === mockFulfillmentById[orderId]?.orderId) {
        await route.fulfill({
          status: 503,
          json: apiErrorEnvelope(mockFulfillmentDetailError.code, mockFulfillmentDetailError.message, mockFulfillmentDetailError.traceId),
        })
        return
      }
      const detail = mockFulfillmentById[orderId]
      if (detail) {
        await route.fulfill({ json: apiEnvelope(detail, 'ADMIN_FULFILLMENT_DETAIL') })
      } else {
        await route.fulfill({
          status: 404,
          json: apiErrorEnvelope('FULFILLMENT_NOT_FOUND', 'Fulfillment not found', 'trace-fulfillment-404'),
        })
      }
      return
    }

    if (method === 'POST' && /^\/api\/admin\/fulfillments\/\d+\/ship$/.test(path)) {
      recordAdminApiCall(route)
      shipApiCallCount++
      shipApiAuthHeaders.push(request.headers().authorization ?? '')
      try {
        const body = request.postDataJSON()
        shipApiPayloads.push(JSON.stringify(body ?? {}))
      } catch {
        shipApiPayloads.push('{}')
      }

      if (deferFulfillmentShip) {
        pendingFulfillmentShipRoute = route
        return
      }

      if (mockFulfillmentShipError) {
        await route.fulfill({
          status: mockFulfillmentShipError.httpStatus ?? 409,
          json: apiErrorEnvelope(mockFulfillmentShipError.code, mockFulfillmentShipError.message, mockFulfillmentShipError.traceId),
        })
        return
      }

      if (mockFulfillmentShipSuccess) {
        const shipOrderId = Number(path.split('/')[4])
        const shippedFulfillment = mockFulfillmentShipSuccess
        mockFulfillmentById[shipOrderId] = shippedFulfillment
        mockFulfillments = mockFulfillments.map((f) =>
          f.orderId === shipOrderId ? shippedFulfillment : f,
        )
        await route.fulfill({
          status: 200,
          json: apiEnvelope(shippedFulfillment, 'ADMIN_FULFILLMENT_SHIPPED', 'trace-admin-ship-success'),
        })
        return
      }

      await route.fulfill({
        status: 409,
        json: apiErrorEnvelope('ORDER_STATUS_INVALID', 'Only paid unshipped orders can be shipped.', 'trace-admin-ship-invalid'),
      })
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

async function seedOpsSession(page: Page, session: ReturnType<typeof createOpsSession>) {
  await page.addInitScript(
    ([key, data]) => {
      window.sessionStorage.setItem(key, JSON.stringify(data))
    },
    [OPS_SESSION_KEY, session],
  )
}

function useOrders(details: AdminOrderDetailResponse[]) {
  mockOrderById = {}
  mockOrderSummaries = []
  for (const detail of details) {
    mockOrderById[detail.orderId] = detail
    mockOrderSummaries.push({
      orderId: detail.orderId,
      orderNo: detail.orderNo,
      shopId: detail.shopId,
      userId: detail.userId,
      status: detail.status,
      totalAmountCent: detail.totalAmountCent,
      paymentNo: detail.paymentNo,
      itemCount: detail.items.reduce((sum, item) => sum + item.quantity, 0),
      traceId: detail.traceId,
      createdAt: detail.createdAt,
      updatedAt: detail.updatedAt,
    })
  }
}

function useSingleOrder(detail: AdminOrderDetailResponse) {
  useOrders([detail])
}

function useFulfillments(details: AdminFulfillmentResponse[]) {
  mockFulfillmentById = {}
  mockFulfillments = []
  for (const detail of details) {
    mockFulfillmentById[detail.orderId] = detail
    mockFulfillments.push(detail)
  }
}

function useSingleFulfillment(detail: AdminFulfillmentResponse) {
  useFulfillments([detail])
}

function applyMockCancelSuccess(orderId: number, detail: AdminOrderDetailResponse) {
  mockOrderById[orderId] = detail
  mockOrderSummaries = mockOrderSummaries.map((s) =>
    s.orderId === orderId
      ? {
          ...s,
          status: detail.status,
          paymentNo: detail.paymentNo,
          traceId: detail.traceId,
          updatedAt: detail.updatedAt,
        }
      : s,
  )
}

const ALL_STATUS_ORDERS: AdminOrderDetailResponse[] = [
  createAdminOrderDetail({ orderId: 1001, orderNo: 'ADM-CRT-1001', status: 'created', paymentNo: null }),
  createAdminOrderDetail({ orderId: 1002, orderNo: 'ADM-PAI-1002', status: 'paid', paymentNo: 'ADM-PAY-2002' }),
  createShippedOrder(),
  createCompletedOrder(),
  createCancelledOrder(),
  createUnknownOrder(),
]

test.describe('Admin order payment browser smoke', () => {
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

  test('shows login form when no ops session exists', async ({ page }) => {
    await page.goto('/admin?workspace=order')
    await expect(page.locator('.login-shell')).toBeVisible()
    await expect(page.locator('.order-admin-shell')).not.toBeVisible()
    expect(adminApiCallCount).toBe(0)
  })

  test('OPS_COMPENSATION_ADMIN-only session cannot access order workspace', async ({ page }) => {
    await seedOpsSession(page, createOpsCompensationSession())
    await page.goto('/admin?workspace=order')
    await expect(page.locator('.workspace-tab.active')).not.toContainText('Order management')
    await expect(page.locator('.order-admin-shell')).not.toBeVisible()
    expect(adminApiCallCount).toBe(0)
  })

  test('authorized session loads order list with auth headers', async ({ page }) => {
    useSingleOrder(createAdminOrderDetail({ orderId: 1001, orderNo: 'ADM-CRT-1001', status: 'created' }))
    await seedOpsSession(page, createOpsSession())
    await page.goto('/admin?workspace=order')

    await expect(page.locator('.order-admin-shell')).toBeVisible()
    await expect(page.locator('.workspace-tab.active')).toContainText('Order management')
    await expect(page.locator('.list-item')).toHaveCount(1)
    await expect(page.locator('.list-item')).toContainText('ADM-CRT-1001')
    await expect(page.locator('.list-item')).toContainText('Unpaid')
    expect(adminApiAuthHeaders).toContain('Bearer mock-ops-jwt-token')
    expect(adminOrderListQueries.length).toBeGreaterThan(0)
    const firstQuery = adminOrderListQueries[0]
    expect(firstQuery.get('page')).toBe('1')
    expect(firstQuery.get('size')).toBe('20')
    expect(firstQuery.has('status')).toBe(false)
    expect(firstQuery.has('orderNo')).toBe(false)
    expect(firstQuery.has('userId')).toBe(false)
  })

  test('empty list renders empty state without crash', async ({ page }) => {
    mockOrderSummaries = []
    mockOrderById = {}
    await seedOpsSession(page, createOpsSession())
    await page.goto('/admin?workspace=order')

    await expect(page.locator('.order-admin-shell')).toBeVisible()
    await expect(page.locator('section.banner.empty')).toBeVisible()
    await expect(page.locator('section.banner.empty')).toContainText('No orders match')
    await expect(page.locator('.list-item')).toHaveCount(0)
  })

  test('selects order detail from list click', async ({ page }) => {
    useSingleOrder(createAdminOrderDetail({
      orderId: 1001,
      orderNo: 'ADM-CRT-1001',
      status: 'created',
      paymentNo: null,
    }))
    await seedOpsSession(page, createOpsSession())
    await page.goto('/admin?workspace=order')

    await expect(page.locator('.list-item')).toBeVisible()
    await page.locator('.list-item').click()

    await expect(page.locator('.detail-panel')).toBeVisible()
    await expect(page.locator('.detail-panel')).toContainText('ADM-CRT-1001')
    await expect(page.locator('.summary-grid')).toContainText('Unpaid')
    await expect(page.locator('.summary-grid')).toContainText('ord:10001:req-smoke-1001')
    await expect(page.locator('.summary-grid')).toContainText('trace-order-1001')
  })

  test('renders all known status labels in list and detail', async ({ page }) => {
    useOrders(ALL_STATUS_ORDERS)
    await seedOpsSession(page, createOpsSession())
    await page.goto('/admin?workspace=order')

    await expect(page.locator('.list-item')).toHaveCount(6)
    const listText = await page.locator('.list-panel').textContent()
    expect(listText).toContain('Unpaid')
    expect(listText).toContain('Paid')
    expect(listText).toContain('Shipped')
    expect(listText).toContain('Completed')
    expect(listText).toContain('Cancelled')
    expect(listText).toContain('refunding')

    await page.locator('.list-item').first().click()
    await expect(page.locator('.summary-grid')).toContainText('Unpaid')
  })

  test('timeline preserves known and unknown status nodes', async ({ page }) => {
    useOrders([createShippedOrder(), createUnknownOrder()])
    await seedOpsSession(page, createOpsSession())
    await page.goto('/admin?workspace=order')

    await page.locator('.list-item:has-text("ADM-SHP-1003")').click()
    await expect(page.locator('.timeline')).toBeVisible()
    const timelineItems = page.locator('.timeline-item')
    await expect(timelineItems).toHaveCount(3)

    const timelineText = await page.locator('.timeline').textContent()
    expect(timelineText).toContain('Unpaid')
    expect(timelineText).toContain('Paid')
    expect(timelineText).toContain('Shipped')

    await page.locator('.list-item:has-text("ADM-UNK-1006")').click()
    await expect(page.locator('.timeline-item')).toHaveCount(2)
    const unknownTimelineText = await page.locator('.timeline').textContent()
    expect(unknownTimelineText).toContain('refunding')
    expect(unknownTimelineText).toContain('unrecognized status')
  })

  test('payment refresh updates paymentNo for created order without overwriting status', async ({ page }) => {
    useSingleOrder(createAdminOrderDetail({
      orderId: 1001,
      orderNo: 'ADM-CRT-1001',
      status: 'created',
      paymentNo: null,
    }))
    mockPaymentStatus = createAdminPaymentResponse({
      orderId: 1001,
      paymentNo: 'ADM-PAY-NEW',
      status: 'paid',
    })
    await seedOpsSession(page, createOpsSession())
    await page.goto('/admin?workspace=order')

    await page.locator('.list-item').click()
    await expect(page.locator('.detail-panel')).toContainText('ADM-CRT-1001')
    await expect(page.locator('.summary-grid')).toContainText('Unpaid')

    await page.locator('button:has-text("Refresh payment")').click()
    await expect(page.locator('.summary-grid')).toContainText('Payment No')
    await expect(page.locator('.summary-grid')).toContainText('ADM-PAY-NEW')
    await expect(page.locator('.summary-grid')).toContainText('Unpaid')
  })

  test('payment refresh preserves shipped main status', async ({ page }) => {
    useSingleOrder(createShippedOrder())
    mockPaymentStatus = createAdminPaymentResponse({
      orderId: 1003,
      orderNo: 'ADM-SHP-1003',
      paymentNo: 'ADM-PAY-NEW-2',
      status: 'paid',
    })
    await seedOpsSession(page, createOpsSession())
    await page.goto('/admin?workspace=order')

    await page.locator('.list-item').click()
    await expect(page.locator('.summary-grid')).toContainText('Shipped')

    await page.locator('button:has-text("Refresh payment")').click()
    await expect(page.locator('.summary-grid')).toContainText('Payment No')
    await expect(page.locator('.summary-grid')).toContainText('ADM-PAY-NEW-2')
    await expect(page.locator('.summary-grid')).toContainText('Shipped')
    const listItem = page.locator('.list-item.active')
    await expect(listItem).toContainText('Shipped')
  })

  test('payment refresh preserves completed main status', async ({ page }) => {
    useSingleOrder(createCompletedOrder())
    mockPaymentStatus = createAdminPaymentResponse({
      orderId: 1004,
      orderNo: 'ADM-CMP-1004',
      paymentNo: 'ADM-PAY-NEW-3',
      status: 'paid',
    })
    await seedOpsSession(page, createOpsSession())
    await page.goto('/admin?workspace=order')

    await page.locator('.list-item').click()
    await expect(page.locator('.summary-grid')).toContainText('Completed')

    await page.locator('button:has-text("Refresh payment")').click()
    await expect(page.locator('.summary-grid')).toContainText('Completed')
    const listItem = page.locator('.list-item.active')
    await expect(listItem).toContainText('Completed')
  })

  test('payment refresh preserves cancelled main status', async ({ page }) => {
    useSingleOrder(createCancelledOrder())
    mockPaymentStatus = createAdminPaymentResponse({
      orderId: 1005,
      orderNo: 'ADM-CNL-1005',
      paymentNo: 'ADM-PAY-NEW-4',
      status: 'paid',
    })
    await seedOpsSession(page, createOpsSession())
    await page.goto('/admin?workspace=order')

    await page.locator('.list-item').click()
    await expect(page.locator('.summary-grid')).toContainText('Cancelled')

    await page.locator('button:has-text("Refresh payment")').click()
    await expect(page.locator('.summary-grid')).toContainText('Cancelled')
    const listItem = page.locator('.list-item.active')
    await expect(listItem).toContainText('Cancelled')
  })

  test('payment refresh preserves unknown order main status', async ({ page }) => {
    useSingleOrder(createUnknownOrder())
    mockPaymentStatus = createAdminPaymentResponse({
      orderId: 1006,
      orderNo: 'ADM-UNK-1006',
      paymentNo: 'ADM-PAY-NEW-5',
      status: 'paid',
    })
    await seedOpsSession(page, createOpsSession())
    await page.goto('/admin?workspace=order')

    await page.locator('.list-item').click()
    await expect(page.locator('.summary-grid')).toContainText('refunding')

    await page.locator('button:has-text("Refresh payment")').click()
    await expect(page.locator('.summary-grid')).toContainText('refunding')
  })

  test('explicit payment refresh displays PAYMENT_NOT_FOUND with code message traceId', async ({ page }) => {
    useSingleOrder(createAdminOrderDetail({
      orderId: 1001,
      orderNo: 'ADM-CRT-1001',
      status: 'created',
      paymentNo: null,
    }))
    mockPaymentError = {
      code: 'PAYMENT_NOT_FOUND',
      message: 'No payment row for this order',
      traceId: 'trace-payment-missing',
    }
    await seedOpsSession(page, createOpsSession())
    await page.goto('/admin?workspace=order')

    await page.locator('.list-item').click()
    await expect(page.locator('.detail-panel')).toBeVisible()

    await page.locator('button:has-text("Refresh payment")').click()

    await expect(page.locator('.banner.error')).toBeVisible()
    const errorText = await page.locator('.banner.error').textContent()
    expect(errorText).toContain('PAYMENT_NOT_FOUND')
    expect(errorText).toContain('No payment row for this order')
    expect(errorText).toContain('trace-payment-missing')
    await expect(page.locator('.detail-panel')).toContainText('ADM-CRT-1001')
    await expect(page.locator('.summary-grid')).toContainText('Unpaid')
  })

  test('backend list error displays code message traceId', async ({ page }) => {
    mockListError = {
      code: 'DOWNSTREAM_TIMEOUT',
      message: 'Order service unavailable.',
      traceId: 'trace-list-503',
    }
    await seedOpsSession(page, createOpsSession())
    await page.goto('/admin?workspace=order')

    await expect(page.locator('.banner.error')).toBeVisible()
    const errorText = await page.locator('.banner.error').textContent()
    expect(errorText).toContain('DOWNSTREAM_TIMEOUT')
    expect(errorText).toContain('Order service unavailable.')
    expect(errorText).toContain('trace-list-503')
  })

  test('backend detail error displays code message traceId', async ({ page }) => {
    useSingleOrder(createAdminOrderDetail({ orderId: 1001, orderNo: 'ADM-CRT-1001', status: 'created' }))
    mockDetailError = {
      code: 'PAYMENT_REFRESH_FAILED',
      message: 'Payment service unavailable during detail load.',
      traceId: 'trace-detail-503',
    }
    await seedOpsSession(page, createOpsSession())
    await page.goto('/admin?workspace=order')

    await page.locator('.list-item').click()
    await expect(page.locator('.banner.error')).toBeVisible()
    const errorText = await page.locator('.detail-panel .banner.error').textContent()
    expect(errorText).toContain('PAYMENT_REFRESH_FAILED')
    expect(errorText).toContain('Payment service unavailable during detail load.')
    expect(errorText).toContain('trace-detail-503')
  })

  test('restores order detail from deep link URL', async ({ page }) => {
    useSingleOrder(createAdminOrderDetail({
      orderId: 9001,
      orderNo: 'ADM-DEEP-9001',
      status: 'paid',
      paymentNo: 'ADM-PAY-DEEP',
    }))
    mockPaymentStatus = createAdminPaymentResponse({
      orderId: 9001,
      paymentNo: 'ADM-PAY-DEEP',
      status: 'paid',
    })
    await seedOpsSession(page, createOpsSession())
    await page.goto('/admin?workspace=order&orderId=9001')

    await expect(page.locator('.detail-panel')).toBeVisible()
    await expect(page.locator('.detail-panel')).toContainText('ADM-DEEP-9001')
    await expect(page.locator('.summary-grid')).toContainText('Paid')
    await expect(page.locator('.summary-grid')).toContainText('ADM-PAY-DEEP')
  })

  test('restores order detail after page reload', async ({ page }) => {
    useSingleOrder(createAdminOrderDetail({
      orderId: 9001,
      orderNo: 'ADM-DEEP-9001',
      status: 'paid',
      paymentNo: 'ADM-PAY-DEEP',
    }))
    mockPaymentStatus = createAdminPaymentResponse({
      orderId: 9001,
      paymentNo: 'ADM-PAY-DEEP',
      status: 'paid',
    })
    await seedOpsSession(page, createOpsSession())
    await page.goto('/admin?workspace=order&orderId=9001')

    await expect(page.locator('.detail-panel')).toBeVisible()
    await expect(page.locator('.detail-panel')).toContainText('ADM-DEEP-9001')

    await page.reload()
    await expect(page.locator('.detail-panel')).toBeVisible()
    await expect(page.locator('.detail-panel')).toContainText('ADM-DEEP-9001')
    await expect(page.locator('.summary-grid')).toContainText('Paid')
  })

  test('shows payment refresh loading state and guards duplicate clicks', async ({ page }) => {
    useSingleOrder(createAdminOrderDetail({
      orderId: 1001,
      orderNo: 'ADM-CRT-1001',
      status: 'created',
      paymentNo: null,
    }))
    mockPaymentStatus = createAdminPaymentResponse({
      orderId: 1001,
      paymentNo: 'ADM-PAY-1001',
      status: 'paid',
    })
    await seedOpsSession(page, createOpsSession())
    await page.goto('/admin?workspace=order')

    await page.locator('.list-item').click()

    // Wait for selectOrder()'s automatic payment refresh to be captured before
    // enabling deferred responses, otherwise the auto-refresh can be deferred.
    await expect.poll(() => adminPaymentStatusCallCount).toBe(1)
    await expect(page.locator('button:has-text("Refresh payment")')).toBeVisible()

    deferPaymentResponse = true

    try {
      await page.locator('button:has-text("Refresh payment")').click()
      await expect(page.locator('button:has-text("Refreshing")')).toBeVisible()
      await expect(page.locator('button:has-text("Refreshing")')).toBeDisabled()

      const countAfterClick = adminPaymentStatusCallCount

      await page.locator('button:has-text("Refreshing")').dispatchEvent('click')
      expect(adminPaymentStatusCallCount).toBe(countAfterClick)

      const paymentRoute = pendingPaymentRoute
      expect(paymentRoute).not.toBeNull()
      if (!paymentRoute) {
        throw new Error('Expected deferred payment route to be captured.')
      }
      await paymentRoute.fulfill({
        json: apiEnvelope(createAdminPaymentResponse({ orderId: 1001, paymentNo: 'ADM-PAY-NEW' }), 'ADMIN_PAYMENT_STATUS'),
      })
      pendingPaymentRoute = null

      await expect(page.locator('button:has-text("Refresh payment")')).toBeVisible()
    } finally {
      if (pendingPaymentRoute !== null) {
        await pendingPaymentRoute.abort().catch(() => {})
        pendingPaymentRoute = null
      }
    }
  })

  test.describe('Admin order cancel browser smoke', () => {
    test('created order cancel button is visible and enabled', async ({ page }) => {
      useSingleOrder(createAdminOrderDetail({ orderId: 1001, orderNo: 'ADM-CRT-1001', status: 'created', paymentNo: null }))
      await seedOpsSession(page, createOpsSession())
      await page.goto('/admin?workspace=order')

      await page.locator('.list-item').click()
      await expect(page.locator('.detail-panel')).toBeVisible()

      const cancelBtn = page.locator('.detail-actions button.danger')
      await expect(cancelBtn).toBeVisible()
      await expect(cancelBtn).toBeEnabled()
    })

    test('first cancel click opens confirmation dialog without sending cancel request', async ({ page }) => {
      useSingleOrder(createAdminOrderDetail({ orderId: 1001, orderNo: 'ADM-CRT-1001', status: 'created', paymentNo: null }))
      await seedOpsSession(page, createOpsSession())
      await page.goto('/admin?workspace=order')

      await page.locator('.list-item').click()
      await page.locator('.detail-actions button.danger').click()

      await expect(page.locator('.confirm-dialog')).toBeVisible()
      await expect(page.locator('.confirm-dialog')).toContainText('ADM-CRT-1001')
      expect(cancelApiCallCount).toBe(0)

      await page.locator('.confirm-actions button.secondary').click()
      await expect(page.locator('.confirm-dialog')).not.toBeVisible()
    })

    test('confirm sends cancel request with ops auth header and requestId payload, then syncs status', async ({ page }) => {
      useSingleOrder(createAdminOrderDetail({ orderId: 1001, orderNo: 'ADM-CRT-1001', status: 'created', paymentNo: null }))
      mockCancelSuccess = createAdminOrderDetail({ orderId: 1001, orderNo: 'ADM-CRT-1001', status: 'cancelled', paymentNo: null })
      await seedOpsSession(page, createOpsSession())
      await page.goto('/admin?workspace=order')

      await page.locator('.list-item').click()
      await page.locator('.detail-actions button.danger').click()
      await expect(page.locator('.confirm-dialog')).toBeVisible()

      const countBefore = cancelApiCallCount
      await page.locator('.confirm-actions button.danger').click()

      expect(cancelApiCallCount).toBe(countBefore + 1)
      expect(cancelRequestAuthHeaders.some((h) => h === 'Bearer mock-ops-jwt-token')).toBe(true)

      expect(cancelRequestPayloads.length).toBeGreaterThan(0)
      const payload = JSON.parse(cancelRequestPayloads[0])
      expect(payload.requestId).toBeTruthy()
      expect(typeof payload.requestId).toBe('string')
      expect(payload.requestId.trim().length).toBeGreaterThan(0)

      await expect(page.locator('.confirm-dialog')).not.toBeVisible()
      await expect(page.locator('.summary-grid')).toContainText('Cancelled')
      await expect(page.locator('.list-item.active')).toContainText('Cancelled')
      await expect(page.locator('.detail-actions button.danger')).toBeDisabled()
    })

    test('duplicate cancel confirm while pending sends exactly one request', async ({ page }) => {
      useSingleOrder(createAdminOrderDetail({ orderId: 1001, orderNo: 'ADM-CRT-1001', status: 'created', paymentNo: null }))
      mockCancelSuccess = createAdminOrderDetail({ orderId: 1001, orderNo: 'ADM-CRT-1001', status: 'cancelled', paymentNo: null })
      deferCancelResponse = true
      await seedOpsSession(page, createOpsSession())
      await page.goto('/admin?workspace=order')

      await page.locator('.list-item').click()
      await page.locator('.detail-actions button.danger').click()
      await expect(page.locator('.confirm-dialog')).toBeVisible()

      try {
        await page.locator('.confirm-actions button.danger').click()
        await expect(page.locator('.confirm-actions button.danger')).toContainText('Cancelling')
        await expect(page.locator('.confirm-actions button.danger')).toBeDisabled()

        const countAfterFirst = cancelApiCallCount
        expect(countAfterFirst).toBe(1)

        await page.locator('.confirm-actions button.danger').dispatchEvent('click')
        expect(cancelApiCallCount).toBe(countAfterFirst)

        const cancelRoute = pendingCancelRoute
        expect(cancelRoute).not.toBeNull()
        if (!cancelRoute) {
          throw new Error('Expected deferred cancel route to be captured.')
        }
        const cancelSuccess = mockCancelSuccess
        expect(cancelSuccess).not.toBeNull()
        if (!cancelSuccess) {
          throw new Error('Expected cancel success mock to be configured.')
        }
        applyMockCancelSuccess(1001, cancelSuccess)
        await cancelRoute.fulfill({
          status: 200,
          json: apiEnvelope(cancelSuccess, 'ADMIN_ORDER_CANCELLED', 'trace-admin-cancel-success'),
        })
        pendingCancelRoute = null

        await expect(page.locator('.confirm-dialog')).not.toBeVisible()
      } finally {
        if (pendingCancelRoute !== null) {
          await pendingCancelRoute.abort().catch(() => {})
          pendingCancelRoute = null
        }
      }
    })

    test('non-created orders keep cancel disabled and send zero cancel requests', async ({ page }) => {
      useOrders([
        createAdminOrderDetail({ orderId: 1002, orderNo: 'ADM-PAI-1002', status: 'paid', paymentNo: 'ADM-PAY-2002' }),
        createShippedOrder(),
        createCompletedOrder(),
        createCancelledOrder(),
        createUnknownOrder(),
      ])
      await seedOpsSession(page, createOpsSession())
      await page.goto('/admin?workspace=order')

      const nonCreatedItems = ['ADM-PAI-1002', 'ADM-SHP-1003', 'ADM-CMP-1004', 'ADM-CNL-1005', 'ADM-UNK-1006']
      for (const orderNo of nonCreatedItems) {
        await page.locator(`.list-item:has-text("${orderNo}")`).click()
        await expect(page.locator('.detail-panel')).toBeVisible()
        await expect(page.locator('.detail-actions button.danger')).toBeDisabled()
      }

      expect(cancelApiCallCount).toBe(0)
    })

    test('cancel failure ORDER_STATUS_INVALID displays code message traceId and preserves detail snapshot', async ({ page }) => {
      useSingleOrder(createAdminOrderDetail({ orderId: 1001, orderNo: 'ADM-CRT-1001', status: 'created', paymentNo: null }))
      mockCancelError = {
        code: 'ORDER_STATUS_INVALID',
        message: 'Only created orders can be cancelled.',
        traceId: 'trace-order-cancel-invalid',
        httpStatus: 409,
      }
      await seedOpsSession(page, createOpsSession())
      await page.goto('/admin?workspace=order')

      await page.locator('.list-item').click()
      await page.locator('.detail-actions button.danger').click()
      await expect(page.locator('.confirm-dialog')).toBeVisible()

      await page.locator('.confirm-actions button.danger').click()

      await expect(page.locator('.detail-panel .banner.error')).toBeVisible()
      const errorText = await page.locator('.detail-panel .banner.error').textContent()
      expect(errorText).toContain('ORDER_STATUS_INVALID')
      expect(errorText).toContain('Only created orders can be cancelled.')
      expect(errorText).toContain('trace-order-cancel-invalid')

      await expect(page.locator('.summary-grid')).toContainText('ADM-CRT-1001')
      await expect(page.locator('.summary-grid')).toContainText('Unpaid')
      await expect(page.locator('.list-item.active')).toContainText('Unpaid')
    })

    test('cancel failure DOWNSTREAM_TIMEOUT displays code message traceId and preserves detail snapshot', async ({ page }) => {
      useSingleOrder(createAdminOrderDetail({ orderId: 1001, orderNo: 'ADM-CRT-1001', status: 'created', paymentNo: null }))
      mockCancelError = {
        code: 'DOWNSTREAM_TIMEOUT',
        message: 'Order service unavailable during cancel.',
        traceId: 'trace-order-cancel-timeout',
        httpStatus: 503,
      }
      await seedOpsSession(page, createOpsSession())
      await page.goto('/admin?workspace=order')

      await page.locator('.list-item').click()
      await page.locator('.detail-actions button.danger').click()
      await expect(page.locator('.confirm-dialog')).toBeVisible()

      await page.locator('.confirm-actions button.danger').click()

      await expect(page.locator('.detail-panel .banner.error')).toBeVisible()
      const errorText = await page.locator('.detail-panel .banner.error').textContent()
      expect(errorText).toContain('DOWNSTREAM_TIMEOUT')
      expect(errorText).toContain('Order service unavailable during cancel.')
      expect(errorText).toContain('trace-order-cancel-timeout')

      await expect(page.locator('.summary-grid')).toContainText('ADM-CRT-1001')
      await expect(page.locator('.summary-grid')).toContainText('Unpaid')
    })
  })

  test.describe('Admin fulfillment shipping browser smoke', () => {
    async function seedFulfillmentTest(page: Page) {
      mockSessionRefresh = createOpsSessionResponse(createFulfillmentSession())
      await seedOpsSession(page, createFulfillmentSession())
    }

    test('shows login form when no ops session exists', async ({ page }) => {
      await page.goto('/admin?workspace=fulfillment')
      await expect(page.locator('.login-shell')).toBeVisible()
      await expect(page.locator('.fulfillment-shell')).not.toBeVisible()
      expect(adminApiCallCount).toBe(0)
    })

    test('OPS_COMPENSATION_ADMIN-only session cannot access fulfillment workspace', async ({ page }) => {
      await seedOpsSession(page, createOpsCompensationSession())
      await page.goto('/admin?workspace=fulfillment')
      await expect(page.locator('.fulfillment-shell')).not.toBeVisible()
      expect(adminApiCallCount).toBe(0)
    })

    test('authorized session loads fulfillment list with auth headers', async ({ page }) => {
      useSingleFulfillment(createAdminFulfillmentResponse({ orderId: 1001, orderNo: 'FUL-ORD-1001', fulfillmentStatus: 'unshipped' }))
      await seedFulfillmentTest(page)
      await page.goto('/admin?workspace=fulfillment')

      await expect(page.locator('.fulfillment-shell')).toBeVisible()
      await expect(page.locator('.workspace-tab.active')).toContainText('Fulfillment')
      await expect(page.locator('.list-item')).toHaveCount(1)
      await expect(page.locator('.list-item')).toContainText('FUL-ORD-1001')
      expect(adminApiAuthHeaders).toContain('Bearer mock-ops-jwt-token')
    })

    test('empty list renders empty state without crash', async ({ page }) => {
      mockFulfillments = []
      mockFulfillmentById = {}
      await seedFulfillmentTest(page)
      await page.goto('/admin?workspace=fulfillment')

      await expect(page.locator('.fulfillment-shell')).toBeVisible()
      await expect(page.locator('section.banner.empty')).toBeVisible()
      await expect(page.locator('section.banner.empty')).toContainText('No fulfillment orders')
      await expect(page.locator('.list-item')).toHaveCount(0)
    })

    test('paid unshipped order shows ship form and button enabled, no ship API call before submit', async ({ page }) => {
      useSingleFulfillment(createAdminFulfillmentResponse({ orderId: 1001, orderNo: 'FUL-ORD-1001', fulfillmentStatus: 'unshipped' }))
      await seedFulfillmentTest(page)
      await page.goto('/admin?workspace=fulfillment')

      await expect(page.locator('.ship-form')).toBeVisible()
      const shipBtn = page.locator('.ship-form .primary')
      await expect(shipBtn).toBeVisible()
      await expect(shipBtn).toBeEnabled()
      expect(shipApiCallCount).toBe(0)
    })

    test('submits ship with ops auth header, requestId, trimmed carrier and trackingNo, then syncs detail and list', async ({ page }) => {
      useSingleFulfillment(createAdminFulfillmentResponse({ orderId: 1001, orderNo: 'FUL-ORD-1001', fulfillmentStatus: 'unshipped' }))
      mockFulfillmentShipSuccess = createAdminFulfillmentResponse({
        orderId: 1001,
        orderNo: 'FUL-ORD-1001',
        status: 'shipped',
        fulfillmentStatus: 'shipped',
        carrier: 'SF Express',
        trackingNo: 'SF123456789CN',
        shippedAt: '2026-05-16T09:00:00+08:00',
      })
      await seedFulfillmentTest(page)
      await page.goto('/admin?workspace=fulfillment')

      const inputs = page.locator('.ship-form input')
      await inputs.nth(0).fill(' SF Express ')
      await inputs.nth(1).fill(' SF123456789CN ')

      await page.locator('.ship-form .primary').click()

      expect(shipApiCallCount).toBe(1)
      expect(shipApiAuthHeaders).toContain('Bearer mock-ops-jwt-token')

      expect(shipApiPayloads.length).toBeGreaterThan(0)
      const payload = JSON.parse(shipApiPayloads[0])
      expect(payload.requestId).toBeTruthy()
      expect(typeof payload.requestId).toBe('string')
      expect(payload.requestId.trim().length).toBeGreaterThan(0)
      expect(payload.carrier).toBe('SF Express')
      expect(payload.trackingNo).toBe('SF123456789CN')

      await expect(page.locator('.summary-grid')).toContainText('Shipped')
      await expect(page.locator('.summary-grid')).toContainText('SF Express')
      await expect(page.locator('.summary-grid')).toContainText('SF123456789CN')
      await expect(page.locator('.list-item.active')).toContainText('Shipped')
    })

    test('duplicate ship click while pending sends exactly one request and shows pending state', async ({ page }) => {
      useSingleFulfillment(createAdminFulfillmentResponse({ orderId: 1001, orderNo: 'FUL-ORD-1001', fulfillmentStatus: 'unshipped' }))
      mockFulfillmentShipSuccess = createAdminFulfillmentResponse({
        orderId: 1001,
        orderNo: 'FUL-ORD-1001',
        status: 'shipped',
        fulfillmentStatus: 'shipped',
        carrier: 'SF Express',
        trackingNo: 'SF123456789CN',
      })
      deferFulfillmentShip = true
      await seedFulfillmentTest(page)
      await page.goto('/admin?workspace=fulfillment')

      const inputs = page.locator('.ship-form input')
      await inputs.nth(0).fill('SF Express')
      await inputs.nth(1).fill('SF123456789CN')

      try {
        await page.locator('.ship-form .primary').click()
        await expect(page.locator('.ship-form .primary')).toContainText('Shipping...')
        await expect(page.locator('.ship-form .primary')).toBeDisabled()

        const countAfterFirst = shipApiCallCount
        expect(countAfterFirst).toBe(1)

        await page.locator('.ship-form .primary').dispatchEvent('click')
        expect(shipApiCallCount).toBe(countAfterFirst)

        const shipRoute = pendingFulfillmentShipRoute
        expect(shipRoute).not.toBeNull()
        if (!shipRoute) {
          throw new Error('Expected deferred fulfillment ship route to be captured.')
        }
        await shipRoute.fulfill({
          status: 200,
          json: apiEnvelope(mockFulfillmentShipSuccess, 'ADMIN_FULFILLMENT_SHIPPED', 'trace-admin-ship-success'),
        })
        pendingFulfillmentShipRoute = null

        await expect(page.locator('.ship-form .primary')).toContainText('Ship order')
      } finally {
        if (pendingFulfillmentShipRoute !== null) {
          await pendingFulfillmentShipRoute.abort().catch(() => {})
          pendingFulfillmentShipRoute = null
        }
      }
    })

    test('non-shippable lifecycle statuses keep ship button disabled and send zero ship requests', async ({ page }) => {
      useFulfillments([
        createAdminFulfillmentResponse({ orderId: 1006, orderNo: 'FUL-CRT-1006', status: 'created', fulfillmentStatus: 'unshipped' }),
        createAdminFulfillmentResponse({ orderId: 1002, orderNo: 'FUL-SHP-1002', fulfillmentStatus: 'shipped', status: 'shipped' }),
        createAdminFulfillmentResponse({ orderId: 1003, orderNo: 'FUL-CMP-1003', fulfillmentStatus: 'completed', status: 'completed' }),
        createAdminFulfillmentResponse({ orderId: 1004, orderNo: 'FUL-CNL-1004', fulfillmentStatus: 'cancelled', status: 'cancelled' }),
        createAdminFulfillmentResponse({ orderId: 1005, orderNo: 'FUL-UNK-1005', fulfillmentStatus: 'refunding', status: 'refunding' }),
      ])
      await seedFulfillmentTest(page)
      await page.goto('/admin?workspace=fulfillment')

      const nonShippableItems = ['FUL-CRT-1006', 'FUL-SHP-1002', 'FUL-CMP-1003', 'FUL-CNL-1004', 'FUL-UNK-1005']
      for (const orderNo of nonShippableItems) {
        await page.locator(`.list-item:has-text("${orderNo}")`).click()
        await expect(page.locator('.detail-panel')).toBeVisible()
        const shipBtn = page.locator('.ship-form .primary')
        await expect(shipBtn).toBeDisabled()
      }

      expect(shipApiCallCount).toBe(0)
    })

    test('ORDER_STATUS_INVALID failure displays code message traceId and preserves detail and list snapshot', async ({ page }) => {
      useSingleFulfillment(createAdminFulfillmentResponse({ orderId: 1001, orderNo: 'FUL-ORD-1001', fulfillmentStatus: 'unshipped' }))
      mockFulfillmentShipError = {
        code: 'ORDER_STATUS_INVALID',
        message: 'Only paid unshipped orders can be shipped.',
        traceId: 'trace-admin-ship-invalid',
        httpStatus: 409,
      }
      await seedFulfillmentTest(page)
      await page.goto('/admin?workspace=fulfillment')

      const inputs = page.locator('.ship-form input')
      await inputs.nth(0).fill('SF Express')
      await inputs.nth(1).fill('SF123456789CN')

      await page.locator('.ship-form .primary').click()

      await expect(page.locator('.detail-panel .banner.error')).toBeVisible()
      const errorText = await page.locator('.detail-panel .banner.error').textContent()
      expect(errorText).toContain('ORDER_STATUS_INVALID')
      expect(errorText).toContain('Only paid unshipped orders can be shipped.')
      expect(errorText).toContain('trace-admin-ship-invalid')

      await expect(page.locator('.detail-panel')).toContainText('FUL-ORD-1001')
      await expect(page.locator('.summary-grid')).toContainText('Awaiting shipment')
      await expect(page.locator('.list-item.active')).toContainText('Awaiting shipment')
    })

    test('DOWNSTREAM_TIMEOUT failure displays code message traceId and preserves detail snapshot', async ({ page }) => {
      useSingleFulfillment(createAdminFulfillmentResponse({ orderId: 1001, orderNo: 'FUL-ORD-1001', fulfillmentStatus: 'unshipped' }))
      mockFulfillmentShipError = {
        code: 'DOWNSTREAM_TIMEOUT',
        message: 'Fulfillment service unavailable.',
        traceId: 'trace-admin-ship-timeout',
        httpStatus: 503,
      }
      await seedFulfillmentTest(page)
      await page.goto('/admin?workspace=fulfillment')

      const inputs = page.locator('.ship-form input')
      await inputs.nth(0).fill('SF Express')
      await inputs.nth(1).fill('SF123456789CN')

      await page.locator('.ship-form .primary').click()

      await expect(page.locator('.detail-panel .banner.error')).toBeVisible()
      const errorText = await page.locator('.detail-panel .banner.error').textContent()
      expect(errorText).toContain('DOWNSTREAM_TIMEOUT')
      expect(errorText).toContain('Fulfillment service unavailable.')
      expect(errorText).toContain('trace-admin-ship-timeout')

      await expect(page.locator('.detail-panel')).toContainText('FUL-ORD-1001')
      await expect(page.locator('.summary-grid')).toContainText('Awaiting shipment')
    })

    test('generic fulfillment failure preserves detail snapshot without optimistic shipped state', async ({ page }) => {
      useSingleFulfillment(createAdminFulfillmentResponse({ orderId: 1001, orderNo: 'FUL-ORD-1001', fulfillmentStatus: 'unshipped' }))
      mockFulfillmentShipError = {
        code: 'FULFILLMENT_FAILED',
        message: 'Logistics provider error.',
        traceId: 'trace-fulfillment-failed',
        httpStatus: 500,
      }
      await seedFulfillmentTest(page)
      await page.goto('/admin?workspace=fulfillment')

      await page.locator('.ship-form input').nth(0).fill('SF Express')
      await page.locator('.ship-form input').nth(1).fill('SF123456789CN')
      await page.locator('.ship-form .primary').click()

      await expect(page.locator('.detail-panel .banner.error')).toBeVisible()
      const errorText = await page.locator('.detail-panel .banner.error').textContent()
      expect(errorText).toContain('FULFILLMENT_FAILED')
      expect(errorText).toContain('Logistics provider error.')
      expect(errorText).toContain('trace-fulfillment-failed')

      await expect(page.locator('.detail-panel')).toContainText('FUL-ORD-1001')
      await expect(page.locator('.summary-grid')).toContainText('Awaiting shipment')
      await expect(page.locator('.list-item.active')).toContainText('Awaiting shipment')
    })

    test('successful ship does not overwrite order main status with payment status', async ({ page }) => {
      useSingleFulfillment(createAdminFulfillmentResponse({ orderId: 1001, orderNo: 'FUL-ORD-1001', status: 'paid', fulfillmentStatus: 'unshipped' }))
      mockFulfillmentShipSuccess = createAdminFulfillmentResponse({
        orderId: 1001,
        orderNo: 'FUL-ORD-1001',
        status: 'shipped',
        fulfillmentStatus: 'shipped',
        carrier: 'SF Express',
        trackingNo: 'SF123456789CN',
        shippedAt: '2026-05-16T09:00:00+08:00',
      })
      await seedFulfillmentTest(page)
      await page.goto('/admin?workspace=fulfillment')

      await page.locator('.ship-form input').nth(0).fill('SF Express')
      await page.locator('.ship-form input').nth(1).fill('SF123456789CN')
      await page.locator('.ship-form .primary').click()

      const summaryGrid = page.locator('.summary-grid')
      await expect(summaryGrid.locator('div').nth(1)).toContainText('Shipped')
      await expect(summaryGrid.locator('div').nth(2)).toContainText('Shipped')
      await expect(summaryGrid).not.toContainText('Paid')
      await expect(summaryGrid).not.toContainText('Awaiting shipment')
      await expect(page.locator('.list-item.active')).toContainText('Shipped')
    })
  })
})

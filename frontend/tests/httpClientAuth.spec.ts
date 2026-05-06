import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getJson, postJson, setAuthFailureHandler } from '../src/services/httpClient'
import {
  clearPersistedOpsSession,
  readPersistedOpsSession,
  writePersistedOpsSession,
} from '../src/services/opsSessionStorage'
import {
  clearPersistedMallSession,
  writePersistedMallSession,
} from '../src/services/mallSessionStorage'
import type { PersistedOpsSession } from '../src/types/api/auth'

function createSession(): PersistedOpsSession {
  return {
    userId: 10001,
    shopId: 1,
    username: 'ops-admin',
    accessToken: 'jwt-admin-token',
    tokenType: 'Bearer',
    expiresAt: '2026-05-05T10:00:00.000Z',
    roles: ['ADMIN'],
    permissions: [],
  }
}

describe('httpClient auth integration', () => {
  const storage = new Map<string, string>()

  beforeEach(() => {
    vi.stubGlobal('window', {
      sessionStorage: {
        getItem: (key: string) => storage.get(key) ?? null,
        setItem: (key: string, value: string) => {
          storage.set(key, value)
        },
        removeItem: (key: string) => {
          storage.delete(key)
        },
      },
    })
  })

  afterEach(() => {
    storage.clear()
    clearPersistedOpsSession()
    clearPersistedMallSession()
    setAuthFailureHandler(null)
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('round-trips persisted ops session state', () => {
    const session = createSession()

    writePersistedOpsSession(session)

    expect(readPersistedOpsSession()).toEqual(session)
  })

  it('injects authorization header from persisted session', async () => {
    writePersistedOpsSession(createSession())
    const fetchSpy = vi.fn(async (_input: string, init?: RequestInit) => {
      expect((init?.headers as Record<string, string>).Authorization).toBe('Bearer jwt-admin-token')
      return new Response(JSON.stringify({
        code: 'OK',
        message: 'ok',
        data: { ok: true },
        traceId: 'trace-ok',
        timestamp: '2026-05-05T08:00:00Z',
      }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      })
    })
    vi.stubGlobal('fetch', fetchSpy)

    const response = await postJson<{ ok: boolean }>('/api/internal/orders/compensation-records/query', {})

    expect(response.data.ok).toBe(true)
    expect(fetchSpy).toHaveBeenCalledOnce()
  })

  it('emits unauthorized auth failure by default but respects suppression', async () => {
    const failureSpy = vi.fn()
    setAuthFailureHandler(failureSpy)
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
      code: 'AUTH_TOKEN_EXPIRED',
      message: 'expired',
      data: null,
      traceId: 'trace-auth',
      timestamp: '2026-05-05T08:00:00Z',
    }), {
      status: 401,
      headers: { 'content-type': 'application/json' },
    })))

    await expect(postJson('/api/internal/payments/compensation-records/query', {}))
      .rejects.toMatchObject({ code: 'AUTH_TOKEN_EXPIRED', status: 401 })
    expect(failureSpy).toHaveBeenCalledOnce()

    failureSpy.mockClear()

    await expect(postJson('/api/users/ops/login', {}, { suppressAuthStateChange: true }))
      .rejects.toMatchObject({ code: 'AUTH_TOKEN_EXPIRED', status: 401 })
    expect(failureSpy).not.toHaveBeenCalled()
  })

  it('sends GET requests with query parameters and no request body', async () => {
    const fetchSpy = vi.fn(async (_input: string, init?: RequestInit) => {
      expect(init?.method).toBe('GET')
      expect(init?.body).toBeUndefined()
      return new Response(JSON.stringify({
        code: 'PRODUCT_LISTED',
        message: 'ok',
        data: { items: [], total: 0, page: 1, size: 20 },
        traceId: 'trace-products',
        timestamp: '2026-05-06T08:00:00Z',
      }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      })
    })
    vi.stubGlobal('fetch', fetchSpy)

    const response = await getJson('/api/products', { page: 1, size: 20 }, { authContext: 'none' })

    expect(fetchSpy.mock.calls[0]?.[0]).toBe('/api/products?page=1&size=20')
    expect(response.meta.code).toBe('PRODUCT_LISTED')
  })

  it('uses mall auth token for mall requests without replacing ops auth behavior', async () => {
    writePersistedOpsSession(createSession())
    writePersistedMallSession({
      userId: 20001,
      shopId: 1,
      accessToken: 'jwt-user-token',
      tokenType: 'Bearer',
      expiresAt: '2026-05-06T10:00:00.000Z',
      roles: ['USER'],
    })
    const seenTokens: string[] = []
    vi.stubGlobal('fetch', vi.fn(async (_input: string, init?: RequestInit) => {
      seenTokens.push((init?.headers as Record<string, string>).Authorization ?? '')
      return new Response(JSON.stringify({
        code: 'OK',
        message: 'ok',
        data: { ok: true },
        traceId: 'trace-token',
        timestamp: '2026-05-06T08:00:00Z',
      }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      })
    }))

    await postJson('/api/orders', {}, { authContext: 'mall' })
    await postJson('/api/internal/orders/compensation-records/query', {})

    expect(seenTokens).toEqual(['Bearer jwt-user-token', 'Bearer jwt-admin-token'])
  })
})

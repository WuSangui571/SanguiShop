import type { ApiResponseMeta, ApiResult } from '../types/api/common'
import { readPersistedMallAccessToken } from './mallSessionStorage'
import { readPersistedOpsAccessToken } from './opsSessionStorage'

export class HttpClientError extends Error {
  readonly code: string
  readonly status: number
  readonly traceId: string | null

  constructor(message: string, options: { code: string; status: number; traceId: string | null }) {
    super(message)
    this.name = 'HttpClientError'
    this.code = options.code
    this.status = options.status
    this.traceId = options.traceId
  }
}

export type AuthContext = 'ops' | 'mall' | 'none'

type QueryValue = string | number | boolean | null | undefined

interface RequestOptions {
  body?: unknown
  method?: 'GET' | 'POST' | 'PUT'
  query?: Record<string, QueryValue>
  suppressAuthStateChange?: boolean
  authContext?: AuthContext
}

interface JsonResponse<T> {
  data: T
  meta: ApiResponseMeta
}

interface AuthFailureEvent {
  type: 'unauthorized' | 'forbidden'
  error: HttpClientError
}

let authFailureHandler: ((event: AuthFailureEvent) => void) | null = null

function resolveBaseUrl(): string {
  return (import.meta.env.VITE_API_BASE_URL ?? '').trim()
}

function createRequestTraceId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID()
  }

  return `trace-${Date.now()}`
}

function resolveAuthToken(authContext: AuthContext): string | null {
  if (authContext === 'none') {
    return null
  }
  if (authContext === 'mall') {
    return readPersistedMallAccessToken()
  }

  return readPersistedOpsAccessToken()
}

export function setAuthFailureHandler(handler: ((event: AuthFailureEvent) => void) | null) {
  authFailureHandler = handler
}

async function parseEnvelope<T>(response: Response): Promise<ApiResult<T> | null> {
  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('application/json')) {
    return null
  }

  return (await response.json()) as ApiResult<T>
}

function toMeta<T>(response: Response, payload: ApiResult<T> | null): ApiResponseMeta {
  return {
    code: payload?.code ?? `HTTP_${response.status}`,
    message: payload?.message ?? response.statusText,
    traceId: payload?.traceId ?? '',
    timestamp: payload?.timestamp ?? '',
    status: response.status,
  }
}

function buildHeaders(authContext: AuthContext): HeadersInit {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    'X-Trace-Id': createRequestTraceId(),
  }

  const token = resolveAuthToken(authContext)
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  return headers
}

async function requestJson<T>(path: string, init: RequestOptions = {}): Promise<JsonResponse<T>> {
  const method = init.method ?? 'POST'
  const authContext = init.authContext ?? 'ops'
  const requestInit: RequestInit = {
    method,
    headers: buildHeaders(authContext),
  }

  if (method !== 'GET') {
    requestInit.body = JSON.stringify(init.body ?? {})
  }

  const response = await fetch(`${resolveBaseUrl()}${buildPath(path, init.query)}`, requestInit)

  const payload = await parseEnvelope<T>(response)
  const meta = toMeta(response, payload)
  const error = new HttpClientError(meta.message || 'Request failed.', {
    code: meta.code,
    status: meta.status,
    traceId: meta.traceId || null,
  })

  if (!response.ok || !payload) {
    if (!init.suppressAuthStateChange && authContext === 'ops' && authFailureHandler) {
      if (meta.status === 401) {
        authFailureHandler({ type: 'unauthorized', error })
      } else if (meta.status === 403 || meta.code === 'AUTH_FORBIDDEN') {
        authFailureHandler({ type: 'forbidden', error })
      }
    }
    throw error
  }

  return {
    data: payload.data,
    meta,
  }
}

export async function postJson<T>(
  path: string,
  body: unknown,
  options: { suppressAuthStateChange?: boolean; authContext?: AuthContext } = {},
): Promise<JsonResponse<T>> {
  return requestJson<T>(path, {
    body,
    suppressAuthStateChange: options.suppressAuthStateChange,
    authContext: options.authContext,
  })
}

export async function putJson<T>(
  path: string,
  body: unknown,
  options: { suppressAuthStateChange?: boolean; authContext?: AuthContext } = {},
): Promise<JsonResponse<T>> {
  return requestJson<T>(path, {
    method: 'PUT',
    body,
    suppressAuthStateChange: options.suppressAuthStateChange,
    authContext: options.authContext,
  })
}

export async function getJson<T>(
  path: string,
  query: Record<string, QueryValue> = {},
  options: { suppressAuthStateChange?: boolean; authContext?: AuthContext } = {},
): Promise<JsonResponse<T>> {
  return requestJson<T>(path, {
    method: 'GET',
    query,
    suppressAuthStateChange: options.suppressAuthStateChange,
    authContext: options.authContext,
  })
}

function buildPath(path: string, query?: Record<string, QueryValue>): string {
  const params = new URLSearchParams()
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value === null || value === undefined || value === '') {
      return
    }
    params.set(key, String(value))
  })

  const serialized = params.toString()
  return serialized ? `${path}?${serialized}` : path
}

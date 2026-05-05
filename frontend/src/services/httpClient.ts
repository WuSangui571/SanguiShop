import type { ApiResponseMeta, ApiResult } from '../types/api/common'
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

interface RequestOptions {
  body?: unknown
  suppressAuthStateChange?: boolean
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

function resolveAuthToken(): string | null {
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

function buildHeaders(): HeadersInit {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    'X-Trace-Id': createRequestTraceId(),
  }

  const token = resolveAuthToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  return headers
}

async function requestJson<T>(path: string, init: RequestOptions = {}): Promise<JsonResponse<T>> {
  const response = await fetch(`${resolveBaseUrl()}${path}`, {
    method: 'POST',
    headers: buildHeaders(),
    body: JSON.stringify(init.body ?? {}),
  })

  const payload = await parseEnvelope<T>(response)
  const meta = toMeta(response, payload)
  const error = new HttpClientError(meta.message || 'Request failed.', {
    code: meta.code,
    status: meta.status,
    traceId: meta.traceId || null,
  })

  if (!response.ok || !payload) {
    if (!init.suppressAuthStateChange && authFailureHandler) {
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
  options: { suppressAuthStateChange?: boolean } = {},
): Promise<JsonResponse<T>> {
  return requestJson<T>(path, { body, suppressAuthStateChange: options.suppressAuthStateChange })
}

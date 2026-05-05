import type { ApiResponseMeta, ApiResult } from '../types/api/common'

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
}

interface JsonResponse<T> {
  data: T
  meta: ApiResponseMeta
}

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
  if (typeof window === 'undefined') {
    return null
  }

  return window.sessionStorage.getItem('sangui.admin.token')
}

function clearExpiredAuthToken(code: string) {
  if (typeof window === 'undefined') {
    return
  }

  if (code === 'AUTH_TOKEN_EXPIRED') {
    window.sessionStorage.removeItem('sangui.admin.token')
  }
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

  clearExpiredAuthToken(meta.code)

  if (!response.ok || !payload) {
    throw new HttpClientError(meta.message || 'Request failed.', {
      code: meta.code,
      status: meta.status,
      traceId: meta.traceId || null,
    })
  }

  return {
    data: payload.data,
    meta,
  }
}

export async function postJson<T>(path: string, body: unknown): Promise<JsonResponse<T>> {
  return requestJson<T>(path, { body })
}

import { HttpClientError } from '../../services/httpClient'

export interface SeckillAdminErrorState {
  code: string
  message: string
  traceId: string | null
}

export function toSeckillAdminError(
  caught: unknown,
  fallback: string,
  fallbackCode = 'UNEXPECTED_ERROR',
): SeckillAdminErrorState {
  if (caught instanceof HttpClientError) {
    return {
      code: caught.code,
      message: caught.message,
      traceId: caught.traceId,
    }
  }

  return {
    code: fallbackCode,
    message: fallback,
    traceId: null,
  }
}

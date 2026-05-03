export interface ApiResult<T> {
  code: string
  message: string
  data: T
  traceId: string
  timestamp: string
}

export interface ApiResponseMeta {
  code: string
  message: string
  traceId: string
  timestamp: string
  status: number
}

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

export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface CreateOrderItemRequest {
  skuId: number
  quantity: number
}

export interface CreateOrderRequest {
  shopId: number
  userId: string
  requestId: string
  items: CreateOrderItemRequest[]
}

export type OrderStatus = 'created' | 'cancelled' | 'paid' | 'shipped' | 'completed' | string

export interface OrderItemResponse {
  productId: number
  skuId: number
  skuName: string
  priceCent: number
  quantity: number
  lineAmountCent: number
}

export interface OrderReviewResponse {
  orderReviewId: number
  shopId: number
  orderId: number
  orderNo: string
  userId: string
  rating: number
  content: string | null
  imageUrls: string[]
  requestId: string
  traceId: string | null
  createdAt: string | null
}

export interface CreateOrderReviewRequest {
  requestId: string
  rating: number
  content?: string | null
  imageUrls?: string[]
}

export interface OrderResponse {
  orderId: number
  orderNo: string
  shopId: number
  userId: string
  requestId: string
  status: OrderStatus
  totalAmountCent: number
  items: OrderItemResponse[]
  createdAt?: string | null
  updatedAt?: string | null
  fulfillmentStatus?: FulfillmentStatus | null
  carrier?: string | null
  trackingNo?: string | null
  shippedAt?: string | null
  completedAt?: string | null
  reviewed?: boolean
  review?: OrderReviewResponse | null
}

export interface OrderPageResponse {
  page: number
  size: number
  total: number
  items: OrderResponse[]
}

export type AdminOrderStatusFilter = 'all' | 'created' | 'paid' | 'cancelled' | string

export interface AdminOrderQueryParams {
  page: number
  size: number
  status?: AdminOrderStatusFilter
  orderNo?: string
  userId?: string
  fromTime?: string
  toTime?: string
}

export interface AdminOrderSummaryResponse {
  orderId: number
  orderNo: string
  shopId: number
  userId: string
  status: OrderStatus
  totalAmountCent: number
  paymentNo: string | null
  itemCount: number
  traceId: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface AdminOrderStatusTimelineResponse {
  status: OrderStatus
  occurredAt: string | null
  traceId: string | null
}

export interface AdminOrderDetailResponse {
  orderId: number
  orderNo: string
  shopId: number
  userId: string
  requestId: string
  reservationNo: string | null
  paymentNo: string | null
  status: OrderStatus
  totalAmountCent: number
  traceId: string | null
  createdAt: string | null
  updatedAt: string | null
  items: OrderItemResponse[]
  statusTimeline: AdminOrderStatusTimelineResponse[]
}

export interface AdminOrderPageResponse {
  page: number
  size: number
  total: number
  items: AdminOrderSummaryResponse[]
}

export interface AdminCancelOrderRequest {
  requestId: string
}

export interface ConfirmOrderReceiptRequest {
  requestId: string
}

export type FulfillmentStatus = 'all' | 'unshipped' | 'shipped' | 'completed' | string

export interface AdminFulfillmentQueryParams {
  page: number
  size: number
  status?: FulfillmentStatus
  orderNo?: string
  userId?: string
  fromTime?: string
  toTime?: string
}

export interface AdminFulfillmentResponse {
  orderId: number
  orderNo: string
  shopId: number
  userId: string
  status: OrderStatus
  fulfillmentStatus: FulfillmentStatus
  totalAmountCent: number
  carrier: string | null
  trackingNo: string | null
  shippedAt: string | null
  traceId: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface AdminFulfillmentPageResponse {
  page: number
  size: number
  total: number
  items: AdminFulfillmentResponse[]
}

export interface ShipFulfillmentRequest {
  requestId: string
  carrier: string
  trackingNo: string
}

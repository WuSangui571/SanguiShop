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

export type OrderStatus = 'created' | 'cancelled' | 'paid' | string

export interface OrderItemResponse {
  productId: number
  skuId: number
  skuName: string
  priceCent: number
  quantity: number
  lineAmountCent: number
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
}

export interface OrderPageResponse {
  page: number
  size: number
  total: number
  items: OrderResponse[]
}

import type { MallSession } from '../../types/api/auth'
import type { CreateOrderRequest, OrderResponse } from '../../types/api/order'

const CART_STORAGE_VERSION = 1
const CART_STORAGE_PREFIX = 'sangui.mall.cart.v1'
const MAX_CART_QUANTITY = 999

export interface CartItemInput {
  productId: number
  productName: string
  skuId: number
  skuName: string
  priceCent: number
  availableStock: number
  quantity: number
}

export interface MallCartItemDraft extends CartItemInput {
  shopId: number
  userId: string
  addedAt: string
  updatedAt: string
}

interface SerializedMallCart {
  version: number
  shopId: number
  userId: string
  items: MallCartItemDraft[]
}

export function createMallCartStorageKey(session: MallSession): string {
  return `${CART_STORAGE_PREFIX}:${session.shopId}:${String(session.userId)}`
}

export function addMallCartItem(
  currentItems: MallCartItemDraft[],
  session: MallSession,
  input: CartItemInput,
  now = new Date().toISOString(),
): MallCartItemDraft[] {
  const userId = String(session.userId)
  const nextQuantity = normalizeQuantity(input.quantity, input.availableStock)
  const existing = currentItems.find((item) => item.skuId === input.skuId)

  if (!existing) {
    return [
      ...currentItems,
      {
        shopId: session.shopId,
        userId,
        productId: input.productId,
        productName: input.productName,
        skuId: input.skuId,
        skuName: input.skuName,
        priceCent: input.priceCent,
        availableStock: input.availableStock,
        quantity: nextQuantity,
        addedAt: now,
        updatedAt: now,
      },
    ]
  }

  return currentItems.map((item) => {
    if (item.skuId !== input.skuId) {
      return item
    }

    return {
      ...item,
      productName: input.productName,
      skuName: input.skuName,
      priceCent: input.priceCent,
      availableStock: input.availableStock,
      quantity: normalizeQuantity(item.quantity + nextQuantity, input.availableStock),
      updatedAt: now,
    }
  })
}

export function setMallCartItemQuantity(
  currentItems: MallCartItemDraft[],
  skuId: number,
  quantity: number,
): MallCartItemDraft[] {
  return currentItems.map((item) => {
    if (item.skuId !== skuId) {
      return item
    }

    return {
      ...item,
      quantity: normalizeQuantity(quantity, item.availableStock),
      updatedAt: new Date().toISOString(),
    }
  })
}

export function removeMallCartItem(currentItems: MallCartItemDraft[], skuId: number): MallCartItemDraft[] {
  return currentItems.filter((item) => item.skuId !== skuId)
}

export function clearSubmittedMallCartItems(
  currentItems: MallCartItemDraft[],
  order: OrderResponse,
): MallCartItemDraft[] {
  const submittedSkuIds = new Set(order.items.map((item) => item.skuId))
  return currentItems.filter((item) => !submittedSkuIds.has(item.skuId))
}

export function calculateMallCartTotalCent(items: MallCartItemDraft[]): number {
  return items.reduce((total, item) => total + item.priceCent * item.quantity, 0)
}

export function calculateMallCartItemCount(items: MallCartItemDraft[]): number {
  return items.reduce((total, item) => total + item.quantity, 0)
}

export function canCheckoutMallCart(items: MallCartItemDraft[], isCheckingOut: boolean): boolean {
  return !isCheckingOut && items.length > 0 && items.every((item) => item.quantity > 0)
}

export function buildCartCreateOrderRequest(options: {
  session: MallSession
  requestId: string
  items: MallCartItemDraft[]
}): CreateOrderRequest {
  return {
    shopId: options.session.shopId,
    userId: String(options.session.userId),
    requestId: options.requestId,
    items: options.items.map((item) => ({
      skuId: item.skuId,
      quantity: item.quantity,
    })),
  }
}

export function serializeMallCart(session: MallSession, items: MallCartItemDraft[]): string {
  const snapshot: SerializedMallCart = {
    version: CART_STORAGE_VERSION,
    shopId: session.shopId,
    userId: String(session.userId),
    items,
  }

  return JSON.stringify(snapshot)
}

export function deserializeMallCart(serialized: string | null, session: MallSession): MallCartItemDraft[] {
  if (!serialized) {
    return []
  }

  try {
    const parsed = JSON.parse(serialized) as Partial<SerializedMallCart>
    if (
      parsed.version !== CART_STORAGE_VERSION
      || parsed.shopId !== session.shopId
      || parsed.userId !== String(session.userId)
      || !Array.isArray(parsed.items)
    ) {
      return []
    }

    return parsed.items.filter(isValidCartItem).map((item) => ({
      ...item,
      quantity: normalizeQuantity(item.quantity, item.availableStock),
    }))
  } catch {
    return []
  }
}

export function normalizeQuantity(quantity: number, availableStock: number): number {
  const boundedByStock = Number.isFinite(availableStock) && availableStock > 0
    ? Math.min(MAX_CART_QUANTITY, Math.floor(availableStock))
    : MAX_CART_QUANTITY
  const safeQuantity = Number.isFinite(quantity) ? Math.floor(quantity) : 1
  return Math.min(Math.max(1, safeQuantity), Math.max(1, boundedByStock))
}

function isValidCartItem(item: unknown): item is MallCartItemDraft {
  if (!item || typeof item !== 'object') {
    return false
  }

  const candidate = item as Partial<MallCartItemDraft>
  return (
    typeof candidate.shopId === 'number'
    && typeof candidate.userId === 'string'
    && typeof candidate.productId === 'number'
    && typeof candidate.productName === 'string'
    && typeof candidate.skuId === 'number'
    && typeof candidate.skuName === 'string'
    && typeof candidate.priceCent === 'number'
    && typeof candidate.availableStock === 'number'
    && typeof candidate.quantity === 'number'
    && typeof candidate.addedAt === 'string'
    && typeof candidate.updatedAt === 'string'
  )
}

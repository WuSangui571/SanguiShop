import { describe, expect, it } from 'vitest'
import { HttpClientError } from '../../services/httpClient'
import {
  buildCreateProductRequest,
  buildStatusUpdateRequest,
  buildStockAdjustmentRequest,
  buildUpdateProductRequest,
  createEmptyProductDraft,
  createSubmissionGate,
  createDraftFromDetail,
  toProductAdminError,
  validateProductDraft,
} from './productManagementModel'

describe('productManagementModel', () => {
  it('builds payloads with trimmed values', () => {
    const draft = createEmptyProductDraft()
    draft.productId = 101
    draft.productName = '  Sneaker  '
    draft.productDescription = '  Daily trainer  '
    draft.skus = [{
      skuId: 201,
      skuCode: '  shoe-42  ',
      skuName: '  42  ',
      priceCent: 59900,
      availableStock: 10,
    }]

    const createPayload = buildCreateProductRequest(1, '10001', draft)
    const updatePayload = buildUpdateProductRequest(1, '10001', draft)

    expect(createPayload.productName).toBe('Sneaker')
    expect(createPayload.skus[0].skuCode).toBe('shoe-42')
    expect(updatePayload.productId).toBe(101)
  })

  it('validates price, stock, and duplicate skuCode inputs', () => {
    const draft = createEmptyProductDraft()
    draft.productName = 'Product'
    draft.skus = [
      { skuId: null, skuCode: 'sku-a', skuName: 'A', priceCent: 59900, availableStock: 0 },
      { skuId: null, skuCode: 'sku-a', skuName: 'B', priceCent: 0, availableStock: -1 },
    ]

    const result = validateProductDraft(draft)

    expect(result.valid).toBe(false)
    expect(result.errors.skus[0].skuCode).toBe('duplicate skuCode')
    expect(result.errors.skus[1].skuCode).toBe('duplicate skuCode')
    expect(result.errors.skus[1].priceCent).toBe('priceCent must be a positive integer')
    expect(result.errors.skus[1].availableStock).toBe('availableStock must be a non-negative integer')
  })

  it('preserves traceId in error descriptions', () => {
    const error = new HttpClientError('Product save failed.', {
      code: 'VALIDATION_FAILED',
      status: 400,
      traceId: 'trace-product-1',
    })

    expect(toProductAdminError(error, 'fallback')).toEqual({
      code: 'VALIDATION_FAILED',
      message: 'Product save failed.',
      traceId: 'trace-product-1',
    })
  })

  it('guards duplicate submit attempts', () => {
    const gate = createSubmissionGate()

    expect(gate.begin()).toBe(true)
    expect(gate.begin()).toBe(false)
    gate.end()
    expect(gate.begin()).toBe(true)
  })

  it('builds stock and status request payloads', () => {
    expect(buildStatusUpdateRequest('active', 'req-1')).toEqual({ status: 'active', requestId: 'req-1' })
    expect(buildStockAdjustmentRequest(20, 'req-2')).toEqual({ availableStock: 20, requestId: 'req-2' })
  })

  it('creates a draft from product detail', () => {
    const draft = createDraftFromDetail({
      productId: 101,
      productName: 'Sneaker',
      productDescription: 'Daily trainer',
      status: 'active',
      skus: [{
        skuId: 201,
        skuCode: 'shoe-42',
        skuName: '42',
        priceCent: 59900,
        availableStock: 10,
        reservedStock: 0,
      }],
    })

    expect(draft.productId).toBe(101)
    expect(draft.skus[0].skuCode).toBe('shoe-42')
  })
})

import { afterEach, describe, expect, it, vi } from 'vitest'
import { uploadReviewImage } from '../src/services/uploadApi'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('uploadApi', () => {
  it('posts review image as FormData without overriding multipart content type', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({
      code: 'REVIEW_IMAGE_UPLOADED',
      message: 'ok',
      data: {
        url: '/api/uploads/review-images/review-a.jpg',
        contentType: 'image/jpeg',
        sizeBytes: 3,
      },
      traceId: 'trace-upload',
      timestamp: '2026-05-08T10:00:00+08:00',
    }), {
      status: 200,
      headers: { 'content-type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    const file = new File([new Uint8Array([0xFF, 0xD8, 0xFF])], 'review.jpg', { type: 'image/jpeg' })
    const result = await uploadReviewImage(file)

    expect(result.data.url).toBe('/api/uploads/review-images/review-a.jpg')
    expect(fetchMock).toHaveBeenCalledOnce()
    const firstCall = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    const [, init] = firstCall
    expect(init.method).toBe('POST')
    expect(init.body).toBeInstanceOf(FormData)
    expect((init.headers as Record<string, string>)['Content-Type']).toBeUndefined()
    expect((init.headers as Record<string, string>).Accept).toBe('application/json')
  })
})

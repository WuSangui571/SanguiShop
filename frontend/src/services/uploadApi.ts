import { postFormData } from './httpClient'
import type { ReviewImageUploadResponse } from '../types/api/upload'

export function uploadReviewImage(file: File) {
  const formData = new FormData()
  formData.set('file', file)
  return postFormData<ReviewImageUploadResponse>('/api/uploads/review-images', formData, {
    authContext: 'mall',
    suppressAuthStateChange: true,
  })
}

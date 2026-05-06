import { postJson } from './httpClient'
import type { MallLoginRequest, MallLoginResponse } from '../types/api/auth'

export function loginMallUser(payload: MallLoginRequest) {
  return postJson<MallLoginResponse>('/api/users/login', payload, {
    authContext: 'none',
    suppressAuthStateChange: true,
  })
}

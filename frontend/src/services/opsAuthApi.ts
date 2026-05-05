import { postJson } from './httpClient'
import type { OpsLoginRequest, OpsSessionResponse } from '../types/api/auth'

export function loginOpsUser(payload: OpsLoginRequest) {
  return postJson<OpsSessionResponse>('/api/users/ops/login', payload, {
    suppressAuthStateChange: true,
  })
}

export function refreshOpsSession() {
  return postJson<OpsSessionResponse>('/api/users/ops/session/refresh', {})
}

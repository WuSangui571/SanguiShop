export interface OpsLoginRequest {
  shopId: number
  usernameOrMobile: string
  password: string
}

export interface OpsSessionResponse {
  userId: number
  shopId: number
  username: string
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  roles: string[]
  permissions: string[]
}

export interface PersistedOpsSession {
  userId: number
  shopId: number
  username: string
  accessToken: string
  tokenType: string
  expiresAt: string
  roles: string[]
  permissions: string[]
}

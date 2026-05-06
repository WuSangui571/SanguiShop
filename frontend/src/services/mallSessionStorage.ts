import type { MallSession } from '../types/api/auth'

const MALL_SESSION_KEY = 'sangui.mall.session'

export function readPersistedMallSession(): MallSession | null {
  if (typeof window === 'undefined') {
    return null
  }

  const serialized = window.sessionStorage.getItem(MALL_SESSION_KEY)
  if (!serialized) {
    return null
  }

  try {
    const parsed = JSON.parse(serialized) as MallSession
    if (
      typeof parsed.userId !== 'number'
      || typeof parsed.shopId !== 'number'
      || typeof parsed.accessToken !== 'string'
      || typeof parsed.tokenType !== 'string'
      || typeof parsed.expiresAt !== 'string'
      || !Array.isArray(parsed.roles)
    ) {
      return null
    }

    return parsed
  } catch {
    return null
  }
}

export function writePersistedMallSession(session: MallSession) {
  if (typeof window === 'undefined') {
    return
  }

  window.sessionStorage.setItem(MALL_SESSION_KEY, JSON.stringify(session))
}

export function clearPersistedMallSession() {
  if (typeof window === 'undefined') {
    return
  }

  window.sessionStorage.removeItem(MALL_SESSION_KEY)
}

export function readPersistedMallAccessToken(): string | null {
  return readPersistedMallSession()?.accessToken ?? null
}

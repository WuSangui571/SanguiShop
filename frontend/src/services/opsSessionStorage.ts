import type { PersistedOpsSession } from '../types/api/auth'

const OPS_SESSION_KEY = 'sangui.ops.session'

export function readPersistedOpsSession(): PersistedOpsSession | null {
  if (typeof window === 'undefined') {
    return null
  }

  const serialized = window.sessionStorage.getItem(OPS_SESSION_KEY)
  if (!serialized) {
    return null
  }

  try {
    const parsed = JSON.parse(serialized) as PersistedOpsSession
    if (
      typeof parsed.accessToken !== 'string'
      || typeof parsed.username !== 'string'
      || typeof parsed.expiresAt !== 'string'
      || !Array.isArray(parsed.roles)
      || !Array.isArray(parsed.permissions)
    ) {
      return null
    }

    return parsed
  } catch {
    return null
  }
}

export function writePersistedOpsSession(session: PersistedOpsSession) {
  if (typeof window === 'undefined') {
    return
  }

  window.sessionStorage.setItem(OPS_SESSION_KEY, JSON.stringify(session))
}

export function clearPersistedOpsSession() {
  if (typeof window === 'undefined') {
    return
  }

  window.sessionStorage.removeItem(OPS_SESSION_KEY)
}

export function readPersistedOpsAccessToken(): string | null {
  return readPersistedOpsSession()?.accessToken ?? null
}

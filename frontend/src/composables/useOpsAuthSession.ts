import { computed, reactive } from 'vue'
import { HttpClientError, setAuthFailureHandler } from '../services/httpClient'
import { loginOpsUser, refreshOpsSession } from '../services/opsAuthApi'
import {
  clearPersistedOpsSession,
  readPersistedOpsSession,
  writePersistedOpsSession,
} from '../services/opsSessionStorage'
import type { OpsLoginRequest, OpsSessionResponse, PersistedOpsSession } from '../types/api/auth'

type AuthStatus = 'booting' | 'unauthenticated' | 'authenticated' | 'forbidden'

interface AuthState {
  status: AuthStatus
  session: PersistedOpsSession | null
  error: HttpClientError | null
  notice: string
  isSubmitting: boolean
  isRefreshing: boolean
}

const REFRESH_WINDOW_MS = 60_000

const state = reactive<AuthState>({
  status: 'booting',
  session: null,
  error: null,
  notice: '',
  isSubmitting: false,
  isRefreshing: false,
})

let bootstrapped = false
let refreshTimer: number | null = null

setAuthFailureHandler((event) => {
  if (event.type === 'unauthorized') {
    clearSession('Session expired. Sign in again to continue compensation operations.')
    state.error = null
    return
  }

  if (state.session) {
    state.status = 'forbidden'
    state.error = event.error
  }
})

export function useOpsAuthSession() {
  const isAuthenticated = computed(() => state.status === 'authenticated')
  const isForbidden = computed(() => state.status === 'forbidden')
  const isBooting = computed(() => state.status === 'booting')
  const sessionExpiresLabel = computed(() => {
    if (!state.session) {
      return '--'
    }
    return new Date(state.session.expiresAt).toLocaleString()
  })

  async function bootstrap() {
    if (bootstrapped) {
      return
    }
    bootstrapped = true

    const restored = readPersistedOpsSession()
    if (!restored) {
      state.status = 'unauthenticated'
      return
    }

    if (isExpired(restored)) {
      clearSession('Stored session expired. Sign in again to continue.')
      return
    }

    applySession(restored)

    if (shouldRefreshSoon(restored)) {
      await runRefreshSession({ silent: true })
    }
  }

  async function login(payload: OpsLoginRequest) {
    state.isSubmitting = true
    state.error = null
    state.notice = ''

    try {
      const result = await loginOpsUser(payload)
      applySession(toPersistedSession(result.data))
      return true
    } catch (caught) {
      state.error = toHttpClientError(caught, 'Unable to sign in.')
      state.status = 'unauthenticated'
      return false
    } finally {
      state.isSubmitting = false
    }
  }

  async function refreshSession(options: { silent?: boolean } = {}) {
    return runRefreshSession(options)
  }

  function signOut(message = 'You have signed out.') {
    clearSession(message)
    state.error = null
  }

  function clearNotice() {
    state.notice = ''
  }

  return {
    state,
    isAuthenticated,
    isForbidden,
    isBooting,
    sessionExpiresLabel,
    bootstrap,
    login,
    refreshSession,
    signOut,
    clearNotice,
  }
}

async function runRefreshSession(options: { silent?: boolean } = {}) {
  if (!state.session) {
    return false
  }

  const { silent = false } = options
  state.isRefreshing = true
  if (!silent) {
    state.error = null
    state.notice = ''
  }

  try {
    const result = await refreshOpsSession()
    applySession(toPersistedSession(result.data))
    if (!silent) {
      state.notice = 'Session refreshed.'
    }
    return true
  } catch (caught) {
    const error = toHttpClientError(caught, 'Session refresh failed.')
    if (error.status === 403 || error.code === 'AUTH_FORBIDDEN') {
      state.status = 'forbidden'
      state.error = error
      return false
    }
    if (!silent) {
      state.error = error
    }
    return false
  } finally {
    state.isRefreshing = false
  }
}

function applySession(session: PersistedOpsSession) {
  state.session = session
  state.status = 'authenticated'
  state.error = null
  writePersistedOpsSession(session)
  scheduleRefresh(session)
}

function clearSession(notice: string) {
  state.session = null
  state.status = 'unauthenticated'
  state.notice = notice
  cancelRefreshTimer()
  clearPersistedOpsSession()
}

function scheduleRefresh(session: PersistedOpsSession) {
  cancelRefreshTimer()
  if (typeof window === 'undefined') {
    return
  }

  const expiresAtMs = new Date(session.expiresAt).getTime()
  const delay = Math.max(0, expiresAtMs - Date.now() - REFRESH_WINDOW_MS)
  refreshTimer = window.setTimeout(() => {
    void runRefreshSession({ silent: true })
  }, delay)
}

function cancelRefreshTimer() {
  if (refreshTimer !== null && typeof window !== 'undefined') {
    window.clearTimeout(refreshTimer)
  }
  refreshTimer = null
}

function shouldRefreshSoon(session: PersistedOpsSession): boolean {
  const expiresAtMs = new Date(session.expiresAt).getTime()
  return expiresAtMs - Date.now() <= REFRESH_WINDOW_MS
}

function isExpired(session: PersistedOpsSession): boolean {
  return new Date(session.expiresAt).getTime() <= Date.now()
}

function toPersistedSession(session: OpsSessionResponse): PersistedOpsSession {
  return {
    userId: session.userId,
    shopId: session.shopId,
    username: session.username,
    accessToken: session.accessToken,
    tokenType: session.tokenType,
    expiresAt: new Date(Date.now() + session.expiresInSeconds * 1000).toISOString(),
    roles: [...session.roles],
    permissions: [...session.permissions],
  }
}

function toHttpClientError(caught: unknown, fallbackMessage: string): HttpClientError {
  if (caught instanceof HttpClientError) {
    return caught
  }

  return new HttpClientError(fallbackMessage, {
    code: 'UNEXPECTED_ERROR',
    status: 0,
    traceId: null,
  })
}

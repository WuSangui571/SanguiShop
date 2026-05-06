import { computed, reactive } from 'vue'
import { loginMallUser } from '../services/mallAuthApi'
import {
  clearPersistedMallSession,
  readPersistedMallSession,
  writePersistedMallSession,
} from '../services/mallSessionStorage'
import { HttpClientError } from '../services/httpClient'
import type { MallLoginRequest, MallLoginResponse, MallSession } from '../types/api/auth'

interface MallSessionState {
  session: MallSession | null
  error: HttpClientError | null
  isSubmitting: boolean
}

const state = reactive<MallSessionState>({
  session: null,
  error: null,
  isSubmitting: false,
})

let bootstrapped = false

export function useMallSession() {
  const isAuthenticated = computed(() => Boolean(state.session) && !isExpired(state.session))

  function bootstrap() {
    if (bootstrapped) {
      return
    }
    bootstrapped = true

    const restored = readPersistedMallSession()
    if (!restored || isExpired(restored)) {
      clearPersistedMallSession()
      state.session = null
      return
    }

    state.session = restored
  }

  async function login(payload: MallLoginRequest) {
    state.isSubmitting = true
    state.error = null

    try {
      const result = await loginMallUser(payload)
      const session = toPersistedSession(result.data)
      writePersistedMallSession(session)
      state.session = session
      return true
    } catch (caught) {
      state.error = toHttpClientError(caught, 'Unable to sign in.')
      return false
    } finally {
      state.isSubmitting = false
    }
  }

  function signOut() {
    clearPersistedMallSession()
    state.session = null
    state.error = null
  }

  return {
    state,
    isAuthenticated,
    bootstrap,
    login,
    signOut,
  }
}

function toPersistedSession(session: MallLoginResponse): MallSession {
  return {
    userId: session.userId,
    shopId: session.shopId,
    accessToken: session.accessToken,
    tokenType: session.tokenType,
    expiresAt: new Date(Date.now() + session.expiresInSeconds * 1000).toISOString(),
    roles: [...session.roles],
  }
}

function isExpired(session: MallSession | null): boolean {
  if (!session) {
    return true
  }

  return new Date(session.expiresAt).getTime() <= Date.now()
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

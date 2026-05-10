// @vitest-environment happy-dom
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { shallowMount, type VueWrapper } from '@vue/test-utils'
import type { PersistedOpsSession } from './types/api/auth'

const mockSessionRef = vi.hoisted(() => ({ current: null as PersistedOpsSession | null }))

vi.mock('./composables/useOpsAuthSession', () => ({
  useOpsAuthSession: () => ({
    state: {
      status: 'authenticated',
      session: mockSessionRef.current,
      error: null,
      notice: '',
      isSubmitting: false,
      isRefreshing: false,
    },
    isAuthenticated: true,
    isForbidden: false,
    isBooting: false,
    sessionExpiresLabel: '--',
    bootstrap: vi.fn(),
    login: vi.fn(),
    refreshSession: vi.fn(),
    signOut: vi.fn(),
    clearNotice: vi.fn(),
  }),
}))

vi.mock('./composables/useAppPreferences', () => ({
  useAppPreferences: () => ({
    t: (key: string) => key,
    locale: { value: 'zh-Hans' },
    theme: { value: 'light' },
  }),
}))

import App from './App.vue'

function adminSession(roles: string[], permissions: string[]): PersistedOpsSession {
  return {
    userId: 1,
    shopId: 1,
    username: 'admin',
    accessToken: 'test-token',
    tokenType: 'Bearer',
    expiresAt: '2099-12-31T23:59:59+08:00',
    roles,
    permissions,
  }
}

describe('App review workspace permission gating', () => {
  let wrapper: VueWrapper | null = null

  beforeEach(() => {
    window.history.replaceState(null, '', '/admin?workspace=review')
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    mockSessionRef.current = null
  })

  it('renders review workspace for ADMIN role', async () => {
    mockSessionRef.current = adminSession(['ADMIN'], [])
    wrapper = shallowMount(App)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('admin.reviewWorkspace')
  })

  it('renders review workspace for REVIEW_MANAGEMENT_ADMIN permission', async () => {
    mockSessionRef.current = adminSession([], ['REVIEW_MANAGEMENT_ADMIN'])
    wrapper = shallowMount(App)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('admin.reviewWorkspace')
  })

  it('does not render review workspace for OPS_COMPENSATION_ADMIN alone', async () => {
    mockSessionRef.current = adminSession([], ['OPS_COMPENSATION_ADMIN'])
    wrapper = shallowMount(App)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).not.toContain('admin.reviewWorkspace')
  })
})

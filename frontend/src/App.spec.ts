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

describe('App fulfillment workspace permission gating', () => {
  let wrapper: VueWrapper | null = null

  beforeEach(() => {
    window.history.replaceState(null, '', '/admin?workspace=fulfillment')
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    mockSessionRef.current = null
  })

  it('renders fulfillment workspace for ADMIN role', async () => {
    mockSessionRef.current = adminSession(['ADMIN'], [])
    wrapper = shallowMount(App)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('admin.fulfillmentWorkspace')
  })

  it('renders fulfillment workspace for LOGISTICS_FULFILLMENT_ADMIN permission', async () => {
    mockSessionRef.current = adminSession([], ['LOGISTICS_FULFILLMENT_ADMIN'])
    wrapper = shallowMount(App)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('admin.fulfillmentWorkspace')
  })

  it('does not render fulfillment workspace for OPS_COMPENSATION_ADMIN alone', async () => {
    mockSessionRef.current = adminSession([], ['OPS_COMPENSATION_ADMIN'])
    wrapper = shallowMount(App)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).not.toContain('admin.fulfillmentWorkspace')
  })
})

describe('App order workspace permission gating', () => {
  let wrapper: VueWrapper | null = null

  beforeEach(() => {
    window.history.replaceState(null, '', '/admin?workspace=order')
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    mockSessionRef.current = null
  })

  it('renders order workspace for ADMIN role', async () => {
    mockSessionRef.current = adminSession(['ADMIN'], [])
    wrapper = shallowMount(App)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('admin.orderWorkspace')
  })

  it('renders order workspace for ORDER_MANAGEMENT_ADMIN permission', async () => {
    mockSessionRef.current = adminSession([], ['ORDER_MANAGEMENT_ADMIN'])
    wrapper = shallowMount(App)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('admin.orderWorkspace')
  })

  it('does not render order workspace for OPS_COMPENSATION_ADMIN alone', async () => {
    mockSessionRef.current = adminSession([], ['OPS_COMPENSATION_ADMIN'])
    wrapper = shallowMount(App)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).not.toContain('admin.orderWorkspace')
  })
})

describe('App seckill workspace permission gating', () => {
  let wrapper: VueWrapper | null = null

  beforeEach(() => {
    window.history.replaceState(null, '', '/admin?workspace=seckill')
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    mockSessionRef.current = null
  })

  it('renders seckill workspace for ADMIN role', async () => {
    mockSessionRef.current = adminSession(['ADMIN'], [])
    wrapper = shallowMount(App)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('admin.seckillWorkspace')
  })

  it('renders seckill workspace for SECKILL_ACTIVITY_ADMIN permission', async () => {
    mockSessionRef.current = adminSession([], ['SECKILL_ACTIVITY_ADMIN'])
    wrapper = shallowMount(App)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('admin.seckillWorkspace')
  })

  it('does not render seckill workspace for OPS_COMPENSATION_ADMIN alone', async () => {
    mockSessionRef.current = adminSession([], ['OPS_COMPENSATION_ADMIN'])
    wrapper = shallowMount(App)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).not.toContain('admin.seckillWorkspace')
  })
})

describe('App product workspace permission gating', () => {
  let wrapper: VueWrapper | null = null

  beforeEach(() => {
    window.history.replaceState(null, '', '/admin?workspace=product')
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    mockSessionRef.current = null
  })

  it('renders product workspace for ADMIN role', async () => {
    mockSessionRef.current = adminSession(['ADMIN'], [])
    wrapper = shallowMount(App)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('admin.productWorkspace')
  })

  it('renders product workspace for PRODUCT_CATALOG_ADMIN permission', async () => {
    mockSessionRef.current = adminSession([], ['PRODUCT_CATALOG_ADMIN'])
    wrapper = shallowMount(App)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('admin.productWorkspace')
  })

  it('does not render product workspace for OPS_COMPENSATION_ADMIN alone', async () => {
    mockSessionRef.current = adminSession([], ['OPS_COMPENSATION_ADMIN'])
    wrapper = shallowMount(App)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).not.toContain('admin.productWorkspace')
  })
})

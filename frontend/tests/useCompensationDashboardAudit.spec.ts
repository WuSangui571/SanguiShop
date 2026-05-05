import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createRenderer,
  defineComponent,
  h,
  type Component,
} from 'vue'
import { useCompensationDashboard } from '../src/composables/useCompensationDashboard'
import type { AuditObservabilityConfig } from '../src/views/admin/compensationDashboardModel'

type DashboardComposable = ReturnType<typeof useCompensationDashboard>

interface TestNode {
  type: string
  children: TestNode[]
  parent: TestNode | null
}

vi.mock('../src/services/compensationApi', () => ({
  queryOrderCompensations: vi.fn(async () => createQueryResult()),
  queryPaymentCompensations: vi.fn(async () => createQueryResult()),
  reconcilePaymentManually: vi.fn(async () => createQueryResult()),
  reconcilePaymentsInBulk: vi.fn(async () => createQueryResult()),
  replayOrderTimeoutInBulk: vi.fn(async () => createQueryResult()),
  replayOrderTimeoutManually: vi.fn(async () => createQueryResult()),
}))

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('useCompensationDashboard audit observability actions', () => {
  it('does not open a new window when an audit query link is unavailable', () => {
    const open = stubWindow()
    const dashboard = mountDashboard({})

    dashboard.openAuditQuery('kibanaKql')

    expect(open).not.toHaveBeenCalled()
  })

  it('opens configured audit query links in a noopener tab', () => {
    const open = stubWindow()
    const dashboard = mountDashboard({
      kibanaDiscoverUrl: 'https://kibana.example/app/discover#/',
      lokiExploreUrl: 'https://grafana.example/explore',
    })

    dashboard.openAuditQuery('kibanaKql')
    dashboard.openAuditQuery('lokiLogql')

    expect(open).toHaveBeenNthCalledWith(
      1,
      expect.stringContaining('https://kibana.example/app/discover'),
      '_blank',
      'noopener,noreferrer',
    )
    expect(open).toHaveBeenNthCalledWith(
      2,
      expect.stringContaining('https://grafana.example/explore'),
      '_blank',
      'noopener,noreferrer',
    )
  })
})

function mountDashboard(auditObservabilityConfig: AuditObservabilityConfig): DashboardComposable {
  let dashboard: DashboardComposable | null = null
  const Host = defineComponent({
    setup() {
      dashboard = useCompensationDashboard({ auditObservabilityConfig })
      return () => h('div')
    },
  })

  testRenderer.createApp(Host as Component).mount(createNode('root'))
  if (!dashboard) {
    throw new Error('Dashboard composable was not mounted.')
  }
  return dashboard
}

function stubWindow() {
  const storage = new Map<string, string>()
  const open = vi.fn()

  vi.stubGlobal('window', {
    localStorage: {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => {
        storage.set(key, value)
      },
    },
    location: {
      pathname: '/admin/compensation',
      search: '',
    },
    history: {
      replaceState: vi.fn(),
    },
    open,
  })

  return open
}

const testRenderer = createRenderer<TestNode, TestNode>({
  createElement(type) {
    return createNode(type)
  },
  createText(text) {
    return createNode(`#text:${text}`)
  },
  createComment(text) {
    return createNode(`#comment:${text}`)
  },
  setText(node, text) {
    node.type = `#text:${text}`
  },
  setElementText(node, text) {
    node.children = [createNode(`#text:${text}`)]
  },
  parentNode(node) {
    return node.parent
  },
  nextSibling(node) {
    if (!node.parent) {
      return null
    }
    const index = node.parent.children.indexOf(node)
    return node.parent.children[index + 1] ?? null
  },
  insert(child, parent, anchor) {
    child.parent = parent
    if (!anchor) {
      parent.children.push(child)
      return
    }
    const index = parent.children.indexOf(anchor)
    parent.children.splice(index >= 0 ? index : parent.children.length, 0, child)
  },
  remove(child) {
    if (!child.parent) {
      return
    }
    child.parent.children = child.parent.children.filter((node) => node !== child)
    child.parent = null
  },
  patchProp() {
    // No props are needed for the composable host component.
  },
})

function createNode(type: string): TestNode {
  return {
    type,
    children: [],
    parent: null,
  }
}

function createQueryResult() {
  return {
    data: {
      shopId: 1,
      pageNo: 1,
      pageSize: 20,
      total: 0,
      items: [],
    },
    meta: {
      code: 'OK',
      message: 'ok',
      traceId: 'trace-test',
      timestamp: '2026-05-05T08:00:00Z',
    },
  }
}

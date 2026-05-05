import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  computed,
  createRenderer,
  reactive,
  ref,
  type Component,
} from 'vue'
import type { useCompensationDashboard } from '../src/composables/useCompensationDashboard'
import {
  createDefaultDashboardState,
  type AuditQueryKind,
  type AuditQueryTemplates,
  type DashboardItem,
} from '../src/views/admin/compensationDashboardModel'

type DashboardComposable = ReturnType<typeof useCompensationDashboard>

type TestNodeType = string

interface TestNode {
  type: TestNodeType
  props: Record<string, unknown>
  children: Array<TestNode | string>
  parent: TestNode | null
  text: string
}

const emptyAuditLinks: AuditQueryTemplates = {
  kibanaKql: '',
  kibanaLucene: '',
  lokiLogql: '',
}

const populatedAuditLinks: AuditQueryTemplates = {
  kibanaKql: 'https://kibana.example/app/discover#/?_a=(query:(language:kuery,query:test))',
  kibanaLucene: 'https://kibana.example/app/discover#/?_a=(query:(language:lucene,query:test))',
  lokiLogql: 'https://grafana.example/explore?left=test',
}

afterEach(() => {
  vi.doUnmock('../src/composables/useCompensationDashboard')
  vi.resetModules()
  vi.restoreAllMocks()
})

describe('CompensationDashboardView audit observability actions', () => {
  it('disables open buttons when audit observability links are unavailable', async () => {
    const rendered = await renderDashboardView(emptyAuditLinks)

    const kibanaButtons = findButtonsByText(rendered.root, 'Open in Kibana')
    const lokiButtons = findButtonsByText(rendered.root, 'Open in Loki')

    expect(kibanaButtons).toHaveLength(2)
    expect(lokiButtons).toHaveLength(1)
    expect(kibanaButtons.every(isDisabled)).toBe(true)
    expect(lokiButtons.every(isDisabled)).toBe(true)
  })

  it('opens the expected audit query kind from enabled Kibana and Loki buttons', async () => {
    const rendered = await renderDashboardView(populatedAuditLinks)

    const kibanaButtons = findButtonsByText(rendered.root, 'Open in Kibana')
    const lokiButtons = findButtonsByText(rendered.root, 'Open in Loki')

    expect(kibanaButtons).toHaveLength(2)
    expect(lokiButtons).toHaveLength(1)
    expect(kibanaButtons.every(isDisabled)).toBe(false)
    expect(lokiButtons.every(isDisabled)).toBe(false)

    click(kibanaButtons[0])
    click(kibanaButtons[1])
    click(lokiButtons[0])

    expect(rendered.openAuditQuery).toHaveBeenNthCalledWith(1, 'kibanaKql')
    expect(rendered.openAuditQuery).toHaveBeenNthCalledWith(2, 'kibanaLucene')
    expect(rendered.openAuditQuery).toHaveBeenNthCalledWith(3, 'lokiLogql')
  })
})

async function renderDashboardView(auditQueryLinks: AuditQueryTemplates) {
  const openAuditQuery = vi.fn((_: AuditQueryKind) => undefined)

  vi.doMock('../src/composables/useCompensationDashboard', () => ({
    useCompensationDashboard: () => createDashboardComposable(auditQueryLinks, openAuditQuery),
  }))

  const viewModule = await import('../src/views/admin/CompensationDashboardView.vue')
  const root = createNode('root')
  const app = testRenderer.createApp(viewModule.default as Component)
  app.mount(root)

  return {
    root,
    openAuditQuery,
  }
}

function createDashboardComposable(
  auditQueryLinks: AuditQueryTemplates,
  openAuditQuery: (kind: AuditQueryKind) => void,
): DashboardComposable {
  const defaults = createDefaultDashboardState(new Date('2026-05-05T12:30:00Z'))

  return {
    activeView: ref(defaults.view),
    filters: reactive(defaults.filters),
    replayControls: reactive(defaults.replayControls),
    auditFilters: reactive(defaults.auditFilters),
    auditQueryTemplates: computed(() => ({
      kibanaKql: 'message : "Ops audit event."',
      kibanaLucene: 'message:"Ops audit event."',
      lokiLogql: '{app=~"sangui-.*"} |= "Ops audit event."',
    })),
    auditQueryLinks: computed(() => auditQueryLinks),
    isLoading: ref(false),
    response: ref(null),
    lastMeta: ref(null),
    error: ref(null),
    errorDescription: computed(() => ''),
    actionError: ref(null),
    actionErrorAuditFilters: ref(null),
    actionErrorDescription: computed(() => ''),
    lastAction: ref(null),
    isBulkRunning: ref(false),
    items: computed<DashboardItem[]>(() => []),
    summaryCards: computed(() => []),
    canGoPrev: computed(() => false),
    canGoNext: computed(() => false),
    canRunReplay: computed(() => false),
    isAnyReplayRunning: computed(() => false),
    bulkTargetCount: computed(() => 0),
    submit: vi.fn(async () => undefined),
    reset: vi.fn(async () => undefined),
    setView: vi.fn(async () => undefined),
    goToPage: vi.fn(async () => undefined),
    setPageSize: vi.fn(async () => undefined),
    runManualReplay: vi.fn(async () => undefined),
    runBulkReplay: vi.fn(async () => undefined),
    isManualReplayPending: vi.fn(() => false),
    copyTraceId: vi.fn(async () => undefined),
    isTraceCopied: vi.fn(() => false),
    copyAuditQuery: vi.fn(async () => undefined),
    openAuditQuery,
    copiedAuditQueryKey: ref(null),
    applyAuditTrail: vi.fn(() => undefined),
    exportCurrentPage: vi.fn(() => undefined),
  } as DashboardComposable
}

const testRenderer = createRenderer<TestNode, TestNode>({
  createElement(type) {
    return createNode(type)
  },
  createText(text) {
    const node = createNode('#text')
    node.text = text
    return node
  },
  createComment(text) {
    const node = createNode('#comment')
    node.text = text
    return node
  },
  setText(node, text) {
    node.text = text
  },
  setElementText(node, text) {
    node.children = [text]
  },
  parentNode(node) {
    return node.parent
  },
  nextSibling(node) {
    if (!node.parent) {
      return null
    }
    const index = node.parent.children.indexOf(node)
    const sibling = node.parent.children[index + 1]
    return typeof sibling === 'string' ? null : sibling ?? null
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
    const parent = child.parent
    if (!parent) {
      return
    }
    parent.children = parent.children.filter((node) => node !== child)
    child.parent = null
  },
  patchProp(node, key, _previousValue, nextValue) {
    if (nextValue === null || nextValue === undefined || nextValue === false) {
      delete node.props[key]
      return
    }
    node.props[key] = nextValue
  },
})

function createNode(type: TestNodeType): TestNode {
  return {
    type,
    props: {},
    children: [],
    parent: null,
    text: '',
  }
}

function findButtonsByText(root: TestNode, text: string): TestNode[] {
  const buttons: TestNode[] = []

  walk(root, (node) => {
    if (node.type === 'button' && getText(node).includes(text)) {
      buttons.push(node)
    }
  })

  return buttons
}

function walk(node: TestNode, visit: (node: TestNode) => void) {
  visit(node)
  for (const child of node.children) {
    if (typeof child !== 'string') {
      walk(child, visit)
    }
  }
}

function getText(node: TestNode): string {
  const childText = node.children.map((child) => (typeof child === 'string' ? child : getText(child))).join('')
  return `${node.text}${childText}`
}

function isDisabled(node: TestNode): boolean {
  return node.props.disabled === true
}

function click(node: TestNode) {
  const handler = node.props.onClick
  if (typeof handler !== 'function') {
    throw new Error('Expected button to have a click handler.')
  }
  ;(handler as (event: unknown) => void)({ type: 'click' })
}

import { describe, expect, it } from 'vitest'
import {
  createRenderer,
  type Component,
} from 'vue'
import AuditQueryTemplateCard from '../src/views/admin/components/AuditQueryTemplateCard'
import type { AuditQueryKind } from '../src/views/admin/compensationDashboardModel'

interface TestNode {
  type: string
  props: Record<string, unknown>
  children: Array<TestNode | string>
  parent: TestNode | null
  text: string
}

describe('AuditQueryTemplateCard', () => {
  it('disables the open button when the observability link is unavailable', () => {
    const rendered = renderCard({ link: '' })

    const openButton = findButtonByText(rendered.root, 'Open in Kibana')

    expect(openButton.props.disabled).toBe(true)
    expect(openButton.props.title).toBe('Set VITE_KIBANA_DISCOVER_URL to enable')
    expect(rendered.emitted.open).toEqual([])
  })

  it('emits the audit query kind when an enabled open button is clicked', () => {
    const rendered = renderCard({
      link: 'https://kibana.example/app/discover#/?_a=(query:(language:kuery,query:test))',
    })

    const openButton = findButtonByText(rendered.root, 'Open in Kibana')
    const copyButton = findButtonByText(rendered.root, 'Copy query')

    expect(openButton.props.disabled).toBeUndefined()
    expect(openButton.props.title).toBe('Open in Kibana Discover')

    click(copyButton)
    click(openButton)

    expect(rendered.emitted.copy).toEqual(['kibanaKql'])
    expect(rendered.emitted.open).toEqual(['kibanaKql'])
  })

  it('renders copied state and Loki platform text without changing the emitted kind', () => {
    const rendered = renderCard({
      kind: 'lokiLogql',
      platformLabel: 'Loki',
      title: 'Loki LogQL',
      link: 'https://grafana.example/explore?left=test',
      copied: true,
      enabledTitle: 'Open in Loki Explore',
      disabledTitle: 'Set VITE_LOKI_EXPLORE_URL to enable',
    })

    const copyButton = findButtonByText(rendered.root, 'Copied')
    const openButton = findButtonByText(rendered.root, 'Open in Loki')

    click(copyButton)
    click(openButton)

    expect(rendered.emitted.copy).toEqual(['lokiLogql'])
    expect(rendered.emitted.open).toEqual(['lokiLogql'])
  })
})

function renderCard(options: Partial<CardOptions> = {}) {
  const emitted: Record<'copy' | 'open', AuditQueryKind[]> = {
    copy: [],
    open: [],
  }
  const root = createNode('root')
  const props: CardOptions = {
    title: 'Kibana KQL',
    template: 'message : "Ops audit event."',
    link: '',
    kind: 'kibanaKql',
    platformLabel: 'Kibana',
    copied: false,
    disabledTitle: 'Set VITE_KIBANA_DISCOVER_URL to enable',
    enabledTitle: 'Open in Kibana Discover',
    ...options,
  }

  testRenderer.createApp(AuditQueryTemplateCard as Component, {
    ...props,
    onCopy: (kind: AuditQueryKind) => {
      emitted.copy.push(kind)
    },
    onOpen: (kind: AuditQueryKind) => {
      emitted.open.push(kind)
    },
  }).mount(root)

  return {
    root,
    emitted,
  }
}

interface CardOptions {
  title: string
  template: string
  link: string
  kind: AuditQueryKind
  platformLabel: string
  copied: boolean
  disabledTitle: string
  enabledTitle: string
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

function createNode(type: string): TestNode {
  return {
    type,
    props: {},
    children: [],
    parent: null,
    text: '',
  }
}

function findButtonByText(root: TestNode, text: string): TestNode {
  const button = findButtonsByText(root, text)[0]
  if (!button) {
    throw new Error(`Expected to find button containing "${text}".`)
  }
  return button
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

function click(node: TestNode) {
  const handler = node.props.onClick
  if (typeof handler !== 'function') {
    throw new Error('Expected button to have a click handler.')
  }
  ;(handler as (event: unknown) => void)({ type: 'click' })
}

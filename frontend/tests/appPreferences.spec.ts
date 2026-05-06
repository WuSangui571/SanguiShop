import { nextTick } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createAppPreferences,
  getLocaleLabel,
  isAppLocale,
  isAppTheme,
} from '../src/composables/useAppPreferences'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('app preferences', () => {
  it('defaults to Simplified Chinese and light theme', () => {
    const root = createPreferenceRoot()
    const preferences = createAppPreferences({
      storage: createMemoryStorage(),
      document: { documentElement: root },
    })

    expect(preferences.locale.value).toBe('zh-Hans')
    expect(preferences.theme.value).toBe('light')
    expect(preferences.t('common.signIn')).toBe('登录')
    expect(root.lang).toBe('zh-CN')
    expect(root.dataset.locale).toBe('zh-Hans')
    expect(root.dataset.theme).toBe('light')
    preferences.stop()
  })

  it('persists locale and theme changes and updates the document root', async () => {
    const storage = createMemoryStorage()
    const root = createPreferenceRoot()
    const preferences = createAppPreferences({
      storage,
      document: { documentElement: root },
    })

    preferences.setLocale('en')
    preferences.setTheme('dark')
    await nextTick()

    expect(storage.getItem('sangui.app.locale.v1')).toBe('en')
    expect(storage.getItem('sangui.app.theme.v1')).toBe('dark')
    expect(preferences.t('mall.title')).toBe('Storefront')
    expect(root.lang).toBe('en')
    expect(root.dataset.theme).toBe('dark')
    expect(root.style.setProperty).toHaveBeenLastCalledWith('color-scheme', 'dark')
    preferences.stop()

    const restored = createAppPreferences({
      storage,
      document: { documentElement: createPreferenceRoot() },
    })
    expect(restored.locale.value).toBe('en')
    expect(restored.theme.value).toBe('dark')
    restored.stop()
  })

  it('rejects invalid stored values and formats labels without mojibake', () => {
    const storage = createMemoryStorage()
    storage.setItem('sangui.app.locale.v1', 'zh_CN')
    storage.setItem('sangui.app.theme.v1', 'night')

    const preferences = createAppPreferences({
      storage,
      document: { documentElement: createPreferenceRoot() },
    })

    expect(isAppLocale('zh-Hant')).toBe(true)
    expect(isAppLocale('zh_CN')).toBe(false)
    expect(isAppTheme('dark')).toBe(true)
    expect(isAppTheme('night')).toBe(false)
    expect(getLocaleLabel('zh-Hans')).toBe('简体')
    expect(getLocaleLabel('zh-Hant')).toBe('繁体')
    expect(preferences.locale.value).toBe('zh-Hans')
    expect(preferences.theme.value).toBe('light')
    preferences.stop()
  })
})

function createPreferenceRoot() {
  return {
    lang: '',
    dataset: {} as DOMStringMap,
    style: {
      setProperty: vi.fn(),
    } as unknown as CSSStyleDeclaration,
  }
}

function createMemoryStorage() {
  const data = new Map<string, string>()
  return {
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => {
      data.set(key, value)
    },
    removeItem: (key: string) => {
      data.delete(key)
    },
  }
}

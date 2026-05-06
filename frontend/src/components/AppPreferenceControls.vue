<script setup lang="ts">
import { ref } from 'vue'
import {
  appLocales,
  getLocaleLabel,
  useAppPreferences,
  type AppLocale,
} from '../composables/useAppPreferences'

const preferences = useAppPreferences()
const isLanguageMenuOpen = ref(false)

function toggleLanguageMenu() {
  isLanguageMenuOpen.value = !isLanguageMenuOpen.value
}

function selectLocale(locale: AppLocale) {
  preferences.setLocale(locale)
  isLanguageMenuOpen.value = false
}
</script>

<template>
  <aside class="preference-controls" :aria-label="preferences.t('app.language')">
    <div class="language-menu">
      <button
        type="button"
        class="icon-button"
        :aria-label="preferences.t('app.language')"
        :aria-expanded="isLanguageMenuOpen"
        @click="toggleLanguageMenu"
      >
        <span aria-hidden="true">文</span>
      </button>
      <div v-if="isLanguageMenuOpen" class="language-options" role="menu">
        <button
          v-for="locale in appLocales"
          :key="locale"
          type="button"
          class="language-option"
          role="menuitemradio"
          :aria-checked="preferences.locale.value === locale"
          :data-active="preferences.locale.value === locale"
          @click="selectLocale(locale)"
        >
          {{ getLocaleLabel(locale) }}
        </button>
      </div>
    </div>

    <button
      type="button"
      class="theme-button"
      :aria-label="preferences.t('app.theme')"
      @click="preferences.toggleTheme()"
    >
      <span aria-hidden="true">{{ preferences.theme.value === 'dark' ? 'D' : 'L' }}</span>
      {{ preferences.themeLabel }}
    </button>
  </aside>
</template>

<style scoped>
.preference-controls {
  position: fixed;
  z-index: 20;
  top: 0.9rem;
  right: 0.9rem;
  display: flex;
  gap: 0.5rem;
  align-items: center;
  padding: 0.35rem;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: var(--bg-floating);
  box-shadow: var(--shadow-soft);
  backdrop-filter: blur(16px);
}

.language-menu {
  position: relative;
}

.icon-button,
.theme-button,
.language-option {
  min-height: 2.2rem;
  border: 1px solid var(--border-soft);
  border-radius: 7px;
  background: var(--button-secondary-bg);
  color: var(--text-main);
  font-size: 0.84rem;
  font-weight: 800;
  white-space: nowrap;
}

.icon-button {
  width: 2.2rem;
  padding: 0;
  display: inline-grid;
  place-items: center;
}

.language-options {
  position: absolute;
  top: calc(100% + 0.45rem);
  right: 0;
  min-width: 8rem;
  display: grid;
  gap: 0.25rem;
  padding: 0.35rem;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: var(--bg-floating);
  box-shadow: var(--shadow-soft);
}

.language-option {
  width: 100%;
  padding: 0 0.75rem;
  text-align: left;
}

.language-option[data-active='true'] {
  background: var(--active-bg);
  color: var(--accent-strong);
}

.theme-button {
  display: inline-flex;
  gap: 0.35rem;
  align-items: center;
  padding: 0 0.65rem;
}

@media (max-width: 640px) {
  .preference-controls {
    right: 0.5rem;
    top: 0.5rem;
  }
}
</style>

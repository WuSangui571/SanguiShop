<script setup lang="ts">
import {
  appLocales,
  getLocaleLabel,
  useAppPreferences,
  type AppLocale,
} from '../composables/useAppPreferences'

const preferences = useAppPreferences()

function selectLocale(locale: AppLocale) {
  preferences.setLocale(locale)
}
</script>

<template>
  <aside class="preference-controls" :aria-label="preferences.t('app.language')">
    <div class="language-group" role="group" :aria-label="preferences.t('app.language')">
      <button
        v-for="locale in appLocales"
        :key="locale"
        type="button"
        class="preference-button"
        :data-active="preferences.locale.value === locale"
        @click="selectLocale(locale)"
      >
        {{ getLocaleLabel(locale) }}
      </button>
    </div>
    <button
      type="button"
      class="theme-button"
      :aria-label="preferences.t('app.theme')"
      @click="preferences.toggleTheme()"
    >
      <span aria-hidden="true">{{ preferences.theme.value === 'dark' ? '☾' : '☀' }}</span>
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

.language-group {
  display: flex;
  gap: 0.25rem;
}

.preference-button,
.theme-button {
  min-height: 2.2rem;
  border: 1px solid transparent;
  border-radius: 7px;
  padding: 0 0.65rem;
  background: transparent;
  color: var(--text-muted);
  font-size: 0.84rem;
  font-weight: 800;
  white-space: nowrap;
}

.preference-button[data-active='true'],
.theme-button {
  background: var(--button-secondary-bg);
  color: var(--text-main);
  border-color: var(--border-soft);
}

.theme-button {
  display: inline-flex;
  gap: 0.35rem;
  align-items: center;
}

@media (max-width: 640px) {
  .preference-controls {
    left: 0.5rem;
    right: 0.5rem;
    top: 0.5rem;
    justify-content: space-between;
  }

  .preference-button,
  .theme-button {
    padding: 0 0.45rem;
    font-size: 0.78rem;
  }
}
</style>

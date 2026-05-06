<script setup lang="ts">
import { useAppPreferences } from '../../composables/useAppPreferences'

interface Props {
  username: string
  shopId: number
  message: string
}

defineProps<Props>()
const { t } = useAppPreferences()

const emit = defineEmits<{
  refreshSession: []
  signOut: []
}>()
</script>

<template>
  <section class="forbidden-shell">
    <div class="forbidden-panel">
      <p class="eyebrow">{{ t('ops.forbiddenKicker') }}</p>
      <h1>{{ t('ops.forbiddenTitle') }}</h1>
      <p class="intro">
        {{ t('ops.forbiddenIntro', { username, shopId }) }}
      </p>
      <div class="message">{{ message }}</div>
      <div class="actions">
        <button type="button" class="primary" @click="emit('refreshSession')">{{ t('ops.retryRefresh') }}</button>
        <button type="button" class="secondary" @click="emit('signOut')">{{ t('common.signOut') }}</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.forbidden-shell {
  width: min(1180px, calc(100% - 2rem));
  margin: 0 auto;
  min-height: calc(100vh - 3rem);
  display: grid;
  place-items: center;
}

.forbidden-panel {
  width: min(640px, 100%);
  padding: 1.6rem;
  border-radius: 1.5rem;
  background: var(--bg-panel);
  border: 1px solid var(--danger-border);
  box-shadow: var(--shadow-soft);
}

.eyebrow {
  margin: 0 0 0.35rem;
  font-size: 0.78rem;
  letter-spacing: 0;
  text-transform: uppercase;
  color: var(--warning);
}

h1 {
  margin: 0;
  font-size: clamp(2rem, 4.5vw, 3rem);
  line-height: 0.95;
}

.intro {
  margin: 0.85rem 0 1rem;
  color: var(--text-muted);
}

.message {
  padding: 0.95rem 1rem;
  border-radius: 1rem;
  background: var(--danger-bg);
  color: var(--danger-text);
}

.actions {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
  margin-top: 1rem;
}

.primary,
.secondary {
  min-height: 2.95rem;
  border-radius: 0.95rem;
  padding: 0.75rem 1.05rem;
  font-weight: 700;
  border: 1px solid transparent;
}

.primary {
  background: var(--button-primary-bg);
  color: var(--button-primary-text);
}

.secondary {
  background: var(--button-secondary-bg);
  color: var(--button-secondary-text);
  border-color: var(--border-soft);
}
</style>

<script setup lang="ts">
defineProps<{
  label: string
  value: string
  hint: string
  tone?: 'default' | 'success' | 'warning' | 'danger'
}>()
</script>

<template>
  <article class="card" :data-tone="tone ?? 'default'">
    <p class="label">{{ label }}</p>
    <strong class="value">{{ value }}</strong>
    <p class="hint">{{ hint }}</p>
  </article>
</template>

<style scoped>
.card {
  display: grid;
  gap: 0.4rem;
  padding: 1rem 1.1rem;
  border-radius: 1rem;
  background: var(--card-bg);
  border: 1px solid var(--border-soft);
  box-shadow: var(--shadow-soft);
}

.card[data-tone='success'] {
  background: var(--success-bg);
}

.card[data-tone='warning'] {
  background: var(--warning-bg);
}

.card[data-tone='danger'] {
  background: var(--danger-bg);
}

.label {
  margin: 0;
  font-size: 0.82rem;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0;
}

.value {
  font-size: clamp(1.5rem, 3vw, 2rem);
  line-height: 1;
}

.hint {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.88rem;
}
</style>

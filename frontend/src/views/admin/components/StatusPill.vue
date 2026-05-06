<script setup lang="ts">
const props = defineProps<{
  label: string
  tone?: 'default' | 'success' | 'warning' | 'danger'
}>()
</script>

<template>
  <span class="pill" :data-tone="props.tone ?? 'default'">
    {{ props.label }}
  </span>
</template>

<style scoped>
.pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 1.9rem;
  padding: 0.15rem 0.65rem;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0;
  border: 1px solid var(--border-soft);
  background: var(--chip-bg);
  color: var(--chip-text);
}

.pill[data-tone='success'] {
  background: var(--success-bg);
  color: var(--success-text);
}

.pill[data-tone='warning'] {
  background: var(--warning-bg);
  color: var(--warning-text);
}

.pill[data-tone='danger'] {
  background: var(--danger-bg);
  color: var(--danger-text);
}
</style>

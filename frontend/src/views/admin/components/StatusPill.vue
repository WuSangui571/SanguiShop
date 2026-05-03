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
  letter-spacing: 0.02em;
  border: 1px solid rgba(20, 32, 50, 0.08);
  background: rgba(20, 32, 50, 0.06);
  color: #2f3b50;
}

.pill[data-tone='success'] {
  background: rgba(22, 101, 52, 0.12);
  color: #166534;
}

.pill[data-tone='warning'] {
  background: rgba(194, 65, 12, 0.12);
  color: #c2410c;
}

.pill[data-tone='danger'] {
  background: rgba(180, 35, 24, 0.12);
  color: #b42318;
}
</style>

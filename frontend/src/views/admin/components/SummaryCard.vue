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
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(20, 32, 50, 0.08);
  box-shadow: 0 14px 32px rgba(20, 32, 50, 0.06);
}

.card[data-tone='success'] {
  background: linear-gradient(180deg, rgba(228, 249, 235, 0.96), rgba(255, 255, 255, 0.92));
}

.card[data-tone='warning'] {
  background: linear-gradient(180deg, rgba(255, 241, 232, 0.96), rgba(255, 255, 255, 0.92));
}

.card[data-tone='danger'] {
  background: linear-gradient(180deg, rgba(255, 234, 232, 0.96), rgba(255, 255, 255, 0.92));
}

.label {
  margin: 0;
  font-size: 0.82rem;
  color: #607089;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.value {
  font-size: clamp(1.5rem, 3vw, 2rem);
  line-height: 1;
}

.hint {
  margin: 0;
  color: #475569;
  font-size: 0.88rem;
}
</style>

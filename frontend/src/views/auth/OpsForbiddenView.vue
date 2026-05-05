<script setup lang="ts">
interface Props {
  username: string
  shopId: number
  message: string
}

defineProps<Props>()

const emit = defineEmits<{
  refreshSession: []
  signOut: []
}>()
</script>

<template>
  <section class="forbidden-shell">
    <div class="forbidden-panel">
      <p class="eyebrow">Authenticated but blocked</p>
      <h1>Compensation ops access denied</h1>
      <p class="intro">
        The current session belongs to <strong>{{ username }}</strong> in shop {{ shopId }}, but it no longer has the
        required operations access.
      </p>
      <div class="message">{{ message }}</div>
      <div class="actions">
        <button type="button" class="primary" @click="emit('refreshSession')">Retry refresh</button>
        <button type="button" class="secondary" @click="emit('signOut')">Sign out</button>
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
  border: 1px solid rgba(180, 35, 24, 0.14);
  box-shadow: var(--shadow-soft);
}

.eyebrow {
  margin: 0 0 0.35rem;
  font-size: 0.78rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #b45309;
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
  background: rgba(180, 35, 24, 0.08);
  color: #8d1f17;
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
  background: linear-gradient(135deg, #0f766e, #1d4ed8);
  color: #ffffff;
}

.secondary {
  background: rgba(20, 32, 50, 0.04);
  color: #20324d;
  border-color: rgba(20, 32, 50, 0.08);
}
</style>

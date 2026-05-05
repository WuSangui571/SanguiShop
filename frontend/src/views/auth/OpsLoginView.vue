<script setup lang="ts">
import { reactive } from 'vue'
import type { OpsLoginRequest } from '../../types/api/auth'

interface Props {
  isSubmitting: boolean
  notice: string
  errorMessage: string
}

const props = defineProps<Props>()

const emit = defineEmits<{
  submitted: [payload: OpsLoginRequest]
}>()

const form = reactive({
  shopId: String(import.meta.env.VITE_DEFAULT_SHOP_ID ?? '1'),
  usernameOrMobile: '',
  password: '',
})

function submit() {
  emit('submitted', {
    shopId: Number(form.shopId) || 1,
    usernameOrMobile: form.usernameOrMobile.trim(),
    password: form.password,
  })
}
</script>

<template>
  <section class="login-shell">
    <div class="login-panel">
      <p class="eyebrow">Ops sign-in</p>
      <h1>Compensation operations access</h1>
      <p class="intro">
        Use a configured ops admin account to enter the dashboard directly. No manual browser token injection is required.
      </p>

      <div v-if="notice" class="message info">{{ notice }}</div>
      <div v-if="errorMessage" class="message error">{{ errorMessage }}</div>

      <form class="login-form" @submit.prevent="submit">
        <label>
          <span>Shop ID</span>
          <input v-model="form.shopId" type="number" min="1" inputmode="numeric" placeholder="1" />
        </label>
        <label>
          <span>Username or mobile</span>
          <input v-model="form.usernameOrMobile" autocomplete="username" placeholder="ops-admin" />
        </label>
        <label>
          <span>Password</span>
          <input v-model="form.password" type="password" autocomplete="current-password" placeholder="••••••••" />
        </label>
        <button
          type="submit"
          class="primary"
          :disabled="props.isSubmitting || !form.usernameOrMobile.trim() || !form.password"
        >
          {{ props.isSubmitting ? 'Signing in...' : 'Sign in' }}
        </button>
      </form>
    </div>
  </section>
</template>

<style scoped>
.login-shell {
  width: min(1180px, calc(100% - 2rem));
  margin: 0 auto;
  min-height: calc(100vh - 3rem);
  display: grid;
  place-items: center;
}

.login-panel {
  width: min(520px, 100%);
  padding: 1.6rem;
  border-radius: 1.5rem;
  background: var(--bg-panel);
  border: 1px solid var(--border-soft);
  box-shadow: var(--shadow-soft);
}

.eyebrow {
  margin: 0 0 0.35rem;
  font-size: 0.78rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #0f766e;
}

h1 {
  margin: 0;
  font-size: clamp(2rem, 4.5vw, 3rem);
  line-height: 0.95;
}

.intro {
  margin: 0.85rem 0 1.25rem;
  color: var(--text-muted);
}

.login-form {
  display: grid;
  gap: 0.9rem;
}

label {
  display: grid;
  gap: 0.45rem;
  font-weight: 600;
  color: #334155;
}

label span {
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #607089;
}

input {
  width: 100%;
  min-height: 2.9rem;
  border-radius: 0.85rem;
  border: 1px solid rgba(20, 32, 50, 0.12);
  background: rgba(255, 255, 255, 0.95);
  padding: 0.7rem 0.85rem;
  color: #142032;
}

.primary {
  min-height: 2.95rem;
  border-radius: 0.95rem;
  padding: 0.75rem 1.05rem;
  font-weight: 700;
  border: 1px solid transparent;
  background: linear-gradient(135deg, #0f766e, #1d4ed8);
  color: #ffffff;
}

.primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.message {
  margin-bottom: 1rem;
  padding: 0.95rem 1rem;
  border-radius: 1rem;
}

.message.info {
  background: rgba(29, 78, 216, 0.08);
  color: #1d4ed8;
}

.message.error {
  background: rgba(180, 35, 24, 0.08);
  color: #8d1f17;
}
</style>

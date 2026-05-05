<script setup lang="ts">
import { onMounted } from 'vue'
import { useOpsAuthSession } from './composables/useOpsAuthSession'
import OpsForbiddenView from './views/auth/OpsForbiddenView.vue'
import OpsLoginView from './views/auth/OpsLoginView.vue'
import CompensationDashboardView from './views/admin/CompensationDashboardView.vue'

const {
  state,
  isAuthenticated,
  isForbidden,
  isBooting,
  sessionExpiresLabel,
  bootstrap,
  login,
  refreshSession,
  signOut,
  clearNotice,
} = useOpsAuthSession()

onMounted(() => {
  void bootstrap()
})
</script>

<template>
  <main class="app-shell">
    <section v-if="isBooting" class="center-panel status-panel">
      <p class="eyebrow">Ops access</p>
      <h1>Restoring session</h1>
      <p class="muted">Checking the last compensation operations session.</p>
    </section>

    <section v-else-if="isAuthenticated" class="workspace-shell">
      <header class="workspace-header">
        <div>
          <p class="eyebrow">Compensation ops</p>
          <h1>On-call dashboard</h1>
          <p class="muted">
            Signed in as <strong>{{ state.session?.username }}</strong> for shop {{ state.session?.shopId }}.
            Session expiry target: {{ sessionExpiresLabel }}.
          </p>
        </div>
        <div class="workspace-actions">
          <button type="button" class="secondary" :disabled="state.isRefreshing" @click="refreshSession()">
            {{ state.isRefreshing ? 'Refreshing...' : 'Refresh session' }}
          </button>
          <button type="button" class="secondary" @click="signOut()">
            Sign out
          </button>
        </div>
      </header>

      <div v-if="state.notice" class="notice-banner">
        <span>{{ state.notice }}</span>
        <button type="button" class="ghost-button" @click="clearNotice">Dismiss</button>
      </div>

      <CompensationDashboardView />
    </section>

    <OpsForbiddenView
      v-else-if="isForbidden"
      :username="state.session?.username ?? '--'"
      :shop-id="state.session?.shopId ?? 0"
      :message="state.error?.message ?? 'Current session is authenticated but no longer allowed to use compensation operations.'"
      @refresh-session="refreshSession()"
      @sign-out="signOut()"
    />

    <OpsLoginView
      v-else
      :is-submitting="state.isSubmitting"
      :notice="state.notice"
      :error-message="state.error?.message ?? ''"
      @submitted="login"
    />
  </main>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  padding: 1.25rem 0 2rem;
}

.center-panel,
.workspace-header,
.notice-banner {
  width: min(1180px, calc(100% - 2rem));
  margin: 0 auto;
}

.status-panel {
  margin-top: 6rem;
  padding: 2rem;
  border-radius: 1.5rem;
  background: var(--bg-panel);
  border: 1px solid var(--border-soft);
  box-shadow: var(--shadow-soft);
}

.workspace-shell {
  display: grid;
  gap: 1rem;
}

.workspace-header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: end;
}

.workspace-actions {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
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

.muted {
  margin: 0.6rem 0 0;
  color: var(--text-muted);
}

.secondary,
.ghost-button {
  min-height: 2.85rem;
  border-radius: 0.95rem;
  padding: 0.75rem 1.05rem;
  font-weight: 700;
  border: 1px solid rgba(20, 32, 50, 0.08);
  background: rgba(20, 32, 50, 0.04);
  color: #20324d;
}

.secondary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.notice-banner {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: center;
  padding: 0.95rem 1rem;
  border-radius: 1rem;
  background: rgba(29, 78, 216, 0.08);
  color: #1d4ed8;
}

.ghost-button {
  min-height: auto;
  padding: 0.45rem 0.8rem;
  background: transparent;
}

@media (max-width: 860px) {
  .workspace-header,
  .notice-banner {
    display: grid;
  }
}
</style>

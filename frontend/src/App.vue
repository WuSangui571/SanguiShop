<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import AppPreferenceControls from './components/AppPreferenceControls.vue'
import { useAppPreferences } from './composables/useAppPreferences'
import { useOpsAuthSession } from './composables/useOpsAuthSession'
import OpsForbiddenView from './views/auth/OpsForbiddenView.vue'
import OpsLoginView from './views/auth/OpsLoginView.vue'
import CompensationDashboardView from './views/admin/CompensationDashboardView.vue'
import ProductManagementView from './views/admin/ProductManagementView.vue'
import MallStorefrontView from './views/mall/MallStorefrontView.vue'

const { t } = useAppPreferences()
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

const PRODUCT_CATALOG_ADMIN_PERMISSION = 'PRODUCT_CATALOG_ADMIN'
const OPS_COMPENSATION_ADMIN_PERMISSION = 'OPS_COMPENSATION_ADMIN'
const activeAdminWorkspace = ref<'product' | 'compensation'>('product')

const isAdminSurface = computed(() => {
  if (typeof window === 'undefined') {
    return false
  }

  return window.location.pathname.startsWith('/admin')
})

const canAccessProductWorkspace = computed(() => {
  const session = state.session
  if (!session) {
    return false
  }
  return session.roles.includes('ADMIN') || session.permissions.includes(PRODUCT_CATALOG_ADMIN_PERMISSION)
})

const canAccessCompensationWorkspace = computed(() => {
  const session = state.session
  if (!session) {
    return false
  }
  return session.roles.includes('ADMIN') || session.permissions.includes(OPS_COMPENSATION_ADMIN_PERMISSION)
})

const availableAdminWorkspaces = computed(() => {
  const workspaces: Array<'product' | 'compensation'> = []
  if (canAccessProductWorkspace.value) {
    workspaces.push('product')
  }
  if (canAccessCompensationWorkspace.value) {
    workspaces.push('compensation')
  }
  return workspaces
})

watch(
  availableAdminWorkspaces,
  (workspaces) => {
    if (workspaces.length === 0) {
      return
    }
    if (!workspaces.includes(activeAdminWorkspace.value)) {
      activeAdminWorkspace.value = workspaces[0]
    }
  },
  { immediate: true },
)

function selectWorkspace(workspace: 'product' | 'compensation') {
  activeAdminWorkspace.value = workspace
}

onMounted(() => {
  if (isAdminSurface.value) {
    void bootstrap()
  }
})
</script>

<template>
  <AppPreferenceControls />

  <MallStorefrontView v-if="!isAdminSurface" />

  <main v-else class="app-shell">
    <section v-if="isBooting" class="center-panel status-panel">
      <p class="eyebrow">{{ t('ops.access') }}</p>
      <h1>{{ t('ops.restoringSession') }}</h1>
      <p class="muted">{{ t('ops.restoringDescription') }}</p>
    </section>

    <section v-else-if="isAuthenticated" class="workspace-shell">
      <header class="workspace-header">
        <div>
          <p class="eyebrow">{{ t('ops.compensation') }}</p>
          <h1>{{ t('ops.dashboardTitle') }}</h1>
          <p class="muted">
            {{ t('ops.signedInSummary', {
              username: state.session?.username ?? '--',
              shopId: state.session?.shopId ?? '--',
              expiresAt: sessionExpiresLabel,
            }) }}
          </p>
        </div>
        <div class="workspace-actions">
          <button type="button" class="secondary" :disabled="state.isRefreshing" @click="refreshSession()">
            {{ state.isRefreshing ? t('common.refreshing') : t('ops.refreshSession') }}
          </button>
          <button type="button" class="secondary" @click="signOut()">
            {{ t('common.signOut') }}
          </button>
        </div>
      </header>

      <nav class="workspace-nav" :aria-label="t('admin.workspaceLabel')">
        <button
          v-if="canAccessProductWorkspace"
          type="button"
          class="workspace-tab"
          :class="{ active: activeAdminWorkspace === 'product' }"
          :aria-pressed="activeAdminWorkspace === 'product'"
          @click="selectWorkspace('product')"
        >
          {{ t('admin.productWorkspace') }}
        </button>
        <button
          v-if="canAccessCompensationWorkspace"
          type="button"
          class="workspace-tab"
          :class="{ active: activeAdminWorkspace === 'compensation' }"
          :aria-pressed="activeAdminWorkspace === 'compensation'"
          @click="selectWorkspace('compensation')"
        >
          {{ t('admin.compensationWorkspace') }}
        </button>
      </nav>

      <div v-if="state.notice" class="notice-banner">
        <span>{{ state.notice }}</span>
        <button type="button" class="ghost-button" @click="clearNotice">{{ t('common.dismiss') }}</button>
      </div>

      <ProductManagementView
        v-if="activeAdminWorkspace === 'product' && canAccessProductWorkspace"
        :session="state.session"
        :can-access-product-workspace="canAccessProductWorkspace"
      />
      <CompensationDashboardView v-else />
    </section>

    <OpsForbiddenView
      v-else-if="isForbidden"
      :username="state.session?.username ?? '--'"
      :shop-id="state.session?.shopId ?? 0"
      :message="state.error?.message ?? t('ops.forbiddenFallback')"
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
  padding: 4.5rem 0 2rem;
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

.workspace-nav {
  width: min(1180px, calc(100% - 2rem));
  margin: 0 auto;
  display: inline-flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.workspace-tab {
  min-height: 2.85rem;
  border-radius: 0.95rem;
  padding: 0.75rem 1.05rem;
  font-weight: 700;
  border: 1px solid var(--border-soft);
  background: var(--button-secondary-bg);
  color: var(--button-secondary-text);
}

.workspace-tab.active {
  background: var(--button-primary-bg);
  color: var(--button-primary-text);
}

.eyebrow {
  margin: 0 0 0.35rem;
  font-size: 0.78rem;
  letter-spacing: 0;
  text-transform: uppercase;
  color: var(--accent);
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
  border: 1px solid var(--border-soft);
  background: var(--button-secondary-bg);
  color: var(--button-secondary-text);
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
  background: var(--info-bg);
  color: var(--info-text);
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

  .workspace-nav {
    display: grid;
  }
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import AppPreferenceControls from './components/AppPreferenceControls.vue'
import { useAppPreferences } from './composables/useAppPreferences'
import { useOpsAuthSession } from './composables/useOpsAuthSession'
import OpsForbiddenView from './views/auth/OpsForbiddenView.vue'
import OpsLoginView from './views/auth/OpsLoginView.vue'
import CompensationDashboardView from './views/admin/CompensationDashboardView.vue'
import FulfillmentManagementView from './views/admin/FulfillmentManagementView.vue'
import OrderManagementView from './views/admin/OrderManagementView.vue'
import { readAdminOrderIdFromSearch } from './views/admin/orderManagementModel'
import ProductManagementView from './views/admin/ProductManagementView.vue'
import ReviewManagementView from './views/admin/ReviewManagementView.vue'
import SeckillActivityManagementView from './views/admin/SeckillActivityManagementView.vue'
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
const ORDER_MANAGEMENT_ADMIN_PERMISSION = 'ORDER_MANAGEMENT_ADMIN'
const REVIEW_MANAGEMENT_ADMIN_PERMISSION = 'REVIEW_MANAGEMENT_ADMIN'
const LOGISTICS_FULFILLMENT_ADMIN_PERMISSION = 'LOGISTICS_FULFILLMENT_ADMIN'
const SECKILL_ACTIVITY_ADMIN_PERMISSION = 'SECKILL_ACTIVITY_ADMIN'
const OPS_COMPENSATION_ADMIN_PERMISSION = 'OPS_COMPENSATION_ADMIN'
type AdminWorkspace = 'product' | 'order' | 'review' | 'fulfillment' | 'seckill' | 'compensation'

const activeAdminWorkspace = ref<AdminWorkspace>(readAdminWorkspaceFromLocation() ?? 'product')
const initialAdminOrderId = readInitialAdminOrderId()

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

const canAccessOrderWorkspace = computed(() => {
  const session = state.session
  if (!session) {
    return false
  }
  return session.roles.includes('ADMIN') || session.permissions.includes(ORDER_MANAGEMENT_ADMIN_PERMISSION)
})

const canAccessReviewWorkspace = computed(() => {
  const session = state.session
  if (!session) {
    return false
  }
  return session.roles.includes('ADMIN') || session.permissions.includes(REVIEW_MANAGEMENT_ADMIN_PERMISSION)
})

const canAccessFulfillmentWorkspace = computed(() => {
  const session = state.session
  if (!session) {
    return false
  }
  return session.roles.includes('ADMIN') || session.permissions.includes(LOGISTICS_FULFILLMENT_ADMIN_PERMISSION)
})

const canAccessSeckillWorkspace = computed(() => {
  const session = state.session
  if (!session) {
    return false
  }
  return session.roles.includes('ADMIN') || session.permissions.includes(SECKILL_ACTIVITY_ADMIN_PERMISSION)
})

const availableAdminWorkspaces = computed(() => {
  const workspaces: AdminWorkspace[] = []
  if (canAccessProductWorkspace.value) {
    workspaces.push('product')
  }
  if (canAccessOrderWorkspace.value) {
    workspaces.push('order')
  }
  if (canAccessReviewWorkspace.value) {
    workspaces.push('review')
  }
  if (canAccessFulfillmentWorkspace.value) {
    workspaces.push('fulfillment')
  }
  if (canAccessSeckillWorkspace.value) {
    workspaces.push('seckill')
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

watch(
  activeAdminWorkspace,
  (workspace) => {
    replaceAdminWorkspaceUrl(workspace)
  },
)

function selectWorkspace(workspace: AdminWorkspace) {
  activeAdminWorkspace.value = workspace
}

function readAdminWorkspaceFromLocation(): AdminWorkspace | null {
  if (typeof window === 'undefined' || !window.location.pathname.startsWith('/admin')) {
    return null
  }

  const workspace = new URLSearchParams(window.location.search).get('workspace')
  return workspace === 'product'
    || workspace === 'order'
    || workspace === 'review'
    || workspace === 'fulfillment'
    || workspace === 'seckill'
    || workspace === 'compensation'
    ? workspace
    : null
}

function readInitialAdminOrderId(): number | null {
  if (typeof window === 'undefined' || !window.location.pathname.startsWith('/admin')) {
    return null
  }
  return readAdminOrderIdFromSearch(window.location.search)
}

function replaceAdminWorkspaceUrl(workspace: AdminWorkspace) {
  if (typeof window === 'undefined' || !window.location.pathname.startsWith('/admin')) {
    return
  }

  const url = new URL(window.location.href)
  url.searchParams.set('workspace', workspace)
  if (workspace !== 'order') {
    for (const key of ['orderId', 'status', 'orderNo', 'userId', 'from', 'to', 'page', 'size']) {
      url.searchParams.delete(key)
    }
  }
  window.history.replaceState(null, '', `${url.pathname}${url.search}${url.hash}`)
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
          v-if="canAccessOrderWorkspace"
          type="button"
          class="workspace-tab"
          :class="{ active: activeAdminWorkspace === 'order' }"
          :aria-pressed="activeAdminWorkspace === 'order'"
          @click="selectWorkspace('order')"
        >
          {{ t('admin.orderWorkspace') }}
        </button>
        <button
          v-if="canAccessReviewWorkspace"
          type="button"
          class="workspace-tab"
          :class="{ active: activeAdminWorkspace === 'review' }"
          :aria-pressed="activeAdminWorkspace === 'review'"
          @click="selectWorkspace('review')"
        >
          {{ t('admin.reviewWorkspace') }}
        </button>
        <button
          v-if="canAccessFulfillmentWorkspace"
          type="button"
          class="workspace-tab"
          :class="{ active: activeAdminWorkspace === 'fulfillment' }"
          :aria-pressed="activeAdminWorkspace === 'fulfillment'"
          @click="selectWorkspace('fulfillment')"
        >
          {{ t('admin.fulfillmentWorkspace') }}
        </button>
        <button
          v-if="canAccessSeckillWorkspace"
          type="button"
          class="workspace-tab"
          :class="{ active: activeAdminWorkspace === 'seckill' }"
          :aria-pressed="activeAdminWorkspace === 'seckill'"
          @click="selectWorkspace('seckill')"
        >
          {{ t('admin.seckillWorkspace') }}
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
      <OrderManagementView
        v-else-if="activeAdminWorkspace === 'order' && canAccessOrderWorkspace"
        :session="state.session"
        :can-access-order-workspace="canAccessOrderWorkspace"
        :initial-order-id="initialAdminOrderId"
      />
      <ReviewManagementView
        v-else-if="activeAdminWorkspace === 'review' && canAccessReviewWorkspace"
        :session="state.session"
        :can-access-review-workspace="canAccessReviewWorkspace"
      />
      <FulfillmentManagementView
        v-else-if="activeAdminWorkspace === 'fulfillment' && canAccessFulfillmentWorkspace"
        :session="state.session"
        :can-access-fulfillment-workspace="canAccessFulfillmentWorkspace"
      />
      <SeckillActivityManagementView
        v-else-if="activeAdminWorkspace === 'seckill' && canAccessSeckillWorkspace"
        :session="state.session"
        :can-access-seckill-workspace="canAccessSeckillWorkspace"
      />
      <CompensationDashboardView
        v-else-if="activeAdminWorkspace === 'compensation' && canAccessCompensationWorkspace"
      />
      <OpsForbiddenView
        v-else
        :username="state.session?.username ?? '--'"
        :shop-id="state.session?.shopId ?? 0"
        :message="state.error?.message ?? t('ops.forbiddenFallback')"
        @refresh-session="refreshSession()"
        @sign-out="signOut()"
      />
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

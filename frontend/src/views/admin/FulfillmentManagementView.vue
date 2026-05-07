<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useAppPreferences } from '../../composables/useAppPreferences'
import { useFulfillmentManagement } from '../../composables/useFulfillmentManagement'
import type { PersistedOpsSession } from '../../types/api/auth'
import type { FulfillmentStatus, OrderStatus } from '../../types/api/order'
import { formatDateTime, formatMoney } from '../../utils/format'
import { getAdminOrderStatusLabel } from './orderManagementModel'
import { getFulfillmentStatusLabel } from './fulfillmentManagementModel'

interface Props {
  session: PersistedOpsSession | null
  canAccessFulfillmentWorkspace: boolean
}

const props = defineProps<Props>()
const { t } = useAppPreferences()
const sessionRef = computed(() => props.session)
const canAccessRef = computed(() => props.canAccessFulfillmentWorkspace)

const {
  filters,
  items,
  total,
  totalPages,
  detail,
  selectedItem,
  shipDraft,
  listError,
  detailError,
  actionError,
  isLoadingList,
  isLoadingDetail,
  isActionPending,
  canShipSelected,
  bootstrap,
  refreshList,
  selectFulfillment,
  refreshDetail,
  shipSelectedFulfillment,
  updateFilters,
  goToPage,
  retry,
} = useFulfillmentManagement(sessionRef, canAccessRef)

const fulfillmentOptions = computed(() => [
  { label: t('fulfillmentAdmin.statusAll'), value: 'all' },
  { label: t('fulfillmentAdmin.statusUnshipped'), value: 'unshipped' },
  { label: t('fulfillmentAdmin.statusShipped'), value: 'shipped' },
])

const fulfillmentLabels = computed(() => ({
  unshipped: t('fulfillmentAdmin.statusUnshipped'),
  shipped: t('fulfillmentAdmin.statusShipped'),
}))

const orderStatusLabels = computed(() => ({
  created: t('orderAdmin.statusCreated'),
  paid: t('orderAdmin.statusPaid'),
  cancelled: t('orderAdmin.statusCancelled'),
}))

watch(
  () => props.session,
  () => {
    void bootstrap()
  },
  { immediate: true },
)

onMounted(() => {
  void bootstrap()
})

function fulfillmentLabel(status: FulfillmentStatus): string {
  return getFulfillmentStatusLabel(status, fulfillmentLabels.value)
}

function orderLabel(status: OrderStatus): string {
  if (status === 'shipped') {
    return t('fulfillmentAdmin.orderStatusShipped')
  }
  return getAdminOrderStatusLabel(status, orderStatusLabels.value)
}

function applyFilters() {
  void refreshList(true)
}

function resetFilters() {
  updateFilters({
    status: 'all',
    orderNo: '',
    userId: '',
    fromTime: '',
    toTime: '',
    page: 1,
  })
  void refreshList(true)
}

function onShip() {
  void shipSelectedFulfillment()
}
</script>

<template>
  <main class="fulfillment-shell">
    <section class="hero">
      <div>
        <p class="kicker">{{ t('fulfillmentAdmin.kicker') }}</p>
        <h2>{{ t('fulfillmentAdmin.title') }}</h2>
        <p class="intro">{{ t('fulfillmentAdmin.intro') }}</p>
      </div>
      <button type="button" class="secondary" :disabled="isLoadingList" @click="refreshList(true)">
        {{ isLoadingList ? t('common.loading') : t('fulfillmentAdmin.refreshList') }}
      </button>
    </section>

    <section class="filters">
      <label>
        <span>{{ t('fulfillmentAdmin.statusFilter') }}</span>
        <select :value="filters.status" @change="updateFilters({ status: ($event.target as HTMLSelectElement).value })">
          <option v-for="option in fulfillmentOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
      <label>
        <span>{{ t('orderAdmin.orderNo') }}</span>
        <input :value="filters.orderNo" type="text" @input="updateFilters({ orderNo: ($event.target as HTMLInputElement).value })">
      </label>
      <label>
        <span>{{ t('orderAdmin.userId') }}</span>
        <input :value="filters.userId" type="text" @input="updateFilters({ userId: ($event.target as HTMLInputElement).value })">
      </label>
      <label>
        <span>{{ t('orderAdmin.fromTime') }}</span>
        <input :value="filters.fromTime" type="datetime-local" @input="updateFilters({ fromTime: ($event.target as HTMLInputElement).value })">
      </label>
      <label>
        <span>{{ t('orderAdmin.toTime') }}</span>
        <input :value="filters.toTime" type="datetime-local" @input="updateFilters({ toTime: ($event.target as HTMLInputElement).value })">
      </label>
      <div class="filter-actions">
        <button type="button" class="primary" :disabled="isLoadingList" @click="applyFilters">{{ t('fulfillmentAdmin.search') }}</button>
        <button type="button" class="secondary" @click="resetFilters">{{ t('dashboard.reset') }}</button>
      </div>
    </section>

    <section class="meta-strip">
      <span>{{ t('common.shopId') }} {{ session?.shopId ?? '--' }}</span>
      <span>{{ t('common.usernameOrMobile') }} {{ session?.username ?? '--' }}</span>
      <span>{{ t('fulfillmentAdmin.totalFulfillments', { count: total }) }}</span>
    </section>

    <section v-if="listError" class="banner error">
      <strong>{{ listError.message }}</strong>
      <span>{{ t('common.code') }} {{ listError.code }}</span>
      <span v-if="listError.traceId">{{ t('common.traceId') }} {{ listError.traceId }}</span>
      <button type="button" class="secondary" @click="retry">{{ t('common.retry') }}</button>
    </section>

    <section v-else-if="isLoadingList && items.length === 0" class="banner loading">{{ t('common.loading') }}</section>
    <section v-else-if="items.length === 0" class="banner empty">{{ t('fulfillmentAdmin.listEmpty') }}</section>

    <section class="workspace-grid">
      <aside class="list-panel">
        <div class="panel-head">
          <h3>{{ t('fulfillmentAdmin.listTitle') }}</h3>
          <span>{{ t('orderAdmin.pageLabel', { page: filters.page, total: totalPages }) }}</span>
        </div>
        <div class="list">
          <button
            v-for="item in items"
            :key="item.orderId"
            type="button"
            class="list-item"
            :class="{ active: selectedItem?.orderId === item.orderId }"
            @click="selectFulfillment(item.orderId)"
          >
            <strong>{{ item.orderNo }}</strong>
            <span>{{ fulfillmentLabel(item.fulfillmentStatus) }} / {{ orderLabel(item.status) }}</span>
            <small>{{ t('orderAdmin.userId') }} {{ item.userId }}</small>
            <small>{{ formatMoney(item.totalAmountCent) }}</small>
            <small>{{ item.carrier ?? '--' }} {{ item.trackingNo ?? '' }}</small>
            <small>{{ formatDateTime(item.createdAt) }}</small>
          </button>
        </div>
        <div class="pagination">
          <button type="button" class="secondary" :disabled="filters.page <= 1 || isLoadingList" @click="goToPage(filters.page - 1)">
            {{ t('common.prev') }}
          </button>
          <button type="button" class="secondary" :disabled="filters.page >= totalPages || isLoadingList" @click="goToPage(filters.page + 1)">
            {{ t('common.next') }}
          </button>
        </div>
      </aside>

      <section class="detail-panel">
        <div class="panel-head">
          <div>
            <h3>{{ detail?.orderNo ?? t('fulfillmentAdmin.detailEmptyTitle') }}</h3>
            <p class="meta">{{ detail ? fulfillmentLabel(detail.fulfillmentStatus) : t('fulfillmentAdmin.detailEmptyMeta') }}</p>
          </div>
          <button type="button" class="secondary" :disabled="!detail || isLoadingDetail" @click="refreshDetail">
            {{ isLoadingDetail ? t('common.refreshing') : t('fulfillmentAdmin.refreshDetail') }}
          </button>
        </div>

        <div v-if="detailError" class="banner error">
          <strong>{{ detailError.message }}</strong>
          <span>{{ t('common.code') }} {{ detailError.code }}</span>
          <span v-if="detailError.traceId">{{ t('common.traceId') }} {{ detailError.traceId }}</span>
        </div>
        <div v-if="actionError" class="banner error">
          <strong>{{ actionError.message }}</strong>
          <span>{{ t('common.code') }} {{ actionError.code }}</span>
          <span v-if="actionError.traceId">{{ t('common.traceId') }} {{ actionError.traceId }}</span>
        </div>

        <template v-if="detail">
          <div class="summary-grid">
            <div>
              <span>{{ t('orderAdmin.amount') }}</span>
              <strong>{{ formatMoney(detail.totalAmountCent) }}</strong>
            </div>
            <div>
              <span>{{ t('orderAdmin.status') }}</span>
              <strong>{{ orderLabel(detail.status) }}</strong>
            </div>
            <div>
              <span>{{ t('fulfillmentAdmin.fulfillmentStatus') }}</span>
              <strong>{{ fulfillmentLabel(detail.fulfillmentStatus) }}</strong>
            </div>
            <div>
              <span>{{ t('fulfillmentAdmin.shippedAt') }}</span>
              <strong>{{ formatDateTime(detail.shippedAt) }}</strong>
            </div>
            <div>
              <span>{{ t('fulfillmentAdmin.carrier') }}</span>
              <strong>{{ detail.carrier ?? '--' }}</strong>
            </div>
            <div>
              <span>{{ t('fulfillmentAdmin.trackingNo') }}</span>
              <strong>{{ detail.trackingNo ?? '--' }}</strong>
            </div>
            <div>
              <span>{{ t('common.traceId') }}</span>
              <strong>{{ detail.traceId ?? '--' }}</strong>
            </div>
          </div>

          <form class="ship-form" @submit.prevent="onShip">
            <label>
              <span>{{ t('fulfillmentAdmin.carrier') }}</span>
              <input v-model="shipDraft.carrier" type="text" :disabled="!canShipSelected || isActionPending">
            </label>
            <label>
              <span>{{ t('fulfillmentAdmin.trackingNo') }}</span>
              <input v-model="shipDraft.trackingNo" type="text" :disabled="!canShipSelected || isActionPending">
            </label>
            <button type="submit" class="primary" :disabled="!canShipSelected || isActionPending">
              {{ isActionPending ? t('fulfillmentAdmin.shipping') : t('fulfillmentAdmin.shipOrder') }}
            </button>
          </form>
        </template>

        <div v-else class="banner empty">{{ t('fulfillmentAdmin.detailEmpty') }}</div>
      </section>
    </section>
  </main>
</template>

<style scoped>
.fulfillment-shell {
  width: min(1180px, calc(100% - 2rem));
  margin: 0 auto;
  display: grid;
  gap: 1rem;
}

.hero,
.panel-head,
.filter-actions,
.pagination {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: end;
  flex-wrap: wrap;
}

.kicker {
  margin: 0 0 0.35rem;
  font-size: 0.78rem;
  text-transform: uppercase;
  color: var(--accent);
}

h2,
h3 {
  margin: 0;
}

.intro,
.meta,
small {
  color: var(--text-muted);
}

.filters,
.meta-strip,
.summary-grid,
.ship-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 0.75rem;
  align-items: end;
}

label,
.summary-grid div {
  display: grid;
  gap: 0.35rem;
}

label span,
.summary-grid span {
  font-size: 0.78rem;
  text-transform: uppercase;
  color: var(--text-muted);
}

input,
select {
  width: 100%;
  border: 1px solid var(--border-soft);
  border-radius: 0.85rem;
  background: var(--bg-panel);
  color: var(--text-main);
  min-height: 2.8rem;
  padding: 0.7rem 0.85rem;
}

.primary,
.secondary {
  min-height: 2.8rem;
  border-radius: 0.85rem;
  padding: 0.7rem 0.95rem;
  font-weight: 700;
  border: 1px solid var(--border-soft);
}

.primary {
  background: var(--button-primary-bg);
  color: var(--button-primary-text);
}

.secondary {
  background: var(--button-secondary-bg);
  color: var(--button-secondary-text);
}

button:disabled,
input:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.banner {
  display: grid;
  gap: 0.35rem;
  padding: 0.95rem 1rem;
  border-radius: 1rem;
  border: 1px solid var(--border-soft);
}

.banner.error {
  background: var(--danger-bg);
  color: var(--danger-text);
}

.banner.loading {
  background: var(--info-bg);
  color: var(--info-text);
}

.banner.empty {
  background: var(--bg-soft);
  color: var(--text-muted);
}

.workspace-grid {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 1rem;
}

.list-panel,
.detail-panel {
  background: var(--bg-panel);
  border: 1px solid var(--border-soft);
  border-radius: 1.25rem;
  padding: 1rem;
}

.list {
  display: grid;
  gap: 0.65rem;
}

.list-item {
  text-align: left;
  display: grid;
  gap: 0.2rem;
  padding: 0.85rem;
  border-radius: 0.9rem;
  border: 1px solid var(--border-soft);
  background: var(--bg-soft);
  color: var(--text-main);
}

.list-item.active {
  border-color: var(--accent);
  background: var(--info-bg);
}

.summary-grid {
  margin-bottom: 1rem;
}

.summary-grid strong,
.list-item small {
  overflow-wrap: anywhere;
}

@media (max-width: 960px) {
  .workspace-grid,
  .hero,
  .panel-head {
    display: grid;
  }

  .workspace-grid {
    grid-template-columns: 1fr;
  }
}
</style>

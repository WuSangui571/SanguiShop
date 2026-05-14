<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useAppPreferences } from '../../composables/useAppPreferences'
import { useOrderManagement } from '../../composables/useOrderManagement'
import type { PersistedOpsSession } from '../../types/api/auth'
import type { OrderStatus } from '../../types/api/order'
import { formatDateTime, formatMoney } from '../../utils/format'
import {
  ADMIN_ORDER_FILTER_STORAGE_KEY,
  buildAdminOrderSearchParams,
  createDefaultOrderFilters,
  deriveAdminOrderTimeline,
  deserializeAdminOrderFilters,
  getAdminOrderStatusLabel,
  readAdminOrderFiltersFromSearch,
  serializeAdminOrderFilters,
  type AdminOrderFilterDraft,
} from './orderManagementModel'

interface Props {
  session: PersistedOpsSession | null
  canAccessOrderWorkspace: boolean
  initialOrderId?: number | null
}

const props = defineProps<Props>()
const { t } = useAppPreferences()
const sessionRef = computed(() => props.session)
const canAccessRef = computed(() => props.canAccessOrderWorkspace)
const showCancelConfirm = ref(false)

const {
  filters,
  items,
  total,
  totalPages,
  detail,
  payment,
  selectedItem,
  listError,
  detailError,
  paymentError,
  actionError,
  isLoadingList,
  isLoadingDetail,
  isRefreshingPayment,
  isActionPending,
  canCancelSelectedOrder,
  bootstrap,
  refreshList,
  selectOrder,
  refreshDetail,
  refreshPaymentStatus,
  cancelSelectedOrder,
  updateFilters,
  goToPage,
  retry,
} = useOrderManagement(sessionRef, canAccessRef, {
  initialFilters: readInitialOrderFilters(),
  initialOrderId: props.initialOrderId ?? null,
})

const statusOptions = computed(() => [
  { label: t('orderAdmin.statusAll'), value: 'all' },
  { label: t('orderAdmin.statusCreated'), value: 'created' },
  { label: t('orderAdmin.statusPaid'), value: 'paid' },
  { label: t('orderAdmin.statusCancelled'), value: 'cancelled' },
  { label: t('orderAdmin.statusShipped'), value: 'shipped' },
  { label: t('orderAdmin.statusCompleted'), value: 'completed' },
])

const statusLabels = computed(() => ({
  created: t('orderAdmin.statusCreated'),
  paid: t('orderAdmin.statusPaid'),
  cancelled: t('orderAdmin.statusCancelled'),
  shipped: t('orderAdmin.statusShipped'),
  completed: t('orderAdmin.statusCompleted'),
}))

const timelineLabels = computed(() => ({
  created: t('orderAdmin.timelineCreatedDescription'),
  paid: t('orderAdmin.timelinePaidDescription'),
  cancelled: t('orderAdmin.timelineCancelledDescription'),
  shipped: t('orderAdmin.timelineShippedDescription'),
  completed: t('orderAdmin.timelineCompletedDescription'),
  unknown: t('orderAdmin.timelineUnknownDescription'),
}))

const timelineEntries = computed(() => (
  detail.value
    ? deriveAdminOrderTimeline(detail.value.statusTimeline, statusLabels.value, timelineLabels.value)
    : []
))

watch(
  () => props.session,
  () => {
    void bootstrap()
  },
  { immediate: true },
)

watch(
  filters,
  () => {
    persistOrderFilters(filters.value)
    replaceOrderSearchUrl(filters.value, detail.value?.orderId ?? props.initialOrderId ?? null)
  },
  { deep: true },
)

watch(
  () => detail.value?.orderId,
  (orderId) => {
    replaceOrderSearchUrl(filters.value, orderId ?? null)
  },
)

function statusLabel(status: OrderStatus): string {
  return getAdminOrderStatusLabel(status, statusLabels.value)
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

function onSelectOrder(orderId: number) {
  void selectOrder(orderId)
}

function onRefreshDetail() {
  void refreshDetail()
}

function onRefreshPayment() {
  void refreshPaymentStatus()
}

function onCancelOrder() {
  showCancelConfirm.value = true
}

async function confirmCancelOrder() {
  const cancelled = await cancelSelectedOrder()
  if (cancelled) {
    showCancelConfirm.value = false
  }
}

function dismissCancelConfirm() {
  if (!isActionPending.value) {
    showCancelConfirm.value = false
  }
}

function readInitialOrderFilters(): AdminOrderFilterDraft {
  if (typeof window === 'undefined') {
    return createDefaultOrderFilters()
  }

  return readAdminOrderFiltersFromSearch(window.location.search)
    ?? deserializeAdminOrderFilters(readStoredOrderFilters())
    ?? createDefaultOrderFilters()
}

function readStoredOrderFilters(): string | null {
  try {
    return window.sessionStorage.getItem(ADMIN_ORDER_FILTER_STORAGE_KEY)
  } catch {
    return null
  }
}

function persistOrderFilters(nextFilters: AdminOrderFilterDraft) {
  if (typeof window === 'undefined') {
    return
  }

  try {
    window.sessionStorage.setItem(ADMIN_ORDER_FILTER_STORAGE_KEY, serializeAdminOrderFilters(nextFilters))
  } catch {
    // Filter persistence is best-effort; URL state and in-memory filters still drive the UI.
  }
}

function replaceOrderSearchUrl(nextFilters: AdminOrderFilterDraft, orderId: number | null) {
  if (typeof window === 'undefined' || !window.location.pathname.startsWith('/admin')) {
    return
  }

  const params = buildAdminOrderSearchParams(nextFilters, orderId)
  window.history.replaceState(null, '', `${window.location.pathname}?${params.toString()}${window.location.hash}`)
}
</script>

<template>
  <main class="order-admin-shell">
    <section class="hero">
      <div>
        <p class="kicker">{{ t('orderAdmin.kicker') }}</p>
        <h2>{{ t('orderAdmin.title') }}</h2>
        <p class="intro">{{ t('orderAdmin.intro') }}</p>
      </div>
      <div class="hero-actions">
        <button type="button" class="secondary" :disabled="isLoadingList" @click="refreshList(true)">
          {{ isLoadingList ? t('common.loading') : t('orderAdmin.refreshList') }}
        </button>
      </div>
    </section>

    <section class="filters">
      <label>
        <span>{{ t('orderAdmin.statusFilter') }}</span>
        <select :value="filters.status" @change="updateFilters({ status: ($event.target as HTMLSelectElement).value })">
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
      <label>
        <span>{{ t('orderAdmin.orderNo') }}</span>
        <input :value="filters.orderNo" type="text" @input="updateFilters({ orderNo: ($event.target as HTMLInputElement).value })" />
      </label>
      <label>
        <span>{{ t('orderAdmin.userId') }}</span>
        <input :value="filters.userId" type="text" @input="updateFilters({ userId: ($event.target as HTMLInputElement).value })" />
      </label>
      <label>
        <span>{{ t('orderAdmin.fromTime') }}</span>
        <input :value="filters.fromTime" type="datetime-local" @input="updateFilters({ fromTime: ($event.target as HTMLInputElement).value })" />
      </label>
      <label>
        <span>{{ t('orderAdmin.toTime') }}</span>
        <input :value="filters.toTime" type="datetime-local" @input="updateFilters({ toTime: ($event.target as HTMLInputElement).value })" />
      </label>
      <div class="filter-actions">
        <button type="button" class="primary" :disabled="isLoadingList" @click="applyFilters">
          {{ t('orderAdmin.search') }}
        </button>
        <button type="button" class="secondary" @click="resetFilters">{{ t('dashboard.reset') }}</button>
      </div>
    </section>

    <section class="meta-strip">
      <span>{{ t('common.shopId') }} {{ session?.shopId ?? '--' }}</span>
      <span>{{ t('common.usernameOrMobile') }} {{ session?.username ?? '--' }}</span>
      <span>{{ t('orderAdmin.totalOrders', { count: total }) }}</span>
    </section>

    <section v-if="listError" class="banner error">
      <strong>{{ listError.message }}</strong>
      <span>{{ t('common.code') }} {{ listError.code }}</span>
      <span v-if="listError.traceId">{{ t('common.traceId') }} {{ listError.traceId }}</span>
      <button type="button" class="secondary" @click="retry">{{ t('common.retry') }}</button>
    </section>

    <section v-else-if="isLoadingList && items.length === 0" class="banner loading">
      {{ t('common.loading') }}
    </section>

    <section v-else-if="items.length === 0" class="banner empty">
      {{ t('orderAdmin.listEmpty') }}
    </section>

    <section class="workspace-grid">
      <aside class="list-panel">
        <div class="panel-head">
          <h3>{{ t('orderAdmin.listTitle') }}</h3>
          <span>{{ t('orderAdmin.pageLabel', { page: filters.page, total: totalPages }) }}</span>
        </div>
        <div class="list">
          <button
            v-for="item in items"
            :key="item.orderId"
            type="button"
            class="list-item"
            :class="{ active: selectedItem?.orderId === item.orderId }"
            @click="onSelectOrder(item.orderId)"
          >
            <strong>{{ item.orderNo }}</strong>
            <span>{{ statusLabel(item.status) }}</span>
            <small>{{ t('orderAdmin.userId') }} {{ item.userId }}</small>
            <small>{{ formatMoney(item.totalAmountCent) }} | {{ t('orderAdmin.itemCount') }} {{ item.itemCount }}</small>
            <small>{{ t('orderAdmin.paymentNo') }} {{ item.paymentNo ?? '--' }}</small>
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
            <h3>{{ detail?.orderNo ?? t('orderAdmin.detailEmptyTitle') }}</h3>
            <p class="meta">{{ detail ? statusLabel(detail.status) : t('orderAdmin.detailEmptyMeta') }}</p>
          </div>
          <div class="detail-actions">
            <button type="button" class="secondary" :disabled="!detail || isLoadingDetail" @click="onRefreshDetail">
              {{ isLoadingDetail ? t('common.refreshing') : t('orderAdmin.refreshOrder') }}
            </button>
            <button type="button" class="secondary" :disabled="!detail || isRefreshingPayment" @click="onRefreshPayment">
              {{ isRefreshingPayment ? t('common.refreshing') : t('orderAdmin.refreshPayment') }}
            </button>
            <button
              type="button"
              class="danger"
              :disabled="!canCancelSelectedOrder || isActionPending"
              @click="onCancelOrder"
            >
              {{ isActionPending ? t('orderAdmin.cancelling') : t('orderAdmin.cancelOrder') }}
            </button>
          </div>
        </div>

        <div v-if="detailError" class="banner error">
          <strong>{{ detailError.message }}</strong>
          <span>{{ t('common.code') }} {{ detailError.code }}</span>
          <span v-if="detailError.traceId">{{ t('common.traceId') }} {{ detailError.traceId }}</span>
        </div>
        <div v-if="paymentError" class="banner error">
          <strong>{{ paymentError.message }}</strong>
          <span>{{ t('common.code') }} {{ paymentError.code }}</span>
          <span v-if="paymentError.traceId">{{ t('common.traceId') }} {{ paymentError.traceId }}</span>
        </div>
        <div v-if="actionError" class="banner error">
          <strong>{{ actionError.message }}</strong>
          <span>{{ t('common.code') }} {{ actionError.code }}</span>
          <span v-if="actionError.traceId">{{ t('common.traceId') }} {{ actionError.traceId }}</span>
        </div>

        <template v-if="detail">
          <div class="summary-grid">
            <div>
              <span>{{ t('orderAdmin.orderNo') }}</span>
              <strong>{{ detail.orderNo }}</strong>
            </div>
            <div>
              <span>{{ t('orderAdmin.userId') }}</span>
              <strong>{{ detail.userId }}</strong>
            </div>
            <div>
              <span>{{ t('orderAdmin.amount') }}</span>
              <strong>{{ formatMoney(detail.totalAmountCent) }}</strong>
            </div>
            <div>
              <span>{{ t('orderAdmin.status') }}</span>
              <strong>{{ statusLabel(detail.status) }}</strong>
            </div>
            <div>
              <span>{{ t('orderAdmin.reservationNo') }}</span>
              <strong>{{ detail.reservationNo ?? '--' }}</strong>
            </div>
            <div>
              <span>{{ t('orderAdmin.paymentNo') }}</span>
              <strong>{{ payment?.paymentNo ?? detail.paymentNo ?? '--' }}</strong>
            </div>
            <div>
              <span>{{ t('orderAdmin.paymentStatus') }}</span>
              <strong>{{ payment ? statusLabel(payment.status) : '--' }}</strong>
            </div>
            <div>
              <span>{{ t('common.traceId') }}</span>
              <strong>{{ detail.traceId ?? '--' }}</strong>
            </div>
          </div>

          <div class="section-head">
            <h4>{{ t('orderAdmin.itemsTitle') }}</h4>
          </div>
          <div class="items-table">
            <div class="table-row table-head">
              <span>{{ t('orderAdmin.sku') }}</span>
              <span>{{ t('orderAdmin.quantity') }}</span>
              <span>{{ t('orderAdmin.unitPrice') }}</span>
              <span>{{ t('orderAdmin.lineTotal') }}</span>
            </div>
            <div v-for="item in detail.items" :key="`${item.productId}-${item.skuId}`" class="table-row">
              <span>{{ item.skuName }} / {{ item.skuId }}</span>
              <span>{{ item.quantity }}</span>
              <span>{{ formatMoney(item.priceCent) }}</span>
              <span>{{ formatMoney(item.lineAmountCent) }}</span>
            </div>
          </div>

          <div class="section-head">
            <h4>{{ t('orderAdmin.timelineTitle') }}</h4>
            <p class="section-note">{{ t('orderAdmin.timelineIntro') }}</p>
          </div>
          <div class="timeline">
            <div v-for="entry in timelineEntries" :key="`${entry.status}-${entry.occurredAt}`" class="timeline-item">
              <strong>{{ entry.statusLabel }}</strong>
              <p>{{ entry.description }}</p>
              <span>{{ formatDateTime(entry.occurredAt) }}</span>
              <small>{{ entry.traceId ?? detail.traceId ?? '--' }}</small>
            </div>
          </div>
        </template>

        <div v-else class="banner empty">
          {{ t('orderAdmin.detailEmpty') }}
        </div>
      </section>
    </section>

    <div v-if="showCancelConfirm" class="confirm-backdrop" role="presentation" @click.self="dismissCancelConfirm">
      <section class="confirm-dialog" role="dialog" aria-modal="true" :aria-label="t('orderAdmin.cancelConfirmTitle')">
        <h3>{{ t('orderAdmin.cancelConfirmTitle') }}</h3>
        <p>
          {{ t('orderAdmin.cancelConfirmBody', {
            orderNo: detail?.orderNo ?? '--',
          }) }}
        </p>
        <div class="confirm-actions">
          <button type="button" class="secondary" :disabled="isActionPending" @click="dismissCancelConfirm">
            {{ t('common.dismiss') }}
          </button>
          <button type="button" class="danger" :disabled="isActionPending" @click="confirmCancelOrder">
            {{ isActionPending ? t('orderAdmin.cancelling') : t('orderAdmin.cancelConfirmAction') }}
          </button>
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
.order-admin-shell {
  width: min(1180px, calc(100% - 2rem));
  margin: 0 auto;
  display: grid;
  gap: 1rem;
}

.hero {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: end;
}

.kicker {
  margin: 0 0 0.35rem;
  font-size: 0.78rem;
  text-transform: uppercase;
  color: var(--accent);
}

h2,
h3,
h4 {
  margin: 0;
}

h2 {
  font-size: 1.9rem;
  line-height: 1;
}

.intro,
.meta,
.section-note {
  margin: 0.55rem 0 0;
  color: var(--text-muted);
}

.section-note {
  max-width: 34rem;
  font-size: 0.88rem;
  text-align: right;
}

.hero-actions,
.filter-actions,
.detail-actions,
.pagination {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}

.filters,
.meta-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 0.75rem;
  align-items: end;
}

.meta-strip {
  color: var(--text-muted);
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
.secondary,
.danger {
  min-height: 2.8rem;
  border-radius: 0.85rem;
  padding: 0.7rem 0.95rem;
  font-weight: 700;
  border: 1px solid var(--border-soft);
  white-space: normal;
}

.primary {
  background: var(--button-primary-bg);
  color: var(--button-primary-text);
}

.secondary {
  background: var(--button-secondary-bg);
  color: var(--button-secondary-text);
}

.danger {
  background: var(--danger-bg);
  color: var(--danger-text);
}

button:disabled {
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

.panel-head,
.section-head {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: start;
  margin-bottom: 0.9rem;
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
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0.85rem;
  margin-bottom: 1rem;
}

.summary-grid strong,
.timeline-item small,
.list-item small {
  overflow-wrap: anywhere;
}

.items-table,
.timeline {
  display: grid;
  gap: 0.45rem;
  margin-bottom: 1rem;
}

.table-row {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) 0.5fr 0.8fr 0.8fr;
  gap: 0.75rem;
  padding: 0.7rem;
  border-radius: 0.75rem;
  background: var(--bg-soft);
}

.table-head {
  font-size: 0.78rem;
  text-transform: uppercase;
  color: var(--text-muted);
}

.timeline-item {
  display: grid;
  gap: 0.2rem;
  padding: 0.75rem;
  border-left: 3px solid var(--accent);
  background: var(--bg-soft);
  border-radius: 0.75rem;
}

.timeline-item p {
  margin: 0;
  color: var(--text-main);
}

small {
  color: var(--text-muted);
}

.confirm-backdrop {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: grid;
  place-items: center;
  padding: 1rem;
  background: var(--overlay-bg);
}

.confirm-dialog {
  width: min(420px, 100%);
  display: grid;
  gap: 1rem;
  padding: 1rem;
  border: 1px solid var(--border-soft);
  border-radius: 0.75rem;
  background: var(--bg-panel);
  color: var(--text-main);
  box-shadow: var(--shadow-soft);
}

.confirm-dialog p {
  margin: 0;
  color: var(--text-muted);
}

.confirm-actions {
  display: flex;
  justify-content: end;
  gap: 0.5rem;
  flex-wrap: wrap;
}

@media (max-width: 960px) {
  .hero,
  .workspace-grid,
  .panel-head,
  .section-head {
    display: grid;
  }

  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .section-note {
    text-align: left;
  }
}

@media (max-width: 620px) {
  .order-admin-shell {
    width: min(100% - 1rem, 1180px);
  }

  .hero-actions,
  .filter-actions,
  .detail-actions,
  .pagination,
  .confirm-actions {
    display: grid;
    grid-template-columns: 1fr;
  }

  .primary,
  .secondary,
  .danger {
    width: 100%;
  }

  .list-panel,
  .detail-panel {
    padding: 0.75rem;
    border-radius: 0.75rem;
  }

  .table-row {
    grid-template-columns: 1fr;
  }

  .table-head {
    display: none;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>

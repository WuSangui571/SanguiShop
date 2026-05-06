<script setup lang="ts">
import { computed } from 'vue'
import { useAppPreferences } from '../../composables/useAppPreferences'
import { useCompensationDashboard } from '../../composables/useCompensationDashboard'
import type { AuditQueryKind, DashboardItem } from './compensationDashboardModel'
import {
  auditActionOptions,
  auditOutcomeOptions,
  getAggregateKey,
  getLastTraceId,
  pageSizeOptions,
  resultOptions,
  triggerOptions,
} from './compensationDashboardModel'
import AuditQueryTemplateCard from './components/AuditQueryTemplateCard'
import CompensationAggregateCard from './components/CompensationAggregateCard.vue'
import SummaryCard from './components/SummaryCard.vue'

const {
  activeView,
  filters,
  replayControls,
  auditFilters,
  auditQueryTemplates,
  auditQueryLinks,
  isLoading,
  response,
  lastMeta,
  error,
  errorDescription,
  actionError,
  actionErrorAuditFilters,
  actionErrorDescription,
  lastAction,
  isBulkRunning,
  items,
  summaryCards,
  canGoPrev,
  canGoNext,
  canRunReplay,
  isAnyReplayRunning,
  bulkTargetCount,
  submit,
  reset,
  setView,
  goToPage,
  setPageSize,
  runManualReplay,
  runBulkReplay,
  isManualReplayPending,
  copyTraceId,
  isTraceCopied,
  copyAuditQuery,
  openAuditQuery,
  copiedAuditQueryKey,
  applyAuditTrail,
  exportCurrentPage,
} = useCompensationDashboard()
const { t } = useAppPreferences()

const pageLabel = computed(() => {
  const current = response.value
  if (!current) {
    return t('dashboard.pageLabel', { page: 0, total: 0 })
  }

  const totalPages = Math.max(1, Math.ceil(current.total / current.pageSize))
  return t('dashboard.pageLabel', { page: current.pageNo, total: totalPages })
})

function previousPage() {
  if (!response.value || !canGoPrev.value) {
    return
  }

  void goToPage(response.value.pageNo - 1)
}

function nextPage() {
  if (!response.value || !canGoNext.value) {
    return
  }

  void goToPage(response.value.pageNo + 1)
}

function onSubmit() {
  void submit()
}

function onReset() {
  void reset()
}

function onRunBulkReplay() {
  void runBulkReplay()
}

function selectView(view: 'order' | 'payment') {
  void setView(view)
}

function changePageSize(event: Event) {
  const target = event.target as HTMLSelectElement
  void setPageSize(Number(target.value))
}

function handleManualReplay(item: DashboardItem) {
  void runManualReplay(item)
}

function handleCopyTrace(item: DashboardItem) {
  void copyTraceId(item)
}

function handleExport() {
  exportCurrentPage()
}

function handleCopyAuditQuery(kind: AuditQueryKind) {
  void copyAuditQuery(kind)
}

function handleOpenAuditQuery(kind: AuditQueryKind) {
  openAuditQuery(kind)
}

function handleApplyAuditTrail(nextFilters: Parameters<typeof applyAuditTrail>[0]) {
  applyAuditTrail(nextFilters)
}

function triggerOptionLabel(value: string): string {
  if (value === 'manual') {
    return t('dashboard.manual')
  }
  if (value === 'scheduler') {
    return t('dashboard.scheduler')
  }
  return t('dashboard.allTriggers')
}

function resultOptionLabel(value: string): string {
  if (value === 'failed') {
    return t('dashboard.failed')
  }
  if (value === 'skipped') {
    return t('dashboard.skipped')
  }
  if (value === 'cancelled') {
    return t('dashboard.cancelled')
  }
  if (value === 'settled') {
    return t('dashboard.settled')
  }
  return t('dashboard.allResults')
}

function auditActionOptionLabel(value: string): string {
  const labels: Record<string, ReturnType<typeof t>> = {
    'ops.auth.login': t('dashboard.opsLogin'),
    'ops.auth.refresh': t('dashboard.opsRefresh'),
    'ops.order.compensation.query': t('dashboard.orderQuery'),
    'ops.order.timeout-replay.manual': t('dashboard.orderManualReplay'),
    'ops.order.timeout-replay.bulk': t('dashboard.orderBulkReplay'),
    'ops.payment.compensation.query': t('dashboard.paymentQuery'),
    'ops.payment.reconcile.manual': t('dashboard.paymentManualReconcile'),
    'ops.payment.reconcile.bulk': t('dashboard.paymentBulkReconcile'),
  }
  return labels[value] ?? t('dashboard.allAuditActions')
}

function auditOutcomeOptionLabel(value: string): string {
  if (value === 'success') {
    return t('dashboard.success')
  }
  if (value === 'failed') {
    return t('dashboard.failed')
  }
  if (value === 'denied') {
    return t('dashboard.denied')
  }
  return t('dashboard.allOutcomes')
}
</script>

<template>
  <main class="dashboard-shell">
    <section class="hero">
      <div>
        <p class="kicker">{{ t('dashboard.kicker') }}</p>
        <h1>{{ t('dashboard.title') }}</h1>
        <p class="intro">
          {{ t('dashboard.intro') }}
        </p>
      </div>
      <div class="view-toggle" role="tablist" :aria-label="t('dashboard.viewLabel')">
        <button
          type="button"
          class="toggle-button"
          :data-active="activeView === 'payment'"
          @click="selectView('payment')"
        >
          {{ t('dashboard.payment') }}
        </button>
        <button
          type="button"
          class="toggle-button"
          :data-active="activeView === 'order'"
          @click="selectView('order')"
        >
          {{ t('dashboard.order') }}
        </button>
      </div>
    </section>

    <section class="panel filters-panel">
      <form class="filters-grid" @submit.prevent="onSubmit">
        <label>
          <span>{{ t('common.shopId') }}</span>
          <input v-model="filters.shopId" type="number" min="1" inputmode="numeric" placeholder="1" />
        </label>
        <label>
          <span>{{ t('dashboard.orderId') }}</span>
          <input v-model="filters.orderId" type="number" min="1" inputmode="numeric" placeholder="101" />
        </label>
        <label v-if="activeView === 'payment'">
          <span>{{ t('dashboard.paymentNo') }}</span>
          <input v-model="filters.paymentNo" placeholder="PAY-001" />
        </label>
        <label>
          <span>{{ t('dashboard.trigger') }}</span>
          <select v-model="filters.trigger">
            <option v-for="option in triggerOptions" :key="option.label" :value="option.value">
              {{ triggerOptionLabel(option.value) }}
            </option>
          </select>
        </label>
        <label>
          <span>{{ t('dashboard.result') }}</span>
          <select v-model="filters.result">
            <option v-for="option in resultOptions" :key="option.label" :value="option.value">
              {{ resultOptionLabel(option.value) }}
            </option>
          </select>
        </label>
        <label>
          <span>{{ t('dashboard.operator') }}</span>
          <input v-model="filters.operator" placeholder="ops-user" />
        </label>
        <label>
          <span>{{ t('common.traceId') }}</span>
          <input v-model="filters.traceId" placeholder="trace-manual-payment" />
        </label>
        <label>
          <span>{{ t('dashboard.from') }}</span>
          <input v-model="filters.fromTime" type="datetime-local" />
        </label>
        <label>
          <span>{{ t('dashboard.to') }}</span>
          <input v-model="filters.toTime" type="datetime-local" />
        </label>
        <div class="actions">
          <button type="submit" class="primary" :disabled="isLoading">
            {{ isLoading ? t('common.loading') : t('dashboard.runQuery') }}
          </button>
          <button type="button" class="secondary" :disabled="isLoading" @click="onReset">
            {{ t('dashboard.reset') }}
          </button>
        </div>
      </form>
    </section>

    <section class="panel replay-panel">
      <div class="panel-head compact-head">
        <div>
          <h2>{{ t('dashboard.replayTitle') }}</h2>
          <p class="meta">
            {{ t('dashboard.replayIntro') }}
          </p>
        </div>
        <button type="button" class="secondary" :disabled="items.length === 0" @click="handleExport">
          {{ t('dashboard.exportCurrentPage') }}
        </button>
      </div>
      <div class="replay-grid">
        <label>
          <span>{{ t('dashboard.replayOperator') }}</span>
          <input v-model="replayControls.operator" placeholder="ops-oncall" />
        </label>
        <label>
          <span>{{ t('dashboard.bulkLimit') }}</span>
          <input v-model.number="replayControls.bulkLimit" type="number" min="1" inputmode="numeric" />
        </label>
        <label class="checkbox-field">
          <span>{{ t('dashboard.dryRun') }}</span>
          <input v-model="replayControls.dryRun" type="checkbox" />
        </label>
        <div class="replay-actions">
          <button
            type="button"
            class="primary"
            :disabled="!canRunReplay || isAnyReplayRunning || items.length === 0 || isLoading"
            @click="onRunBulkReplay"
          >
            {{ isBulkRunning ? t('dashboard.bulkRunning') : replayControls.dryRun ? t('dashboard.runBulkDryRun') : t('dashboard.runBulkReplay') }}
          </button>
          <p class="footnote compact-note">
            {{ t('dashboard.bulkScope', { count: bulkTargetCount, view: activeView }) }}
          </p>
        </div>
      </div>
    </section>

    <section class="panel audit-panel" aria-labelledby="audit-search-heading">
      <div class="panel-head compact-head">
        <div>
          <p class="kicker">{{ t('dashboard.auditKicker') }}</p>
          <h2 id="audit-search-heading">{{ t('dashboard.auditTitle') }}</h2>
          <p class="meta">
            {{ t('dashboard.auditIntro') }}
          </p>
        </div>
      </div>
      <div class="audit-grid">
        <label>
          <span>{{ t('dashboard.auditShopId') }}</span>
          <input v-model="auditFilters.shopId" type="number" min="1" inputmode="numeric" placeholder="1" />
        </label>
        <label>
          <span>{{ t('dashboard.auditTraceId') }}</span>
          <input v-model="auditFilters.traceId" placeholder="trace-payment-manual" />
        </label>
        <label>
          <span>{{ t('dashboard.auditOperator') }}</span>
          <input v-model="auditFilters.operator" placeholder="ops-user" />
        </label>
        <label>
          <span>{{ t('dashboard.auditAction') }}</span>
          <select v-model="auditFilters.action">
            <option v-for="option in auditActionOptions" :key="option.label" :value="option.value">
              {{ auditActionOptionLabel(option.value) }}
            </option>
          </select>
        </label>
        <label>
          <span>{{ t('dashboard.auditOutcome') }}</span>
          <select v-model="auditFilters.outcome">
            <option v-for="option in auditOutcomeOptions" :key="option.label" :value="option.value">
              {{ auditOutcomeOptionLabel(option.value) }}
            </option>
          </select>
        </label>
      </div>
      <div class="audit-template-grid">
        <AuditQueryTemplateCard
          title="Kibana KQL"
          :template="auditQueryTemplates.kibanaKql"
          :link="auditQueryLinks.kibanaKql"
          kind="kibanaKql"
          platform-label="Kibana"
          :copied="copiedAuditQueryKey === 'kibanaKql'"
          :enabled-title="t('dashboard.kibanaEnabled')"
          :disabled-title="t('dashboard.kibanaDisabled')"
          @copy="handleCopyAuditQuery"
          @open="handleOpenAuditQuery"
        />
        <AuditQueryTemplateCard
          title="Kibana Lucene"
          :template="auditQueryTemplates.kibanaLucene"
          :link="auditQueryLinks.kibanaLucene"
          kind="kibanaLucene"
          platform-label="Kibana"
          :copied="copiedAuditQueryKey === 'kibanaLucene'"
          :enabled-title="t('dashboard.kibanaEnabled')"
          :disabled-title="t('dashboard.kibanaDisabled')"
          @copy="handleCopyAuditQuery"
          @open="handleOpenAuditQuery"
        />
        <AuditQueryTemplateCard
          title="Loki LogQL"
          :template="auditQueryTemplates.lokiLogql"
          :link="auditQueryLinks.lokiLogql"
          kind="lokiLogql"
          platform-label="Loki"
          :copied="copiedAuditQueryKey === 'lokiLogql'"
          :enabled-title="t('dashboard.lokiEnabled')"
          :disabled-title="t('dashboard.lokiDisabled')"
          @copy="handleCopyAuditQuery"
          @open="handleOpenAuditQuery"
        />
      </div>
    </section>

    <section class="summary-grid">
      <SummaryCard
        v-for="card in summaryCards"
        :key="card.label"
        :label="card.label"
        :value="card.value"
        :hint="card.hint"
        :tone="card.tone"
      />
    </section>

    <section class="panel">
      <div class="panel-head">
        <div>
          <h2>{{ t('dashboard.queryResult') }}</h2>
          <p class="meta">
            <template v-if="lastMeta">
              {{ lastMeta.code }} | trace {{ lastMeta.traceId || '--' }}
            </template>
            <template v-else>
              {{ t('dashboard.noResponse') }}
            </template>
          </p>
        </div>
        <div class="paging">
          <label class="compact-field">
            <span>{{ t('dashboard.pageSize') }}</span>
            <select :value="filters.pageSize" @change="changePageSize">
              <option v-for="size in pageSizeOptions" :key="size" :value="size">
                {{ size }}
              </option>
            </select>
          </label>
          <button type="button" class="secondary" :disabled="!canGoPrev" @click="previousPage">
            {{ t('common.prev') }}
          </button>
          <span class="page-label">{{ pageLabel }}</span>
          <button type="button" class="secondary" :disabled="!canGoNext" @click="nextPage">
            {{ t('common.next') }}
          </button>
        </div>
      </div>

      <div v-if="lastAction" class="message" :class="`message-${lastAction.tone}`">
        <strong>{{ lastAction.title }}</strong>
        <span>{{ lastAction.summary }}</span>
        <span>
          {{ t('common.code') }} {{ lastAction.code }}<template v-if="lastAction.traceId">
            | trace {{ lastAction.traceId }}
          </template>
        </span>
        <span v-for="detail in lastAction.details" :key="detail">{{ detail }}</span>
        <button
          v-if="lastAction.auditFilters"
          type="button"
          class="secondary inline-action"
          @click="handleApplyAuditTrail(lastAction.auditFilters)"
        >
          {{ t('dashboard.viewAuditTrail') }}
        </button>
      </div>

      <div v-if="actionError" class="message error">
        <strong>{{ actionError.message }}</strong>
        <span>{{ actionErrorDescription }}</span>
        <span>
          {{ t('common.code') }} {{ actionError.code }}<template v-if="actionError.traceId">
            | trace {{ actionError.traceId }}
          </template>
        </span>
        <button
          v-if="actionErrorAuditFilters"
          type="button"
          class="secondary inline-action"
          @click="handleApplyAuditTrail(actionErrorAuditFilters)"
        >
          {{ t('dashboard.viewAuditTrail') }}
        </button>
      </div>

      <div v-if="error" class="message error">
        <strong>{{ error.message }}</strong>
        <span>{{ errorDescription }}</span>
        <span>
          {{ t('common.code') }} {{ error.code }}<template v-if="error.traceId">
            | trace {{ error.traceId }}
          </template>
        </span>
      </div>

      <div v-else-if="isLoading" class="message loading">
        {{ t('dashboard.fetching') }}
      </div>

      <div v-else-if="items.length === 0" class="message empty">
        {{ t('dashboard.empty') }}
      </div>

      <div v-else class="results-grid">
        <CompensationAggregateCard
          v-for="item in items"
          :key="getAggregateKey(activeView, item)"
          :view="activeView"
          :item="item"
          :manual-replay-disabled="!canRunReplay || isAnyReplayRunning || isLoading"
          :manual-replay-pending="isManualReplayPending(item)"
          :trace-copied="isTraceCopied(item)"
          @manual-replay="handleManualReplay(item)"
          @copy-trace="handleCopyTrace(item)"
        />
      </div>

      <p
        v-if="!error && !isLoading && items.length > 0"
        class="footnote"
      >
        {{ t('dashboard.lastTraceAnchor') }}
        {{
          getLastTraceId(items[0]) ?? '--'
        }}
      </p>
    </section>
  </main>
</template>

<style scoped>
.dashboard-shell {
  width: min(1180px, calc(100% - 2rem));
  margin: 0 auto;
  padding: 2rem 0 3rem;
}

.hero {
  display: grid;
  gap: 1.25rem;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  margin-bottom: 1.5rem;
}

.kicker {
  margin: 0 0 0.35rem;
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0;
  color: var(--accent);
}

h1 {
  margin: 0;
  font-size: clamp(2rem, 4.6vw, 3.2rem);
  line-height: 0.95;
}

.intro {
  margin: 0.75rem 0 0;
  max-width: 720px;
  color: var(--text-muted);
  font-size: 1rem;
}

.view-toggle {
  display: inline-flex;
  padding: 0.35rem;
  border-radius: 999px;
  background: var(--bg-soft);
  border: 1px solid var(--border-soft);
}

.toggle-button {
  border: 0;
  background: transparent;
  color: var(--button-secondary-text);
  padding: 0.75rem 1.15rem;
  border-radius: 999px;
  font-weight: 700;
}

.toggle-button[data-active='true'] {
  background: var(--button-primary-bg);
  color: var(--button-primary-text);
}

.panel {
  background: var(--bg-panel);
  border: 1px solid var(--border-soft);
  box-shadow: var(--shadow-soft);
  border-radius: 1.35rem;
  padding: 1.2rem;
}

.filters-panel {
  margin-bottom: 1.25rem;
}

.replay-panel {
  margin-bottom: 1.25rem;
}

.audit-panel {
  margin-bottom: 1.25rem;
  background:
    linear-gradient(135deg, var(--bg-soft), var(--info-bg)),
    var(--bg-panel);
}

.filters-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0.9rem;
}

label {
  display: grid;
  gap: 0.45rem;
  font-weight: 600;
  color: var(--label-text);
}

label span {
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0;
  color: var(--text-muted);
}

input,
select,
textarea {
  width: 100%;
  border-radius: 0.85rem;
  border: 1px solid var(--border-strong);
}

input,
select {
  min-height: 2.8rem;
  background: var(--input-bg);
  padding: 0.7rem 0.85rem;
  color: var(--text-main);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.7rem;
  align-items: end;
}

.primary,
.secondary {
  min-height: 2.85rem;
  border-radius: 0.95rem;
  padding: 0.75rem 1.05rem;
  font-weight: 700;
  border: 1px solid transparent;
}

.primary {
  background: var(--button-primary-bg);
  color: var(--button-primary-text);
}

.secondary {
  background: var(--button-secondary-bg);
  color: var(--button-secondary-text);
  border-color: var(--border-soft);
}

.mini-button {
  min-height: 2.25rem;
  padding: 0.45rem 0.75rem;
}

.inline-action {
  justify-self: start;
  min-height: 2.35rem;
  margin-top: 0.25rem;
  padding: 0.5rem 0.8rem;
}

.primary:disabled,
.secondary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 0.95rem;
  margin-bottom: 1.25rem;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
  margin-bottom: 1rem;
}

h2 {
  margin: 0;
  font-size: 1.2rem;
}

.meta {
  margin: 0.35rem 0 0;
  color: var(--text-muted);
}

.paging {
  display: flex;
  gap: 0.7rem;
  align-items: end;
  flex-wrap: wrap;
}

.compact-head {
  margin-bottom: 1rem;
}

.replay-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0.9rem;
  align-items: end;
}

.audit-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 0.9rem;
  margin-bottom: 1rem;
}

.audit-template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 0.9rem;
}

.audit-template-card {
  display: grid;
  gap: 0.65rem;
  padding: 0.9rem;
  border-radius: 1rem;
  background: var(--card-bg);
  border: 1px solid var(--border-soft);
}

.template-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
}

.template-head h3 {
  margin: 0;
  font-size: 0.95rem;
}

.template-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.45rem;
}

textarea {
  min-height: 7rem;
  resize: vertical;
  background: var(--surface-subtle);
  color: var(--text-main);
  padding: 0.8rem;
  font: 0.82rem/1.45 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.replay-actions {
  display: grid;
  gap: 0.45rem;
}

.checkbox-field {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  justify-content: space-between;
}

.checkbox-field input {
  width: auto;
  min-height: auto;
}

.compact-field {
  min-width: 110px;
}

.compact-field span {
  font-size: 0.72rem;
}

.page-label {
  min-width: 5.5rem;
  text-align: center;
  font-weight: 700;
}

.message {
  display: grid;
  gap: 0.35rem;
  padding: 1rem;
  border-radius: 1rem;
  margin-bottom: 0.95rem;
}

.message.loading {
  background: var(--info-bg);
  color: var(--info-text);
}

.message.empty {
  background: var(--bg-soft);
  color: var(--text-muted);
}

.message.error {
  background: var(--danger-bg);
  color: var(--danger-text);
}

.message-success {
  background: var(--success-bg);
  color: var(--success-text);
}

.message-warning {
  background: var(--warning-bg);
  color: var(--warning-text);
}

.message-danger {
  background: var(--danger-bg);
  color: var(--danger-text);
}

.results-grid {
  display: grid;
  gap: 1rem;
}

.footnote {
  margin: 1rem 0 0;
  color: var(--text-muted);
  font-size: 0.88rem;
}

.compact-note {
  margin: 0;
}

@media (max-width: 860px) {
  .hero,
  .panel-head {
    grid-template-columns: 1fr;
    display: grid;
  }
}

@media (max-width: 640px) {
  .dashboard-shell {
    width: min(100% - 1rem, 1180px);
    padding-top: 1rem;
  }

  .panel,
  .aggregate-card {
    padding: 1rem;
  }
}
</style>

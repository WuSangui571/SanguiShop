<script setup lang="ts">
import { computed } from 'vue'
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

const pageLabel = computed(() => {
  const current = response.value
  if (!current) {
    return 'Page 0 / 0'
  }

  const totalPages = Math.max(1, Math.ceil(current.total / current.pageSize))
  return `Page ${current.pageNo} / ${totalPages}`
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
</script>

<template>
  <main class="dashboard-shell">
    <section class="hero">
      <div>
        <p class="kicker">Ops dashboard</p>
        <h1>Compensation history console</h1>
        <p class="intro">
          History-backed compensation records from order and payment services, wired for real filtering,
          paging, and attempt drill-down.
        </p>
      </div>
      <div class="view-toggle" role="tablist" aria-label="Compensation data views">
        <button
          type="button"
          class="toggle-button"
          :data-active="activeView === 'payment'"
          @click="selectView('payment')"
        >
          Payment
        </button>
        <button
          type="button"
          class="toggle-button"
          :data-active="activeView === 'order'"
          @click="selectView('order')"
        >
          Order
        </button>
      </div>
    </section>

    <section class="panel filters-panel">
      <form class="filters-grid" @submit.prevent="onSubmit">
        <label>
          <span>Shop ID</span>
          <input v-model="filters.shopId" type="number" min="1" inputmode="numeric" placeholder="1" />
        </label>
        <label>
          <span>Order ID</span>
          <input v-model="filters.orderId" type="number" min="1" inputmode="numeric" placeholder="101" />
        </label>
        <label v-if="activeView === 'payment'">
          <span>Payment No</span>
          <input v-model="filters.paymentNo" placeholder="PAY-001" />
        </label>
        <label>
          <span>Trigger</span>
          <select v-model="filters.trigger">
            <option v-for="option in triggerOptions" :key="option.label" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label>
          <span>Result</span>
          <select v-model="filters.result">
            <option v-for="option in resultOptions" :key="option.label" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label>
          <span>Operator</span>
          <input v-model="filters.operator" placeholder="ops-user" />
        </label>
        <label>
          <span>Trace ID</span>
          <input v-model="filters.traceId" placeholder="trace-manual-payment" />
        </label>
        <label>
          <span>From</span>
          <input v-model="filters.fromTime" type="datetime-local" />
        </label>
        <label>
          <span>To</span>
          <input v-model="filters.toTime" type="datetime-local" />
        </label>
        <div class="actions">
          <button type="submit" class="primary" :disabled="isLoading">
            {{ isLoading ? 'Loading...' : 'Run query' }}
          </button>
          <button type="button" class="secondary" :disabled="isLoading" @click="onReset">
            Reset
          </button>
        </div>
      </form>
    </section>

    <section class="panel replay-panel">
      <div class="panel-head compact-head">
        <div>
          <h2>Replay controls</h2>
          <p class="meta">
            Manual replay runs per card. Bulk replay uses the current page as explicit bounded scope.
          </p>
        </div>
        <button type="button" class="secondary" :disabled="items.length === 0" @click="handleExport">
          Export current page
        </button>
      </div>
      <div class="replay-grid">
        <label>
          <span>Replay operator</span>
          <input v-model="replayControls.operator" placeholder="ops-oncall" />
        </label>
        <label>
          <span>Bulk limit</span>
          <input v-model.number="replayControls.bulkLimit" type="number" min="1" inputmode="numeric" />
        </label>
        <label class="checkbox-field">
          <span>Dry run</span>
          <input v-model="replayControls.dryRun" type="checkbox" />
        </label>
        <div class="replay-actions">
          <button
            type="button"
            class="primary"
            :disabled="!canRunReplay || isAnyReplayRunning || items.length === 0 || isLoading"
            @click="onRunBulkReplay"
          >
            {{ isBulkRunning ? 'Running bulk replay...' : replayControls.dryRun ? 'Run bulk dry-run' : 'Run bulk replay' }}
          </button>
          <p class="footnote compact-note">
            Current bulk scope: {{ bulkTargetCount }} visible {{ activeView }} record(s).
          </p>
        </div>
      </div>
    </section>

    <section class="panel audit-panel" aria-labelledby="audit-search-heading">
      <div class="panel-head compact-head">
        <div>
          <p class="kicker">Log search</p>
          <h2 id="audit-search-heading">Ops audit search templates</h2>
          <p class="meta">
            These filters target structured `Ops audit event.` logs in Kibana or Loki. They do not query
            compensation history tables.
          </p>
        </div>
      </div>
      <div class="audit-grid">
        <label>
          <span>Audit shop ID</span>
          <input v-model="auditFilters.shopId" type="number" min="1" inputmode="numeric" placeholder="1" />
        </label>
        <label>
          <span>Audit trace ID</span>
          <input v-model="auditFilters.traceId" placeholder="trace-payment-manual" />
        </label>
        <label>
          <span>Audit operator</span>
          <input v-model="auditFilters.operator" placeholder="ops-user" />
        </label>
        <label>
          <span>Audit action</span>
          <select v-model="auditFilters.action">
            <option v-for="option in auditActionOptions" :key="option.label" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label>
          <span>Audit outcome</span>
          <select v-model="auditFilters.outcome">
            <option v-for="option in auditOutcomeOptions" :key="option.label" :value="option.value">
              {{ option.label }}
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
          enabled-title="Open in Kibana Discover"
          disabled-title="Set VITE_KIBANA_DISCOVER_URL to enable"
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
          enabled-title="Open in Kibana Discover"
          disabled-title="Set VITE_KIBANA_DISCOVER_URL to enable"
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
          enabled-title="Open in Loki Explore"
          disabled-title="Set VITE_LOKI_EXPLORE_URL to enable"
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
          <h2>Query result</h2>
          <p class="meta">
            <template v-if="lastMeta">
              {{ lastMeta.code }} | trace {{ lastMeta.traceId || '--' }}
            </template>
            <template v-else>
              No successful response received yet.
            </template>
          </p>
        </div>
        <div class="paging">
          <label class="compact-field">
            <span>Page size</span>
            <select :value="filters.pageSize" @change="changePageSize">
              <option v-for="size in pageSizeOptions" :key="size" :value="size">
                {{ size }}
              </option>
            </select>
          </label>
          <button type="button" class="secondary" :disabled="!canGoPrev" @click="previousPage">
            Prev
          </button>
          <span class="page-label">{{ pageLabel }}</span>
          <button type="button" class="secondary" :disabled="!canGoNext" @click="nextPage">
            Next
          </button>
        </div>
      </div>

      <div v-if="lastAction" class="message" :class="`message-${lastAction.tone}`">
        <strong>{{ lastAction.title }}</strong>
        <span>{{ lastAction.summary }}</span>
        <span>
          code {{ lastAction.code }}<template v-if="lastAction.traceId">
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
          View audit trail
        </button>
      </div>

      <div v-if="actionError" class="message error">
        <strong>{{ actionError.message }}</strong>
        <span>{{ actionErrorDescription }}</span>
        <span>
          code {{ actionError.code }}<template v-if="actionError.traceId">
            | trace {{ actionError.traceId }}
          </template>
        </span>
        <button
          v-if="actionErrorAuditFilters"
          type="button"
          class="secondary inline-action"
          @click="handleApplyAuditTrail(actionErrorAuditFilters)"
        >
          View audit trail
        </button>
      </div>

      <div v-if="error" class="message error">
        <strong>{{ error.message }}</strong>
        <span>{{ errorDescription }}</span>
        <span>
          code {{ error.code }}<template v-if="error.traceId">
            | trace {{ error.traceId }}
          </template>
        </span>
      </div>

      <div v-else-if="isLoading" class="message loading">
        Fetching compensation history records...
      </div>

      <div v-else-if="items.length === 0" class="message empty">
        No compensation aggregate matched the current filter set.
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
        Last visible trace anchor:
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
  letter-spacing: 0.1em;
  color: #0f766e;
}

h1 {
  margin: 0;
  font-size: clamp(2rem, 4.6vw, 3.2rem);
  line-height: 0.95;
}

.intro {
  margin: 0.75rem 0 0;
  max-width: 720px;
  color: #475569;
  font-size: 1rem;
}

.view-toggle {
  display: inline-flex;
  padding: 0.35rem;
  border-radius: 999px;
  background: rgba(20, 32, 50, 0.06);
  border: 1px solid rgba(20, 32, 50, 0.08);
}

.toggle-button {
  border: 0;
  background: transparent;
  color: #3d4f68;
  padding: 0.75rem 1.15rem;
  border-radius: 999px;
  font-weight: 700;
}

.toggle-button[data-active='true'] {
  background: linear-gradient(135deg, #0f766e, #1d4ed8);
  color: #ffffff;
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
    linear-gradient(135deg, rgba(15, 118, 110, 0.08), rgba(29, 78, 216, 0.07)),
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
  color: #334155;
}

label span {
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #607089;
}

input,
select,
textarea {
  width: 100%;
  border-radius: 0.85rem;
  border: 1px solid rgba(20, 32, 50, 0.12);
}

input,
select {
  min-height: 2.8rem;
  background: rgba(255, 255, 255, 0.95);
  padding: 0.7rem 0.85rem;
  color: #142032;
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
  background: linear-gradient(135deg, #0f766e, #1d4ed8);
  color: #ffffff;
}

.secondary {
  background: rgba(20, 32, 50, 0.04);
  color: #20324d;
  border-color: rgba(20, 32, 50, 0.08);
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
  color: #607089;
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
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(20, 32, 50, 0.08);
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
  background: rgba(15, 23, 42, 0.94);
  color: #d8f7ee;
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
  background: rgba(29, 78, 216, 0.08);
  color: #1d4ed8;
}

.message.empty {
  background: rgba(20, 32, 50, 0.05);
  color: #475569;
}

.message.error {
  background: rgba(180, 35, 24, 0.08);
  color: #8d1f17;
}

.message-success {
  background: rgba(15, 118, 110, 0.08);
  color: #115e59;
}

.message-warning {
  background: rgba(180, 112, 24, 0.08);
  color: #9a5a12;
}

.message-danger {
  background: rgba(180, 35, 24, 0.08);
  color: #8d1f17;
}

.results-grid {
  display: grid;
  gap: 1rem;
}

.footnote {
  margin: 1rem 0 0;
  color: #607089;
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

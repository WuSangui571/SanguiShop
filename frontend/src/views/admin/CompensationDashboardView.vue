<script setup lang="ts">
import { computed } from 'vue'
import { useCompensationDashboard } from '../../composables/useCompensationDashboard'
import {
  getAggregateKey,
  getLastTraceId,
  pageSizeOptions,
  resultOptions,
  triggerOptions,
} from './compensationDashboardModel'
import CompensationAggregateCard from './components/CompensationAggregateCard.vue'
import SummaryCard from './components/SummaryCard.vue'

const {
  activeView,
  filters,
  isLoading,
  response,
  lastMeta,
  error,
  items,
  summaryCards,
  canGoPrev,
  canGoNext,
  submit,
  reset,
  setView,
  goToPage,
  setPageSize,
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

function selectView(view: 'order' | 'payment') {
  void setView(view)
}

function changePageSize(event: Event) {
  const target = event.target as HTMLSelectElement
  void setPageSize(Number(target.value))
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

      <div v-if="error" class="message error">
        <strong>{{ error.message }}</strong>
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
select {
  width: 100%;
  min-height: 2.8rem;
  border-radius: 0.85rem;
  border: 1px solid rgba(20, 32, 50, 0.12);
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

.results-grid {
  display: grid;
  gap: 1rem;
}

.footnote {
  margin: 1rem 0 0;
  color: #607089;
  font-size: 0.88rem;
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

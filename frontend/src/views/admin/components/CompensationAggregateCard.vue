<script setup lang="ts">
import { computed } from 'vue'
import { useAppPreferences } from '../../../composables/useAppPreferences'
import type {
  OrderCompensationAggregateResponse,
  PaymentCompensationAggregateResponse,
} from '../../../types/api/compensation'
import { formatDateTime, formatMoney } from '../../../utils/format'
import {
  getLatestResult,
  humanizeCode,
  type CompensationView,
} from '../compensationDashboardModel'
import AttemptTimeline from './AttemptTimeline.vue'
import StatusPill from './StatusPill.vue'

const props = defineProps<{
  view: CompensationView
  item: OrderCompensationAggregateResponse | PaymentCompensationAggregateResponse
  manualReplayDisabled: boolean
  manualReplayPending: boolean
  traceCopied: boolean
}>()
const { t } = useAppPreferences()

const emit = defineEmits<{
  (event: 'manual-replay'): void
  (event: 'copy-trace'): void
}>()

const orderItem = computed(() => (
  props.view === 'order' ? props.item as OrderCompensationAggregateResponse : null
))

const paymentItem = computed(() => (
  props.view === 'payment' ? props.item as PaymentCompensationAggregateResponse : null
))

function toneOf(value: string | null): 'default' | 'success' | 'warning' | 'danger' {
  if (value === 'failed') {
    return 'danger'
  }
  if (value === 'skipped') {
    return 'warning'
  }
  if (value === 'cancelled' || value === 'settled' || value === 'paid') {
    return 'success'
  }

  return 'default'
}

const latestTraceId = computed(() => {
  if (orderItem.value) {
    return orderItem.value.order.lastCompensationTraceId ?? orderItem.value.order.traceId
  }
  if (paymentItem.value) {
    return paymentItem.value.payment.lastCompensationTraceId ?? paymentItem.value.payment.traceId
  }

  return null
})
</script>

<template>
  <article class="aggregate-card">
    <template v-if="orderItem">
      <div class="card-head">
        <div>
          <p class="eyebrow">{{ t('aggregate.orderCompensation') }}</p>
          <h3>{{ orderItem.order.orderNo }}</h3>
        </div>
        <div class="pill-row">
          <StatusPill :label="humanizeCode(orderItem.order.status)" />
          <StatusPill
            :label="humanizeCode(getLatestResult(props.item))"
            :tone="toneOf(getLatestResult(props.item))"
          />
        </div>
      </div>
      <dl class="facts">
        <div>
          <dt>{{ t('aggregate.orderId') }}</dt>
          <dd>{{ orderItem.order.orderId }}</dd>
        </div>
        <div>
          <dt>{{ t('aggregate.userId') }}</dt>
          <dd>{{ orderItem.order.userId }}</dd>
        </div>
        <div>
          <dt>{{ t('aggregate.reservation') }}</dt>
          <dd>{{ orderItem.order.reservationNo ?? '--' }}</dd>
        </div>
        <div>
          <dt>{{ t('aggregate.amount') }}</dt>
          <dd>{{ formatMoney(orderItem.order.totalAmountCent) }}</dd>
        </div>
        <div>
          <dt>{{ t('aggregate.latestTrace') }}</dt>
          <dd>{{ orderItem.order.lastCompensationTraceId ?? '--' }}</dd>
        </div>
        <div>
          <dt>{{ t('aggregate.latestAttempt') }}</dt>
          <dd>{{ formatDateTime(orderItem.latestAttemptAt) }}</dd>
        </div>
      </dl>
      <div class="summary-strip">
        <span>{{ t('aggregate.matchedAttempts', { count: orderItem.matchedAttemptCount }) }}</span>
        <span>{{ t('aggregate.totalAttempts', { count: orderItem.totalAttemptCount }) }}</span>
        <span>{{ t('aggregate.operator', { operator: orderItem.order.lastCompensationOperator ?? '--' }) }}</span>
      </div>
      <div class="action-row">
        <button type="button" class="ghost-button" :disabled="!latestTraceId" @click="emit('copy-trace')">
          {{ traceCopied ? t('aggregate.copiedTrace') : t('aggregate.copyTrace') }}
        </button>
        <button
          type="button"
          class="action-button"
          :disabled="manualReplayDisabled"
          @click="emit('manual-replay')"
        >
          {{ manualReplayPending ? t('aggregate.replaying') : t('aggregate.manualReplay') }}
        </button>
      </div>
      <details class="details">
        <summary>{{ t('aggregate.viewAttemptDetail') }}</summary>
        <AttemptTimeline :attempts="orderItem.attempts" />
      </details>
    </template>

    <template v-else-if="paymentItem">
      <div class="card-head">
        <div>
          <p class="eyebrow">{{ t('aggregate.paymentCompensation') }}</p>
          <h3>{{ paymentItem.payment.paymentNo }}</h3>
        </div>
        <div class="pill-row">
          <StatusPill :label="humanizeCode(paymentItem.payment.status)" />
          <StatusPill
            :label="humanizeCode(getLatestResult(props.item))"
            :tone="toneOf(getLatestResult(props.item))"
          />
        </div>
      </div>
      <dl class="facts">
        <div>
          <dt>{{ t('aggregate.paymentId') }}</dt>
          <dd>{{ paymentItem.payment.paymentId }}</dd>
        </div>
        <div>
          <dt>{{ t('aggregate.orderNo') }}</dt>
          <dd>{{ paymentItem.payment.orderNo }}</dd>
        </div>
        <div>
          <dt>{{ t('aggregate.channel') }}</dt>
          <dd>{{ paymentItem.payment.channel }}</dd>
        </div>
        <div>
          <dt>{{ t('aggregate.amount') }}</dt>
          <dd>{{ formatMoney(paymentItem.payment.amountCent) }}</dd>
        </div>
        <div>
          <dt>{{ t('aggregate.latestTrace') }}</dt>
          <dd>{{ paymentItem.payment.lastCompensationTraceId ?? '--' }}</dd>
        </div>
        <div>
          <dt>{{ t('aggregate.latestAttempt') }}</dt>
          <dd>{{ formatDateTime(paymentItem.latestAttemptAt) }}</dd>
        </div>
      </dl>
      <div class="summary-strip">
        <span>{{ t('aggregate.matchedAttempts', { count: paymentItem.matchedAttemptCount }) }}</span>
        <span>{{ t('aggregate.totalAttempts', { count: paymentItem.totalAttemptCount }) }}</span>
        <span>{{ t('aggregate.operator', { operator: paymentItem.payment.lastCompensationOperator ?? '--' }) }}</span>
      </div>
      <div class="action-row">
        <button type="button" class="ghost-button" :disabled="!latestTraceId" @click="emit('copy-trace')">
          {{ traceCopied ? t('aggregate.copiedTrace') : t('aggregate.copyTrace') }}
        </button>
        <button
          type="button"
          class="action-button"
          :disabled="manualReplayDisabled"
          @click="emit('manual-replay')"
        >
          {{ manualReplayPending ? t('aggregate.replaying') : t('aggregate.manualReplay') }}
        </button>
      </div>
      <details class="details">
        <summary>{{ t('aggregate.viewAttemptDetail') }}</summary>
        <AttemptTimeline :attempts="paymentItem.attempts" />
      </details>
    </template>
  </article>
</template>

<style scoped>
.aggregate-card {
  display: grid;
  gap: 1rem;
  padding: 1.2rem;
  border-radius: 1.15rem;
  background: var(--card-bg);
  border: 1px solid var(--border-soft);
  box-shadow: var(--shadow-soft);
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}

.eyebrow {
  margin: 0 0 0.2rem;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0;
  font-size: 0.76rem;
}

h3 {
  margin: 0;
  font-size: 1.18rem;
}

.pill-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  justify-content: flex-end;
}

.facts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(165px, 1fr));
  gap: 0.85rem;
  margin: 0;
}

dt {
  color: var(--text-muted);
  font-size: 0.76rem;
  text-transform: uppercase;
  letter-spacing: 0;
}

dd {
  margin: 0.22rem 0 0;
  word-break: break-word;
  color: var(--text-main);
}

.summary-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 0.8rem;
  padding: 0.85rem 1rem;
  border-radius: 0.9rem;
  background: var(--success-bg);
  color: var(--success-text);
}

.details {
  display: grid;
  gap: 0.9rem;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.action-button,
.ghost-button {
  min-height: 2.7rem;
  border-radius: 0.9rem;
  padding: 0.7rem 1rem;
  font-weight: 700;
  border: 1px solid var(--border-soft);
}

.action-button {
  background: var(--button-primary-bg);
  color: var(--button-primary-text);
  border-color: transparent;
}

.ghost-button {
  background: var(--button-secondary-bg);
  color: var(--button-secondary-text);
}

.action-button:disabled,
.ghost-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

summary {
  font-weight: 700;
  cursor: pointer;
}

@media (max-width: 720px) {
  .card-head {
    flex-direction: column;
  }

  .pill-row {
    justify-content: flex-start;
  }
}
</style>

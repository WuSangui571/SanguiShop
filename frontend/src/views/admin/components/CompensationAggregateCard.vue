<script setup lang="ts">
import { computed } from 'vue'
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
          <p class="eyebrow">Order compensation</p>
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
          <dt>Order ID</dt>
          <dd>{{ orderItem.order.orderId }}</dd>
        </div>
        <div>
          <dt>User ID</dt>
          <dd>{{ orderItem.order.userId }}</dd>
        </div>
        <div>
          <dt>Reservation</dt>
          <dd>{{ orderItem.order.reservationNo ?? '--' }}</dd>
        </div>
        <div>
          <dt>Amount</dt>
          <dd>{{ formatMoney(orderItem.order.totalAmountCent) }}</dd>
        </div>
        <div>
          <dt>Latest Trace</dt>
          <dd>{{ orderItem.order.lastCompensationTraceId ?? '--' }}</dd>
        </div>
        <div>
          <dt>Latest Attempt</dt>
          <dd>{{ formatDateTime(orderItem.latestAttemptAt) }}</dd>
        </div>
      </dl>
      <div class="summary-strip">
        <span>Matched attempts: {{ orderItem.matchedAttemptCount }}</span>
        <span>Total attempts: {{ orderItem.totalAttemptCount }}</span>
        <span>Operator: {{ orderItem.order.lastCompensationOperator ?? '--' }}</span>
      </div>
      <div class="action-row">
        <button type="button" class="ghost-button" :disabled="!latestTraceId" @click="emit('copy-trace')">
          {{ traceCopied ? 'Copied trace' : 'Copy traceId' }}
        </button>
        <button
          type="button"
          class="action-button"
          :disabled="manualReplayDisabled"
          @click="emit('manual-replay')"
        >
          {{ manualReplayPending ? 'Replaying...' : 'Manual replay' }}
        </button>
      </div>
      <details class="details">
        <summary>View attempt detail</summary>
        <AttemptTimeline :attempts="orderItem.attempts" />
      </details>
    </template>

    <template v-else-if="paymentItem">
      <div class="card-head">
        <div>
          <p class="eyebrow">Payment compensation</p>
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
          <dt>Payment ID</dt>
          <dd>{{ paymentItem.payment.paymentId }}</dd>
        </div>
        <div>
          <dt>Order No</dt>
          <dd>{{ paymentItem.payment.orderNo }}</dd>
        </div>
        <div>
          <dt>Channel</dt>
          <dd>{{ paymentItem.payment.channel }}</dd>
        </div>
        <div>
          <dt>Amount</dt>
          <dd>{{ formatMoney(paymentItem.payment.amountCent) }}</dd>
        </div>
        <div>
          <dt>Latest Trace</dt>
          <dd>{{ paymentItem.payment.lastCompensationTraceId ?? '--' }}</dd>
        </div>
        <div>
          <dt>Latest Attempt</dt>
          <dd>{{ formatDateTime(paymentItem.latestAttemptAt) }}</dd>
        </div>
      </dl>
      <div class="summary-strip">
        <span>Matched attempts: {{ paymentItem.matchedAttemptCount }}</span>
        <span>Total attempts: {{ paymentItem.totalAttemptCount }}</span>
        <span>Operator: {{ paymentItem.payment.lastCompensationOperator ?? '--' }}</span>
      </div>
      <div class="action-row">
        <button type="button" class="ghost-button" :disabled="!latestTraceId" @click="emit('copy-trace')">
          {{ traceCopied ? 'Copied trace' : 'Copy traceId' }}
        </button>
        <button
          type="button"
          class="action-button"
          :disabled="manualReplayDisabled"
          @click="emit('manual-replay')"
        >
          {{ manualReplayPending ? 'Replaying...' : 'Manual replay' }}
        </button>
      </div>
      <details class="details">
        <summary>View attempt detail</summary>
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
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(20, 32, 50, 0.08);
  box-shadow: 0 18px 40px rgba(20, 32, 50, 0.08);
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}

.eyebrow {
  margin: 0 0 0.2rem;
  color: #607089;
  text-transform: uppercase;
  letter-spacing: 0.08em;
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
  color: #607089;
  font-size: 0.76rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

dd {
  margin: 0.22rem 0 0;
  word-break: break-word;
  color: #142032;
}

.summary-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 0.8rem;
  padding: 0.85rem 1rem;
  border-radius: 0.9rem;
  background: rgba(15, 118, 110, 0.07);
  color: #204650;
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
  border: 1px solid rgba(20, 32, 50, 0.08);
}

.action-button {
  background: linear-gradient(135deg, #0f766e, #1d4ed8);
  color: #ffffff;
  border-color: transparent;
}

.ghost-button {
  background: rgba(20, 32, 50, 0.03);
  color: #20324d;
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

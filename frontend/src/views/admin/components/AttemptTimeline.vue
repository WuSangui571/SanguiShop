<script setup lang="ts">
import type {
  OrderCompensationAttemptResponse,
  PaymentCompensationAttemptResponse,
} from '../../../types/api/compensation'
import { formatDateTime } from '../../../utils/format'
import { humanizeCode } from '../compensationDashboardModel'
import StatusPill from './StatusPill.vue'

defineProps<{
  attempts: Array<OrderCompensationAttemptResponse | PaymentCompensationAttemptResponse>
}>()

function toneOf(result: string): 'default' | 'success' | 'warning' | 'danger' {
  if (result === 'failed') {
    return 'danger'
  }
  if (result === 'skipped') {
    return 'warning'
  }
  if (result === 'cancelled' || result === 'settled') {
    return 'success'
  }

  return 'default'
}
</script>

<template>
  <ol class="timeline">
    <li v-for="attempt in attempts" :key="attempt.attemptId" class="timeline-item">
      <div class="timeline-top">
        <StatusPill :label="humanizeCode(attempt.result)" :tone="toneOf(attempt.result)" />
        <span class="attempt-meta">Attempt #{{ attempt.attemptId }}</span>
        <span class="attempt-meta">{{ formatDateTime(attempt.createdAt) }}</span>
      </div>
      <dl class="timeline-grid">
        <div>
          <dt>Trace ID</dt>
          <dd>{{ attempt.traceId ?? '--' }}</dd>
        </div>
        <div>
          <dt>Trigger</dt>
          <dd>{{ humanizeCode(attempt.trigger) }}</dd>
        </div>
        <div>
          <dt>Operator</dt>
          <dd>{{ attempt.operator ?? '--' }}</dd>
        </div>
        <div>
          <dt>Error Code</dt>
          <dd>{{ attempt.errorCode ?? '--' }}</dd>
        </div>
      </dl>
      <p class="reason">{{ attempt.reason ?? 'No sanitized reason was persisted for this attempt.' }}</p>
    </li>
  </ol>
</template>

<style scoped>
.timeline {
  display: grid;
  gap: 0.8rem;
  list-style: none;
  margin: 0;
  padding: 0;
}

.timeline-item {
  display: grid;
  gap: 0.7rem;
  padding: 0.95rem 1rem;
  border-radius: 0.9rem;
  background: rgba(20, 32, 50, 0.03);
  border: 1px solid rgba(20, 32, 50, 0.06);
}

.timeline-top {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem;
  align-items: center;
}

.attempt-meta {
  color: #607089;
  font-size: 0.84rem;
}

.timeline-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 0.75rem;
  margin: 0;
}

dt {
  color: #607089;
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

dd {
  margin: 0.22rem 0 0;
  color: #142032;
  word-break: break-word;
}

.reason {
  margin: 0;
  color: #38475d;
}
</style>

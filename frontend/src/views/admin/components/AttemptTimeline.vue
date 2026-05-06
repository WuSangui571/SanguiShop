<script setup lang="ts">
import type {
  OrderCompensationAttemptResponse,
  PaymentCompensationAttemptResponse,
} from '../../../types/api/compensation'
import { useAppPreferences } from '../../../composables/useAppPreferences'
import { formatDateTime } from '../../../utils/format'
import { humanizeCode } from '../compensationDashboardModel'
import StatusPill from './StatusPill.vue'

defineProps<{
  attempts: Array<OrderCompensationAttemptResponse | PaymentCompensationAttemptResponse>
}>()
const { t } = useAppPreferences()

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
        <span class="attempt-meta">{{ t('timeline.attempt', { id: attempt.attemptId }) }}</span>
        <span class="attempt-meta">{{ formatDateTime(attempt.createdAt) }}</span>
      </div>
      <dl class="timeline-grid">
        <div>
          <dt>{{ t('timeline.traceId') }}</dt>
          <dd>{{ attempt.traceId ?? '--' }}</dd>
        </div>
        <div>
          <dt>{{ t('timeline.trigger') }}</dt>
          <dd>{{ humanizeCode(attempt.trigger) }}</dd>
        </div>
        <div>
          <dt>{{ t('timeline.operator') }}</dt>
          <dd>{{ attempt.operator ?? '--' }}</dd>
        </div>
        <div>
          <dt>{{ t('timeline.errorCode') }}</dt>
          <dd>{{ attempt.errorCode ?? '--' }}</dd>
        </div>
      </dl>
      <p class="reason">{{ attempt.reason ?? t('timeline.noReason') }}</p>
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
  background: var(--surface-subtle);
  border: 1px solid var(--border-soft);
}

.timeline-top {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem;
  align-items: center;
}

.attempt-meta {
  color: var(--text-muted);
  font-size: 0.84rem;
}

.timeline-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 0.75rem;
  margin: 0;
}

dt {
  color: var(--text-muted);
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0;
}

dd {
  margin: 0.22rem 0 0;
  color: var(--text-main);
  word-break: break-word;
}

.reason {
  margin: 0;
  color: var(--text-muted);
}
</style>

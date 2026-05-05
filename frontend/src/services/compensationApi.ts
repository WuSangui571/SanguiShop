import { postJson } from './httpClient'
import type {
  BulkOrderTimeoutReplayRequest,
  BulkOrderTimeoutReplayResponse,
  BulkPaymentReconcileRequest,
  BulkPaymentReconcileResponse,
  ManualOrderTimeoutReplayRequest,
  ManualOrderTimeoutReplayResponse,
  ManualPaymentReconcileRequest,
  ManualPaymentReconcileResponse,
  OrderCompensationQueryRequest,
  OrderCompensationQueryResponse,
  PaymentCompensationQueryRequest,
  PaymentCompensationQueryResponse,
} from '../types/api/compensation'

export function queryOrderCompensations(payload: OrderCompensationQueryRequest) {
  return postJson<OrderCompensationQueryResponse>('/api/internal/orders/compensation-records/query', payload)
}

export function queryPaymentCompensations(payload: PaymentCompensationQueryRequest) {
  return postJson<PaymentCompensationQueryResponse>('/api/internal/payments/compensation-records/query', payload)
}

export function replayOrderTimeoutManually(payload: ManualOrderTimeoutReplayRequest) {
  return postJson<ManualOrderTimeoutReplayResponse>('/api/internal/orders/timeout-replays/manual', payload)
}

export function replayOrderTimeoutInBulk(payload: BulkOrderTimeoutReplayRequest) {
  return postJson<BulkOrderTimeoutReplayResponse>('/api/internal/orders/timeout-replays/bulk', payload)
}

export function reconcilePaymentManually(payload: ManualPaymentReconcileRequest) {
  return postJson<ManualPaymentReconcileResponse>('/api/internal/payments/reconciliations/manual', payload)
}

export function reconcilePaymentsInBulk(payload: BulkPaymentReconcileRequest) {
  return postJson<BulkPaymentReconcileResponse>('/api/internal/payments/reconciliations/bulk', payload)
}

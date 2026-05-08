import { computed, ref, type Ref } from 'vue'
import { listAdminReviews, updateAdminReviewReplyVisibility, updateAdminReviewVisibility, upsertAdminReviewReply } from '../services/orderApi'
import type { PersistedOpsSession } from '../types/api/auth'
import type { AdminReviewSummaryResponse, AdminReviewVisibilityFilter } from '../types/api/order'
import {
  buildAdminReviewQuery,
  buildAdminReviewReplyRequest,
  buildAdminReviewReplyVisibilityRequest,
  buildAdminReviewVisibilityRequest,
  createDefaultReviewFilters,
  createSubmissionGate,
  replaceAdminReviewItem,
  toAdminReviewError,
  type AdminReviewErrorState,
  type AdminReviewFilterDraft,
} from '../views/admin/reviewManagementModel'

interface UseReviewManagementOptions {
  createRequestId?: () => string
  initialFilters?: AdminReviewFilterDraft | null
}

export function useReviewManagement(
  session: Ref<PersistedOpsSession | null>,
  canAccessWorkspace: Ref<boolean>,
  options: UseReviewManagementOptions = {},
) {
  const filters = ref<AdminReviewFilterDraft>(options.initialFilters ?? createDefaultReviewFilters())
  const items = ref<AdminReviewSummaryResponse[]>([])
  const total = ref(0)
  const listError = ref<AdminReviewErrorState | null>(null)
  const actionError = ref<AdminReviewErrorState | null>(null)
  const isLoadingList = ref(false)
  const pendingReviewId = ref<number | null>(null)
  const listGate = createSubmissionGate()
  const actionGate = createSubmissionGate()
  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / Math.max(1, filters.value.size))))
  const isActionPending = computed(() => actionGate.isPending())

  async function bootstrap() {
    if (!canAccessWorkspace.value || !session.value) {
      return
    }
    await refreshList()
  }

  async function refreshList() {
    if (!canAccessWorkspace.value || !listGate.begin()) {
      return
    }
    isLoadingList.value = true
    listError.value = null
    try {
      const result = await listAdminReviews(buildAdminReviewQuery(filters.value))
      items.value = result.data.items
      total.value = result.data.total
      filters.value = {
        ...filters.value,
        page: result.data.page,
        size: result.data.size,
      }
    } catch (caught) {
      listError.value = toAdminReviewError(caught, 'Unable to load admin reviews.')
      items.value = []
      total.value = 0
    } finally {
      isLoadingList.value = false
      listGate.end()
    }
  }

  async function updateVisibility(
    reviewId: number,
    visibility: Exclude<AdminReviewVisibilityFilter, 'all'>,
    reason: string,
  ) {
    if (!canAccessWorkspace.value || !actionGate.begin()) {
      return false
    }
    actionError.value = null
    pendingReviewId.value = reviewId
    const requestId = (options.createRequestId ?? createRequestId)()
    try {
      const result = await updateAdminReviewVisibility(
        reviewId,
        buildAdminReviewVisibilityRequest(visibility, reason, requestId),
      )
      items.value = replaceAdminReviewItem(items.value, result.data)
      return true
    } catch (caught) {
      actionError.value = toAdminReviewError(caught, 'Unable to update review visibility.')
      return false
    } finally {
      pendingReviewId.value = null
      actionGate.end()
    }
  }

  async function saveReply(reviewId: number, content: string) {
    if (!canAccessWorkspace.value || !actionGate.begin()) {
      return false
    }
    actionError.value = null
    pendingReviewId.value = reviewId
    const requestId = (options.createRequestId ?? createRequestId)()
    try {
      const result = await upsertAdminReviewReply(reviewId, buildAdminReviewReplyRequest(content, requestId))
      items.value = replaceAdminReviewItem(items.value, result.data)
      return true
    } catch (caught) {
      actionError.value = toAdminReviewError(caught, 'Unable to save review reply.')
      return false
    } finally {
      pendingReviewId.value = null
      actionGate.end()
    }
  }

  async function updateReplyVisibility(
    reviewId: number,
    visibility: Exclude<AdminReviewVisibilityFilter, 'all'>,
  ) {
    if (!canAccessWorkspace.value || !actionGate.begin()) {
      return false
    }
    actionError.value = null
    pendingReviewId.value = reviewId
    const requestId = (options.createRequestId ?? createRequestId)()
    try {
      const result = await updateAdminReviewReplyVisibility(
        reviewId,
        buildAdminReviewReplyVisibilityRequest(visibility, requestId),
      )
      items.value = replaceAdminReviewItem(items.value, result.data)
      return true
    } catch (caught) {
      actionError.value = toAdminReviewError(caught, 'Unable to update review reply visibility.')
      return false
    } finally {
      pendingReviewId.value = null
      actionGate.end()
    }
  }

  function updateFilters(patch: Partial<AdminReviewFilterDraft>) {
    filters.value = {
      ...filters.value,
      ...patch,
      page: patch.page ?? 1,
    }
  }

  function goToPage(page: number) {
    filters.value = {
      ...filters.value,
      page,
    }
    void refreshList()
  }

  function retry() {
    void refreshList()
  }

  return {
    filters,
    items,
    total,
    totalPages,
    listError,
    actionError,
    isLoadingList,
    isActionPending,
    pendingReviewId,
    bootstrap,
    refreshList,
    updateVisibility,
    saveReply,
    updateReplyVisibility,
    updateFilters,
    goToPage,
    retry,
  }
}

function createRequestId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID()
  }
  return `adm-review-${Date.now()}`
}

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useAppPreferences } from '../../composables/useAppPreferences'
import { useReviewManagement } from '../../composables/useReviewManagement'
import type { PersistedOpsSession } from '../../types/api/auth'
import type { AdminReviewSummaryResponse } from '../../types/api/order'
import { formatDateTime } from '../../utils/format'
import {
  ADMIN_REVIEW_FILTER_STORAGE_KEY,
  buildAdminReviewImageError,
  buildAdminReviewImageView,
  canHideAdminReviewReply,
  canRestoreAdminReviewReply,
  canHideAdminReview,
  canRestoreAdminReview,
  createDefaultReviewFilters,
  deserializeAdminReviewFilters,
  getAdminReviewReplyLabel,
  getAdminReviewVisibilityLabel,
  serializeAdminReviewFilters,
  type AdminReviewFilterDraft,
} from './reviewManagementModel'

interface Props {
  session: PersistedOpsSession | null
  canAccessReviewWorkspace: boolean
}

const props = defineProps<Props>()
const { t } = useAppPreferences()
const sessionRef = computed(() => props.session)
const canAccessRef = computed(() => props.canAccessReviewWorkspace)
const moderationReason = ref('')
const replyDrafts = ref<Record<number, string>>({})
const imageLoadFailures = ref<Record<string, ReturnType<typeof buildAdminReviewImageError>>>({})

const {
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
  updateFilters,
  goToPage,
  retry,
  saveReply,
  updateReplyVisibility,
} = useReviewManagement(sessionRef, canAccessRef, {
  initialFilters: readInitialReviewFilters(),
})

const visibilityOptions = computed(() => [
  { label: t('reviewAdmin.visibilityAll'), value: 'all' },
  { label: t('reviewAdmin.visibilityVisible'), value: 'visible' },
  { label: t('reviewAdmin.visibilityHidden'), value: 'hidden' },
])

const ratingOptions = computed(() => [
  { label: t('reviewAdmin.ratingAll'), value: '' },
  { label: t('reviewAdmin.ratingFive'), value: '5' },
  { label: t('reviewAdmin.ratingFour'), value: '4' },
  { label: t('reviewAdmin.ratingThree'), value: '3' },
  { label: t('reviewAdmin.ratingTwo'), value: '2' },
  { label: t('reviewAdmin.ratingOne'), value: '1' },
])

const visibilityLabels = computed(() => ({
  visible: t('reviewAdmin.visibilityVisible'),
  hidden: t('reviewAdmin.visibilityHidden'),
}))

const replyLabels = computed(() => ({
  visible: t('reviewAdmin.replyVisible'),
  hidden: t('reviewAdmin.replyHidden'),
  none: t('reviewAdmin.replyNone'),
}))

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
    persistReviewFilters(filters.value)
  },
  { deep: true },
)

function visibilityLabel(item: AdminReviewSummaryResponse): string {
  return getAdminReviewVisibilityLabel(item.visibilityStatus, visibilityLabels.value)
}

function replyLabel(item: AdminReviewSummaryResponse): string {
  return getAdminReviewReplyLabel(item, replyLabels.value)
}

function imageView(item: AdminReviewSummaryResponse) {
  return buildAdminReviewImageView(item)
}

function imageFailureKey(item: AdminReviewSummaryResponse, url: string): string {
  return `${item.reviewId}:${url}`
}

function imageFailure(item: AdminReviewSummaryResponse, url: string) {
  return imageLoadFailures.value[imageFailureKey(item, url)] ?? null
}

function markImageLoadFailed(item: AdminReviewSummaryResponse, url: string) {
  imageLoadFailures.value = {
    ...imageLoadFailures.value,
    [imageFailureKey(item, url)]: buildAdminReviewImageError(item, url),
  }
}

function applyFilters() {
  void refreshList()
}

function resetFilters() {
  updateFilters(createDefaultReviewFilters())
  void refreshList()
}

function hideReview(item: AdminReviewSummaryResponse) {
  void updateVisibility(item.reviewId, 'hidden', moderationReason.value)
}

function restoreReview(item: AdminReviewSummaryResponse) {
  void updateVisibility(item.reviewId, 'visible', moderationReason.value)
}

function replyDraft(item: AdminReviewSummaryResponse): string {
  return replyDrafts.value[item.reviewId] ?? item.replyContent ?? ''
}

function updateReplyDraft(item: AdminReviewSummaryResponse, content: string) {
  replyDrafts.value = {
    ...replyDrafts.value,
    [item.reviewId]: content,
  }
}

async function submitReply(item: AdminReviewSummaryResponse) {
  const succeeded = await saveReply(item.reviewId, replyDraft(item))
  if (succeeded) {
    const next = items.value.find((candidate) => candidate.reviewId === item.reviewId)
    updateReplyDraft(item, next?.replyContent ?? '')
  }
}

function hideReply(item: AdminReviewSummaryResponse) {
  void updateReplyVisibility(item.reviewId, 'hidden')
}

function restoreReply(item: AdminReviewSummaryResponse) {
  void updateReplyVisibility(item.reviewId, 'visible')
}

function isReviewActionPending(item: AdminReviewSummaryResponse): boolean {
  return isActionPending.value && pendingReviewId.value === item.reviewId
}

function readInitialReviewFilters(): AdminReviewFilterDraft {
  if (typeof window === 'undefined') {
    return createDefaultReviewFilters()
  }

  return deserializeAdminReviewFilters(readStoredReviewFilters()) ?? createDefaultReviewFilters()
}

function readStoredReviewFilters(): string | null {
  try {
    return window.sessionStorage.getItem(ADMIN_REVIEW_FILTER_STORAGE_KEY)
  } catch {
    return null
  }
}

function persistReviewFilters(nextFilters: AdminReviewFilterDraft) {
  if (typeof window === 'undefined') {
    return
  }

  try {
    window.sessionStorage.setItem(ADMIN_REVIEW_FILTER_STORAGE_KEY, serializeAdminReviewFilters(nextFilters))
  } catch {
    // Filter persistence is best-effort; in-memory filters still drive the UI.
  }
}
</script>

<template>
  <main class="review-admin-shell">
    <section class="hero">
      <div>
        <p class="kicker">{{ t('reviewAdmin.kicker') }}</p>
        <h2>{{ t('reviewAdmin.title') }}</h2>
        <p class="intro">{{ t('reviewAdmin.intro') }}</p>
      </div>
      <div class="hero-actions">
        <button type="button" class="secondary" :disabled="isLoadingList" @click="refreshList()">
          {{ isLoadingList ? t('common.loading') : t('reviewAdmin.refreshList') }}
        </button>
      </div>
    </section>

    <section class="filters">
      <label>
        <span>{{ t('reviewAdmin.productId') }}</span>
        <input :value="filters.productId" type="number" min="1" @input="updateFilters({ productId: ($event.target as HTMLInputElement).value })" />
      </label>
      <label>
        <span>{{ t('reviewAdmin.rating') }}</span>
        <select :value="filters.rating" @change="updateFilters({ rating: ($event.target as HTMLSelectElement).value })">
          <option v-for="option in ratingOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
      <label>
        <span>{{ t('reviewAdmin.userId') }}</span>
        <input :value="filters.userId" type="text" @input="updateFilters({ userId: ($event.target as HTMLInputElement).value })" />
      </label>
      <label>
        <span>{{ t('reviewAdmin.visibility') }}</span>
        <select :value="filters.visibility" @change="updateFilters({ visibility: ($event.target as HTMLSelectElement).value })">
          <option v-for="option in visibilityOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
      <label>
        <span>{{ t('reviewAdmin.fromTime') }}</span>
        <input :value="filters.fromTime" type="datetime-local" @input="updateFilters({ fromTime: ($event.target as HTMLInputElement).value })" />
      </label>
      <label>
        <span>{{ t('reviewAdmin.toTime') }}</span>
        <input :value="filters.toTime" type="datetime-local" @input="updateFilters({ toTime: ($event.target as HTMLInputElement).value })" />
      </label>
      <label>
        <span>{{ t('reviewAdmin.reason') }}</span>
        <input v-model="moderationReason" type="text" maxlength="200" :placeholder="t('reviewAdmin.reasonPlaceholder')" />
      </label>
      <div class="filter-actions">
        <button type="button" class="primary" :disabled="isLoadingList" @click="applyFilters">
          {{ t('reviewAdmin.search') }}
        </button>
        <button type="button" class="secondary" @click="resetFilters">{{ t('dashboard.reset') }}</button>
      </div>
    </section>

    <section class="meta-strip">
      <span>{{ t('common.shopId') }} {{ session?.shopId ?? '--' }}</span>
      <span>{{ t('common.usernameOrMobile') }} {{ session?.username ?? '--' }}</span>
      <span>{{ t('reviewAdmin.totalReviews', { count: total }) }}</span>
    </section>

    <section v-if="listError" class="banner error">
      <strong>{{ listError.message }}</strong>
      <span>{{ t('common.code') }} {{ listError.code }}</span>
      <span v-if="listError.traceId">{{ t('common.traceId') }} {{ listError.traceId }}</span>
      <button type="button" class="secondary" @click="retry">{{ t('common.retry') }}</button>
    </section>

    <section v-if="actionError" class="banner error">
      <strong>{{ actionError.message }}</strong>
      <span>{{ t('common.code') }} {{ actionError.code }}</span>
      <span v-if="actionError.traceId">{{ t('common.traceId') }} {{ actionError.traceId }}</span>
    </section>

    <section v-if="!listError && isLoadingList && items.length === 0" class="banner loading">
      {{ t('common.loading') }}
    </section>

    <section v-if="!listError && !isLoadingList && items.length === 0" class="banner empty">
      {{ t('reviewAdmin.listEmpty') }}
    </section>

    <section class="review-list">
      <article v-for="item in items" :key="item.reviewId" class="review-row">
        <div class="review-main">
          <div class="review-title">
            <strong>{{ t('reviewAdmin.reviewId', { id: item.reviewId }) }}</strong>
            <span class="status-pill" :class="{ hidden: item.visibilityStatus === 'hidden' }">
              {{ visibilityLabel(item) }}
            </span>
          </div>
          <p class="review-content">{{ item.content || t('reviewAdmin.contentEmpty') }}</p>
          <div class="review-meta">
            <span>{{ t('reviewAdmin.productId') }} {{ item.productId }}</span>
            <span>{{ t('reviewAdmin.sku') }} {{ item.skuName }} / {{ item.skuId }}</span>
            <span>{{ t('reviewAdmin.ratingValue', { rating: item.rating }) }}</span>
            <span>{{ t('reviewAdmin.imageCount', { count: item.imageCount }) }}</span>
            <span>{{ t('reviewAdmin.userId') }} {{ item.maskedUserId }}</span>
            <span>{{ formatDateTime(item.createdAt) }}</span>
          </div>
          <div v-if="imageView(item).urls.length || imageView(item).unknownCount" class="review-images-admin">
            <div
              v-for="imageUrl in imageView(item).urls"
              :key="imageUrl"
              class="review-image-frame"
              :class="{ failed: imageFailure(item, imageUrl) }"
            >
              <img
                v-if="!imageFailure(item, imageUrl)"
                :src="imageUrl"
                :alt="t('reviewAdmin.imageAlt', { id: item.reviewId })"
                loading="lazy"
                @error="markImageLoadFailed(item, imageUrl)"
              />
              <div v-else class="review-image-fallback">
                <strong>{{ t('reviewAdmin.imageLoadFailed') }}</strong>
                <span>{{ t('common.code') }} {{ imageFailure(item, imageUrl)?.code }}</span>
                <span>{{ t('reviewAdmin.imageUrl') }} {{ imageUrl }}</span>
              </div>
            </div>
            <div v-if="imageView(item).unknownCount" class="review-image-frame unknown">
              <span>{{ t('reviewAdmin.imageUnknown', { count: imageView(item).unknownCount }) }}</span>
            </div>
          </div>
          <div class="review-audit">
            <span>{{ t('reviewAdmin.orderNo') }} {{ item.orderNo }}</span>
            <span>{{ t('reviewAdmin.operator') }} {{ item.visibilityOperator ?? '--' }}</span>
            <span>{{ t('common.traceId') }} {{ item.visibilityTraceId ?? '--' }}</span>
            <span>{{ t('reviewAdmin.reason') }} {{ item.visibilityReason ?? '--' }}</span>
            <span>{{ t('reviewAdmin.updatedAt') }} {{ formatDateTime(item.visibilityUpdatedAt) }}</span>
          </div>
          <div class="reply-box">
            <div class="reply-head">
              <strong>{{ t('reviewAdmin.replyTitle') }}</strong>
              <span class="status-pill" :class="{ hidden: item.replyVisibilityStatus === 'hidden' }">
                {{ replyLabel(item) }}
              </span>
            </div>
            <p v-if="item.replyContent" class="reply-content">{{ item.replyContent }}</p>
            <p v-else class="reply-empty">{{ t('reviewAdmin.replyNone') }}</p>
            <div class="review-audit">
              <span>{{ t('reviewAdmin.operator') }} {{ item.replyOperator ?? '--' }}</span>
              <span>{{ t('common.traceId') }} {{ item.replyTraceId ?? '--' }}</span>
              <span>{{ t('reviewAdmin.updatedAt') }} {{ formatDateTime(item.replyUpdatedAt) }}</span>
            </div>
            <textarea
              :value="replyDraft(item)"
              maxlength="300"
              :placeholder="t('reviewAdmin.replyPlaceholder')"
              :disabled="isActionPending"
              @input="updateReplyDraft(item, ($event.target as HTMLTextAreaElement).value)"
            ></textarea>
            <div class="reply-actions">
              <button
                type="button"
                class="primary"
                :disabled="isActionPending || !replyDraft(item).trim()"
                @click="submitReply(item)"
              >
                {{ isReviewActionPending(item) ? t('reviewAdmin.updating') : (item.replyContent ? t('reviewAdmin.editReply') : t('reviewAdmin.reply')) }}
              </button>
              <button
                type="button"
                class="danger"
                :disabled="!canHideAdminReviewReply(item) || isActionPending"
                @click="hideReply(item)"
              >
                {{ t('reviewAdmin.hideReply') }}
              </button>
              <button
                type="button"
                class="secondary"
                :disabled="!canRestoreAdminReviewReply(item) || isActionPending"
                @click="restoreReply(item)"
              >
                {{ t('reviewAdmin.restoreReply') }}
              </button>
            </div>
          </div>
        </div>
        <div class="row-actions">
          <button
            type="button"
            class="danger"
            :disabled="!canHideAdminReview(item) || isActionPending"
            @click="hideReview(item)"
          >
            {{ isReviewActionPending(item) ? t('reviewAdmin.updating') : t('reviewAdmin.hide') }}
          </button>
          <button
            type="button"
            class="secondary"
            :disabled="!canRestoreAdminReview(item) || isActionPending"
            @click="restoreReview(item)"
          >
            {{ isReviewActionPending(item) ? t('reviewAdmin.updating') : t('reviewAdmin.restore') }}
          </button>
        </div>
      </article>
    </section>

    <section class="pagination">
      <button type="button" class="secondary" :disabled="filters.page <= 1 || isLoadingList" @click="goToPage(filters.page - 1)">
        {{ t('common.prev') }}
      </button>
      <span>{{ t('orderAdmin.pageLabel', { page: filters.page, total: totalPages }) }}</span>
      <button type="button" class="secondary" :disabled="filters.page >= totalPages || isLoadingList" @click="goToPage(filters.page + 1)">
        {{ t('common.next') }}
      </button>
    </section>
  </main>
</template>

<style scoped>
.review-admin-shell {
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

h2 {
  margin: 0;
  font-size: 1.9rem;
  line-height: 1;
}

.intro {
  margin: 0.55rem 0 0;
  color: var(--text-muted);
}

.hero-actions,
.filter-actions,
.row-actions,
.reply-actions,
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

.meta-strip,
.review-meta,
.review-audit {
  color: var(--text-muted);
}

label {
  display: grid;
  gap: 0.35rem;
}

label span {
  font-size: 0.78rem;
  text-transform: uppercase;
  color: var(--text-muted);
}

input,
select,
textarea {
  width: 100%;
  border: 1px solid var(--border-soft);
  border-radius: 0.85rem;
  background: var(--bg-panel);
  color: var(--text-main);
  min-height: 2.8rem;
  padding: 0.7rem 0.85rem;
}

textarea {
  min-height: 5.5rem;
  resize: vertical;
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

.review-list {
  display: grid;
  gap: 0.75rem;
}

.review-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 1rem;
  align-items: start;
  padding: 1rem;
  border: 1px solid var(--border-soft);
  border-radius: 1rem;
  background: var(--bg-panel);
}

.review-main {
  min-width: 0;
  display: grid;
  gap: 0.65rem;
}

.review-title {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  flex-wrap: wrap;
}

.review-content {
  margin: 0;
  overflow-wrap: anywhere;
}

.review-meta,
.review-audit {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
  font-size: 0.88rem;
}

.review-images-admin {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(92px, 120px));
  gap: 0.55rem;
}

.review-image-frame {
  min-height: 92px;
  aspect-ratio: 1;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  overflow: hidden;
  background: var(--surface-subtle);
}

.review-image-frame img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.review-image-frame.failed,
.review-image-frame.unknown {
  padding: 0.55rem;
  display: grid;
  align-content: center;
  color: var(--danger-text);
  background: var(--danger-bg);
}

.review-image-frame.unknown {
  color: var(--text-muted);
  background: var(--bg-soft);
}

.review-image-fallback {
  display: grid;
  gap: 0.25rem;
  font-size: 0.72rem;
  overflow-wrap: anywhere;
}

.reply-box {
  display: grid;
  gap: 0.55rem;
  padding: 0.85rem;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: var(--surface-subtle);
}

.reply-head,
.reply-actions {
  display: flex;
  gap: 0.55rem;
  align-items: center;
  flex-wrap: wrap;
}

.reply-content,
.reply-empty {
  margin: 0;
  overflow-wrap: anywhere;
}

.reply-empty {
  color: var(--text-muted);
}

.status-pill {
  padding: 0.25rem 0.55rem;
  border-radius: 999px;
  background: var(--info-bg);
  color: var(--info-text);
  font-size: 0.78rem;
  font-weight: 700;
}

.status-pill.hidden {
  background: var(--danger-bg);
  color: var(--danger-text);
}

.row-actions {
  justify-content: end;
}

.pagination {
  justify-content: center;
}

@media (max-width: 860px) {
  .hero,
  .review-row {
    display: grid;
    grid-template-columns: 1fr;
  }

  .row-actions {
    justify-content: start;
  }
}

@media (max-width: 620px) {
  .review-admin-shell {
    width: min(100% - 1rem, 1180px);
  }

  .hero-actions,
  .filter-actions,
  .row-actions,
  .reply-actions,
  .pagination {
    display: grid;
    grid-template-columns: 1fr;
  }

  .primary,
  .secondary,
  .danger {
    width: 100%;
  }
}
</style>

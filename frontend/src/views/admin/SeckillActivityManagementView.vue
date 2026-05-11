<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useAppPreferences } from '../../composables/useAppPreferences'
import { useSeckillActivityManagement } from '../../composables/useSeckillActivityManagement'
import type { PersistedOpsSession } from '../../types/api/auth'

interface Props {
  session: PersistedOpsSession | null
  canAccessSeckillWorkspace: boolean
}

const props = defineProps<Props>()
const { t } = useAppPreferences()
const sessionRef = computed(() => props.session)
const canAccessRef = computed(() => props.canAccessSeckillWorkspace)

const {
  items,
  detail,
  draft,
  filterStatus,
  listError,
  detailError,
  actionError,
  isLoadingList,
  isLoadingDetail,
  isSaving,
  validation,
  selectedItem,
  bootstrap,
  refreshList,
  selectActivity,
  createNewActivity,
  saveDraft,
  changeStatus,
  bindSku,
  retry,
  setFilterStatus,
} = useSeckillActivityManagement(sessionRef, canAccessRef)

const bindSkuDraft = ref<Record<number, { activityStock: string; seckillPriceCent: string }>>({})

const statusOptions = computed(() => [
  { label: t('seckillAdmin.statusAll'), value: 'all' },
  { label: t('seckillAdmin.statusDraft'), value: 'draft' },
  { label: t('seckillAdmin.statusScheduled'), value: 'scheduled' },
  { label: t('seckillAdmin.statusActive'), value: 'active' },
  { label: t('seckillAdmin.statusEnded'), value: 'ended' },
])

const detailStatusLabel = computed(() => {
  return statusLabel(draft.value.status)
})

watch(
  () => props.session,
  () => {
    void bootstrap()
  },
  { immediate: true },
)

onMounted(() => {
  void bootstrap()
})

function onSave() {
  void saveDraft()
}

function onRetry() {
  retry()
}

function onSelect(activityId: number) {
  void selectActivity(activityId)
}

function onStatusChange(event: Event) {
  const target = event.target as HTMLSelectElement
  setFilterStatus(target.value as 'all' | 'draft' | 'scheduled' | 'active' | 'ended')
}

function onBindSku(skuId: number) {
  const stockDraft = bindSkuDraft.value[skuId]
  void bindSku(skuId, {
    activityStock: Number(stockDraft?.activityStock ?? ''),
    seckillPriceCent: Number(stockDraft?.seckillPriceCent ?? ''),
  })
}

function setBindSkuDraft(skuId: number, field: 'activityStock' | 'seckillPriceCent', value: string) {
  bindSkuDraft.value = {
    ...bindSkuDraft.value,
    [skuId]: {
      ...bindSkuDraft.value[skuId],
      [field]: value,
    },
  }
}

function statusLabel(status: string): string {
  if (status === 'active') {
    return t('seckillAdmin.statusActive')
  }
  if (status === 'scheduled') {
    return t('seckillAdmin.statusScheduled')
  }
  if (status === 'draft') {
    return t('seckillAdmin.statusDraft')
  }
  if (status === 'ended') {
    return t('seckillAdmin.statusEnded')
  }
  return status
}

function showPublishButton(): boolean {
  return detail.value?.status === 'draft' || detail.value?.status === 'scheduled'
}

function showUnpublishButton(): boolean {
  return detail.value?.status === 'active'
}

function showEndButton(): boolean {
  return detail.value?.status === 'active' || detail.value?.status === 'scheduled'
}
</script>

<template>
  <main class="seckill-admin-shell">
    <section class="hero">
      <div>
        <p class="kicker">{{ t('seckillAdmin.kicker') }}</p>
        <h2>{{ t('seckillAdmin.title') }}</h2>
        <p class="intro">{{ t('seckillAdmin.intro') }}</p>
      </div>
      <div class="hero-actions">
        <button type="button" class="secondary" @click="createNewActivity">{{ t('seckillAdmin.newActivity') }}</button>
        <button type="button" class="secondary" :disabled="isLoadingList" @click="refreshList(true)">
          {{ isLoadingList ? t('common.loading') : t('seckillAdmin.refreshList') }}
        </button>
      </div>
    </section>

    <section class="toolbar">
      <label>
        <span>{{ t('seckillAdmin.statusFilter') }}</span>
        <select :value="filterStatus" @change="onStatusChange">
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
      <div class="toolbar-note">
        <span>{{ t('common.shopId') }} {{ session?.shopId ?? '--' }}</span>
        <span>{{ t('common.usernameOrMobile') }} {{ session?.username ?? '--' }}</span>
      </div>
    </section>

    <section v-if="listError" class="banner error">
      <strong>{{ listError.message }}</strong>
      <span>{{ t('common.code') }} {{ listError.code }}</span>
      <span v-if="listError.traceId">{{ t('common.traceId') }} {{ listError.traceId }}</span>
      <button type="button" class="secondary" @click="onRetry">{{ t('common.retry') }}</button>
    </section>

    <section v-else-if="isLoadingList && items.length === 0" class="banner loading">
      {{ t('common.loading') }}
    </section>

    <section v-else-if="items.length === 0" class="banner empty">
      {{ t('seckillAdmin.listEmpty') }}
    </section>

    <section class="workspace-grid">
      <aside class="list-panel">
        <div class="panel-head">
          <h3>{{ t('seckillAdmin.listTitle') }}</h3>
          <span>{{ items.length }}</span>
        </div>
        <div class="list">
          <button
            v-for="item in items"
            :key="item.activityId"
            type="button"
            class="list-item"
            :class="{ active: selectedItem?.activityId === item.activityId }"
            @click="onSelect(item.activityId)"
          >
            <strong>{{ item.activityName }}</strong>
            <span>{{ statusLabel(item.status) }}</span>
            <small>{{ t('seckillAdmin.skuCount', { count: item.skuCount }) }} | {{ t('seckillAdmin.totalActivityStock') }} {{ item.totalActivityStock }}</small>
          </button>
        </div>
      </aside>

      <section class="detail-panel">
          <div class="panel-head">
            <div>
              <h3>{{ draft.activityName || t('seckillAdmin.newActivity') }}</h3>
              <p class="meta">
                {{ t('seckillAdmin.statusFilter') }} {{ detailStatusLabel }}
              </p>
            </div>
            <div class="detail-actions">
              <button v-if="showPublishButton()" type="button" class="secondary" :disabled="isSaving" @click="changeStatus('active')">
                {{ t('seckillAdmin.publish') }}
              </button>
              <button v-if="showUnpublishButton()" type="button" class="secondary" :disabled="isSaving" @click="changeStatus('draft')">
                {{ t('seckillAdmin.unpublish') }}
              </button>
              <button v-if="showEndButton()" type="button" class="secondary" :disabled="isSaving" @click="changeStatus('ended')">
                {{ t('seckillAdmin.endActivity') }}
              </button>
              <button type="button" class="primary" :disabled="isSaving || !validation.valid" @click="onSave">
                {{ isSaving ? t('seckillAdmin.saving') : t('seckillAdmin.saveActivity') }}
              </button>
            </div>
          </div>

          <div v-if="detailError" class="banner error">
            <strong>{{ detailError.message }}</strong>
            <span>{{ t('common.code') }} {{ detailError.code }}</span>
            <span v-if="detailError.traceId">{{ t('common.traceId') }} {{ detailError.traceId }}</span>
          </div>
          <div v-if="actionError" class="banner error">
            <strong>{{ actionError.message }}</strong>
            <span>{{ t('common.code') }} {{ actionError.code }}</span>
            <span v-if="actionError.traceId">{{ t('common.traceId') }} {{ actionError.traceId }}</span>
          </div>

          <div class="form-grid">
            <label>
              <span>{{ t('seckillAdmin.activityName') }}</span>
              <input v-model="draft.activityName" type="text" />
            </label>
            <label>
              <span>{{ t('seckillAdmin.activityDescription') }}</span>
              <textarea v-model="draft.activityDescription" rows="4" />
            </label>
            <label>
              <span>{{ t('seckillAdmin.startsAt') }}</span>
              <input v-model="draft.startsAt" type="text" />
            </label>
            <label>
              <span>{{ t('seckillAdmin.endsAt') }}</span>
              <input v-model="draft.endsAt" type="text" />
            </label>
          </div>

          <div v-if="detail" class="time-info">
            <span>{{ t('seckillAdmin.serverTime') }}: {{ detail.serverTime }}</span>
          </div>

          <div v-if="detail" class="sku-section">
            <div class="panel-head compact">
              <h4>{{ t('seckillAdmin.skuList') }}</h4>
            </div>
            <div v-for="(sku, index) in detail.skus" :key="sku.skuId" class="sku-card">
              <div class="sku-grid">
                <span>{{ t('seckillAdmin.productName') }}: {{ sku.productName }}</span>
                <span>{{ t('seckillAdmin.skuName') }}: {{ sku.skuName }}</span>
                <span>{{ t('seckillAdmin.priceCent') }}: {{ sku.priceCent }}</span>
                <span>{{ t('seckillAdmin.seckillPriceCent') }}: {{ sku.seckillPriceCent }}</span>
                <span>{{ t('seckillAdmin.availableStock') }}: {{ sku.availableStock }}</span>
                <span>{{ t('seckillAdmin.activityStock') }}: {{ sku.activityStock }}</span>
                <span>{{ t('seckillAdmin.soldCount') }}: {{ sku.soldCount }}</span>
              </div>
              <div v-if="sku.skuId" class="sku-actions">
                <label>
                  <span>{{ t('seckillAdmin.activityStock') }}</span>
                  <input
                    :value="bindSkuDraft[sku.skuId]?.activityStock ?? ''"
                    type="number"
                    min="0"
                    @input="setBindSkuDraft(sku.skuId, 'activityStock', ($event.target as HTMLInputElement).value)"
                  />
                </label>
                <label>
                  <span>{{ t('seckillAdmin.seckillPriceCent') }}</span>
                  <input
                    :value="bindSkuDraft[sku.skuId]?.seckillPriceCent ?? ''"
                    type="number"
                    min="0"
                    @input="setBindSkuDraft(sku.skuId, 'seckillPriceCent', ($event.target as HTMLInputElement).value)"
                  />
                </label>
                <button type="button" class="secondary" :disabled="isSaving" @click="onBindSku(sku.skuId)">
                  {{ t('seckillAdmin.bindSku') }}
                </button>
              </div>
            </div>
          </div>

          <div v-if="validation.errors.activityStock" class="validation-error">
            <small>{{ validation.errors.activityStock }}</small>
          </div>
      </section>
    </section>

    <section v-if="isLoadingDetail" class="banner loading">
      {{ t('seckillAdmin.detailLoading') }}
    </section>
  </main>
</template>

<style scoped>
.seckill-admin-shell {
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
.detail-actions,
.sku-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: end;
}

.toolbar-note {
  display: grid;
  gap: 0.2rem;
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
  min-height: 8rem;
}

.banner {
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

.workspace-grid {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 1rem;
}

.list-panel,
.detail-panel {
  background: var(--bg-panel);
  border: 1px solid var(--border-soft);
  border-radius: 1.25rem;
  padding: 1rem;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: start;
  margin-bottom: 0.9rem;
}

.panel-head.compact {
  margin-bottom: 0.7rem;
}

.list {
  display: grid;
  gap: 0.65rem;
}

.list-item {
  text-align: left;
  display: grid;
  gap: 0.2rem;
  padding: 0.85rem;
  border-radius: 0.9rem;
  border: 1px solid var(--border-soft);
  background: var(--bg-soft);
  cursor: pointer;
}

.list-item.active {
  border-color: var(--accent);
  background: var(--info-bg);
}

.form-grid,
.sku-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0.85rem;
}

.sku-section {
  margin-top: 1rem;
  display: grid;
  gap: 0.8rem;
}

.sku-card {
  display: grid;
  gap: 0.75rem;
  padding: 0.9rem;
  border-radius: 1rem;
  border: 1px solid var(--border-soft);
  background: var(--bg-soft);
}

small,
.validation-error small {
  color: var(--danger-text);
}

.time-info {
  padding: 0.5rem 0;
  color: var(--text-muted);
  font-size: 0.85rem;
}

@media (max-width: 960px) {
  .hero,
  .toolbar,
  .workspace-grid {
    display: grid;
  }

  .workspace-grid {
    grid-template-columns: 1fr;
  }
}
</style>

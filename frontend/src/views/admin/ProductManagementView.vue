<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useAppPreferences } from '../../composables/useAppPreferences'
import { useProductManagement } from '../../composables/useProductManagement'
import type { PersistedOpsSession } from '../../types/api/auth'
import { formatMoney } from '../../utils/format'

interface Props {
  session: PersistedOpsSession | null
  canAccessProductWorkspace: boolean
}

const props = defineProps<Props>()
const { t } = useAppPreferences()
const sessionRef = computed(() => props.session)
const canAccessRef = computed(() => props.canAccessProductWorkspace)

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
  selectProduct,
  createNewProduct,
  addSku,
  removeSku,
  updateSku,
  saveDraft,
  changeStatus,
  adjustStock,
  retry,
  setFilterStatus,
} = useProductManagement(sessionRef, canAccessRef)

const stockAdjustments = ref<Record<number, string>>({})

const statusOptions = computed(() => [
  { label: t('productAdmin.statusAll'), value: 'all' },
  { label: t('productAdmin.statusDraft'), value: 'draft' },
  { label: t('productAdmin.statusActive'), value: 'active' },
  { label: t('productAdmin.statusInactive'), value: 'inactive' },
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

function onSelect(productId: number) {
  void selectProduct(productId)
}

function onAddSku() {
  addSku()
}

function onStatusChange(event: Event) {
  const target = event.target as HTMLSelectElement
  setFilterStatus(target.value as 'all' | 'draft' | 'active' | 'inactive')
}

function onAdjustStock(skuId: number) {
  const nextValue = Number(stockAdjustments.value[skuId] ?? '')
  void adjustStock(skuId, Number.isFinite(nextValue) ? nextValue : 0)
}

function setStockDraft(skuId: number, value: string) {
  stockAdjustments.value = {
    ...stockAdjustments.value,
    [skuId]: value,
  }
}

function statusLabel(status: string): string {
  if (status === 'active') {
    return t('productAdmin.statusActive')
  }
  if (status === 'inactive') {
    return t('productAdmin.statusInactive')
  }
  if (status === 'draft') {
    return t('productAdmin.statusDraft')
  }
  return status
}
</script>

<template>
  <main class="product-admin-shell">
    <section class="hero">
      <div>
        <p class="kicker">{{ t('productAdmin.kicker') }}</p>
        <h2>{{ t('productAdmin.title') }}</h2>
        <p class="intro">{{ t('productAdmin.intro') }}</p>
      </div>
      <div class="hero-actions">
        <button type="button" class="secondary" @click="createNewProduct">{{ t('productAdmin.newProduct') }}</button>
        <button type="button" class="secondary" :disabled="isLoadingList" @click="refreshList(true)">
          {{ isLoadingList ? t('common.loading') : t('productAdmin.refreshList') }}
        </button>
      </div>
    </section>

    <section class="toolbar">
      <label>
        <span>{{ t('productAdmin.productStatus') }}</span>
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
      {{ t('productAdmin.listEmpty') }}
    </section>

    <section class="workspace-grid">
      <aside class="list-panel">
        <div class="panel-head">
          <h3>{{ t('productAdmin.listTitle') }}</h3>
          <span>{{ items.length }}</span>
        </div>
        <div class="list">
          <button
            v-for="item in items"
            :key="item.productId"
            type="button"
            class="list-item"
            :class="{ active: selectedItem?.productId === item.productId }"
            @click="onSelect(item.productId)"
          >
            <strong>{{ item.productName }}</strong>
            <span>{{ item.status }}</span>
            <small>{{ formatMoney(item.minPriceCent) }} - {{ formatMoney(item.maxPriceCent) }}</small>
            <small>{{ t('productAdmin.availableStock') }} {{ item.availableStockTotal }} | {{ t('productAdmin.reservedStock') }} {{ item.reservedStockTotal }}</small>
          </button>
        </div>
      </aside>

      <section class="detail-panel">
          <div class="panel-head">
            <div>
              <h3>{{ draft.productName || t('productAdmin.newProduct') }}</h3>
              <p class="meta">
                {{ t('productAdmin.productStatus') }} {{ detailStatusLabel }}
              </p>
            </div>
            <div class="detail-actions">
              <button v-if="detail" type="button" class="secondary" :disabled="isSaving" @click="changeStatus('active')">
                {{ t('productAdmin.activate') }}
              </button>
              <button v-if="detail" type="button" class="secondary" :disabled="isSaving" @click="changeStatus('inactive')">
                {{ t('productAdmin.deactivate') }}
              </button>
              <button type="button" class="primary" :disabled="isSaving || !validation.valid" @click="onSave">
                {{ isSaving ? t('productAdmin.saving') : t('productAdmin.saveProduct') }}
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
              <span>{{ t('productAdmin.productName') }}</span>
              <input v-model="draft.productName" type="text" />
              <small v-if="validation.errors.productName">{{ t('productAdmin.invalidRequired') }}</small>
            </label>
            <label>
              <span>{{ t('productAdmin.productDescription') }}</span>
              <textarea v-model="draft.productDescription" rows="4" />
            </label>
          </div>

          <div class="sku-section">
            <div class="panel-head compact">
              <h4>{{ t('productAdmin.skuList') }}</h4>
              <button type="button" class="secondary" @click="onAddSku">{{ t('productAdmin.addSku') }}</button>
            </div>
            <div v-for="(sku, index) in draft.skus" :key="`${sku.skuId ?? 'new'}-${index}`" class="sku-card">
              <div class="sku-grid">
                <label>
                  <span>{{ t('productAdmin.skuCode') }}</span>
                  <input :value="sku.skuCode" @input="updateSku(index, { skuCode: ($event.target as HTMLInputElement).value })" />
                  <small v-if="validation.errors.skus[index]?.skuCode">{{ t('productAdmin.duplicateSku') }}</small>
                </label>
                <label>
                  <span>{{ t('productAdmin.skuName') }}</span>
                  <input :value="sku.skuName" @input="updateSku(index, { skuName: ($event.target as HTMLInputElement).value })" />
                </label>
                <label>
                  <span>{{ t('productAdmin.priceCent') }}</span>
                  <input :value="sku.priceCent ?? ''" type="number" min="1" @input="updateSku(index, { priceCent: Number(($event.target as HTMLInputElement).value) })" />
                </label>
                <label>
                  <span>{{ t('productAdmin.availableStock') }}</span>
                  <input :value="sku.availableStock ?? ''" type="number" min="0" @input="updateSku(index, { availableStock: Number(($event.target as HTMLInputElement).value) })" />
                </label>
              </div>
              <div class="sku-actions">
                <button
                  v-if="sku.skuId"
                  type="button"
                  class="secondary"
                  :disabled="isSaving"
                  @click="onAdjustStock(sku.skuId)"
                >
                  {{ t('productAdmin.adjustStock') }}
                </button>
                <button type="button" class="secondary" :disabled="draft.skus.length === 1" @click="removeSku(index)">
                  {{ t('productAdmin.removeSku') }}
                </button>
                <label v-if="sku.skuId">
                  <span>{{ t('productAdmin.availableStock') }}</span>
                  <input :value="stockAdjustments[sku.skuId] ?? sku.availableStock ?? ''" type="number" min="0" @input="setStockDraft(sku.skuId, ($event.target as HTMLInputElement).value)" />
                </label>
              </div>
            </div>
          </div>
      </section>
    </section>

    <section v-if="isLoadingDetail" class="banner loading">
      {{ t('productAdmin.detailLoading') }}
    </section>
  </main>
</template>

<style scoped>
.product-admin-shell {
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

small {
  color: var(--text-muted);
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

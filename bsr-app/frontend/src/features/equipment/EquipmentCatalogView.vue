<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { currentActor, type CurrentActor } from '../identity/authApi'
import { errorText, uiText } from '../../shared/presentation/zh'
import { loadCatalog, loadPricing, saveArchive, type EquipmentArchive } from './equipmentCatalogApi'

const actor = ref<CurrentActor | null>(null)
const storeId = ref('')
const status = ref('')
const inventory = ref('')
const rows = ref<EquipmentArchive[]>([])
const rates = ref<Record<string, string>>({})
const busy = ref(false)
const loaded = ref(false)
const error = ref('')
const notice = ref('')
const editing = ref(false)
const editingId = ref<string>()
const revision = ref('0')
const archiveStore = ref('')
const form = reactive({ inventoryNumber: '', equipmentName: '', equipmentCategory: 'SKI', skillLevel: 'BEGINNER',
  description: '', purchaseDate: '', purchasePrice: '', retailPrice: '', currentDailyRate: '' })
const states = ['AVAILABLE', 'PENDING_ISSUE', 'RENTED', 'RETURN_INSPECTION', 'WAITING_REPAIR', 'IN_REPAIR', 'PENDING_ACCEPTANCE', 'IN_TRANSFER', 'PENDING_REVIEW']
const stores = computed(() => [...new Set(actor.value?.grants.filter(g => ['MANAGER', 'EMPLOYEE'].includes(g.role) && g.storeId).map(g => g.storeId as string) ?? [])])
const canEdit = computed(() => actor.value?.grants.some(g => g.role === 'MANAGER' && g.storeId === storeId.value) ?? false)
watch(storeId, () => { rows.value = []; loaded.value = false; editing.value = false; error.value = ''; notice.value = '' })
onMounted(async () => {
  try {
    actor.value = await currentActor()
    storeId.value = stores.value[0] ?? ''
    rates.value = (await loadPricing()).dailyRates
  } catch (failure) { error.value = errorText(failure) }
})
async function search() {
  if (busy.value || !storeId.value) return
  busy.value = true; error.value = ''; rows.value = []; loaded.value = false; editing.value = false
  try { rows.value = await loadCatalog(storeId.value, status.value, inventory.value); loaded.value = true }
  catch (failure) { error.value = errorText(failure) }
  finally { busy.value = false }
}
function edit(row?: EquipmentArchive) {
  if (!canEdit.value || busy.value) return
  editingId.value = row?.equipmentId; revision.value = row?.revision ?? '0'; archiveStore.value = storeId.value
  Object.assign(form, { inventoryNumber: row?.inventoryNumber ?? '', equipmentName: row?.equipmentName ?? '',
    equipmentCategory: row?.equipmentCategory ?? 'SKI', skillLevel: row?.skillLevel ?? 'BEGINNER',
    description: row?.description ?? '', purchaseDate: row?.purchaseDate ?? '', purchasePrice: row?.purchasePrice ?? '',
    retailPrice: row?.retailPrice ?? '', currentDailyRate: row?.currentDailyRate ?? '' })
  editing.value = true; error.value = ''; notice.value = ''
}
async function save() {
  if (busy.value || !canEdit.value || archiveStore.value !== storeId.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try {
    const saved = await saveArchive({ ...form, storeId: archiveStore.value, expectedRevision: revision.value,
      description: form.description || null, purchaseDate: form.purchaseDate || null, purchasePrice: form.purchasePrice || null,
      retailPrice: form.retailPrice || null, currentDailyRate: form.currentDailyRate || null }, editingId.value)
    editing.value = false
    rows.value = await loadCatalog(storeId.value, status.value, inventory.value)
    loaded.value = true; notice.value = `档案 ${saved.inventoryNumber} 已保存。历史合同租金不变。`
  } catch (failure) { error.value = errorText(failure) }
  finally { busy.value = false }
}
</script>

<template>
  <section class="catalog-page">
    <header>
      <div>
        <p class="eyebrow">
          EQUIPMENT · 雪具管理
        </p><h1>雪具档案</h1><p>查询各状态雪具，维护规格与现行价格。金额统一为 NOK（挪威克朗）。</p>
      </div><button
        v-if="canEdit"
        data-testid="new-archive"
        :disabled="busy"
        @click="edit()"
      >
        新增雪具
      </button>
    </header>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p><p
      v-if="notice"
      role="status"
    >
      {{ notice }}
    </p>
    <form
      class="filters"
      data-testid="catalog-search"
      @submit.prevent="search"
    >
      <label>授权门店<select
        v-model="storeId"
        :disabled="busy"
        required
      ><option value="">请选择</option><option
        v-for="id in stores"
        :key="id"
        :value="id"
      >门店 {{ id }}</option></select></label>
      <label>装备状态<select
        v-model="status"
        aria-label="装备状态"
        :disabled="busy"
      ><option value="">全部状态</option><option
        v-for="state in states"
        :key="state"
        :value="state"
      >{{ uiText(state) }}</option></select></label>
      <label>库存编号<input
        v-model.trim="inventory"
        maxlength="40"
        placeholder="精确查找（选填）"
        :disabled="busy"
      ></label>
      <button
        :disabled="busy || !storeId"
        type="submit"
      >
        {{ busy ? '处理中…' : '查询档案' }}
      </button>
    </form>
    <p v-if="loaded && !rows.length">
      没有符合条件的雪具档案。
    </p>
    <div
      v-if="rows.length"
      class="table-wrap"
    >
      <table>
        <caption>查询结果 · {{ rows.length }} 件</caption><thead><tr><th>库存编号 / 名称</th><th>类别 / 等级</th><th>状态 / 门店</th><th>现行日价</th><th>档案</th></tr></thead><tbody>
          <tr
            v-for="row in rows"
            :key="row.equipmentId"
          >
            <td><strong>{{ row.inventoryNumber }}</strong><br>{{ row.equipmentName }}</td><td>{{ uiText(row.equipmentCategory) }}<br>{{ uiText(row.skillLevel) }}</td><td>{{ uiText(row.status) }}<br>归属 {{ row.homeStoreId }} · 当前 {{ row.currentStoreId }}</td><td>{{ row.currentDailyRate }} NOK / 天</td><td>
              <details><summary>查看详情</summary><p>{{ row.description || '暂无描述' }}</p><p>购入日期：{{ row.purchaseDate || '未记录' }}</p><p>成本：{{ row.purchasePrice ?? '未记录' }} · 零售价：{{ row.retailPrice ?? '未记录' }}</p></details><button
                v-if="canEdit"
                :data-testid="`edit-${row.equipmentId}`"
                :disabled="busy"
                @click="edit(row)"
              >
                编辑档案
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <form
      v-if="editing && canEdit"
      class="editor"
      aria-label="雪具档案编辑"
      @submit.prevent="save"
    >
      <h2>{{ editingId ? '编辑雪具档案' : '新增雪具档案' }}</h2>
      <p>状态及门店位置由租赁、归还、维修和调拨流程变更，不在此处直接修改。</p>
      <div class="fields">
        <label>库存编号<input
          v-model.trim="form.inventoryNumber"
          :disabled="!!editingId || busy"
          maxlength="40"
          required
        ></label><label>名称<input
          v-model.trim="form.equipmentName"
          maxlength="120"
          :disabled="busy"
          required
        ></label>
        <label>类别<select
          v-model="form.equipmentCategory"
          :disabled="!!editingId || busy"
        ><option
          v-for="key in ['SKI','SNOWBOARD','BOOTS','POLES','GOGGLES','HELMET']"
          :key="key"
          :value="key"
        >{{ uiText(key) }}</option></select></label><label>技术等级<input
          v-model.trim="form.skillLevel"
          maxlength="20"
          :disabled="busy"
          required
        ></label>
        <label>购入日期<input
          v-model="form.purchaseDate"
          type="date"
          :disabled="busy"
        ></label><label>购入成本（NOK）<input
          v-model="form.purchasePrice"
          type="number"
          min="0"
          max="9999999999.99"
          step="0.01"
          :disabled="busy"
        ></label><label>零售价（NOK）<input
          v-model="form.retailPrice"
          type="number"
          min="0"
          max="9999999999.99"
          step="0.01"
          :disabled="busy"
        ></label><label>当前日租金（NOK）<input
          v-model="form.currentDailyRate"
          aria-label="当前日租金"
          type="number"
          min="0"
          max="9999999999.99"
          step="0.01"
          :placeholder="rates[form.equipmentCategory] || '请输入'"
          :disabled="busy"
          :required="!!editingId"
        ></label>
      </div>
      <label>雪具描述<textarea
        v-model.trim="form.description"
        maxlength="1000"
        rows="3"
        :disabled="busy"
      /></label><p v-if="!editingId">
        未填写日租金时采用该类别参考价：{{ rates[form.equipmentCategory] || '未配置，需手动填写' }} NOK / 天。
      </p>
      <div class="actions">
        <button
          type="submit"
          :disabled="busy"
        >
          {{ busy ? '保存中…' : '保存档案' }}
        </button><button
          type="button"
          class="secondary"
          :disabled="busy"
          @click="editing = false"
        >
          取消
        </button>
      </div>
    </form>
    <aside v-if="Object.keys(rates).length">
      <h2>BSR 参考日价</h2><div class="rates">
        <span
          v-for="(price, key) in rates"
          :key="key"
        >{{ uiText(String(key)) }} <strong>{{ price }}</strong> NOK / 天</span>
      </div><p>按具体装备现行价建单并保存快照；不是套餐价，不叠加基础费。</p>
    </aside>
  </section>
</template>

<style scoped>
.catalog-page{max-width:76rem}header,.actions{display:flex;justify-content:space-between;gap:1rem;align-items:center}h1{margin:.25rem 0}.eyebrow{font-size:.75rem;color:#527481;letter-spacing:.12em}header p,aside p,.editor>p{color:#627580}.filters,.fields{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:1rem}.filters{align-items:end;margin:1.8rem 0}label{display:grid;gap:.4rem;font-size:.88rem}input,select,textarea{width:100%;box-sizing:border-box;padding:.7rem;border:1px solid #cbd9e0;border-radius:.55rem;background:#fff;color:#16394a;min-height:2.7rem}button{padding:.75rem 1.1rem;background:#126683;color:white;border:0;border-radius:.55rem;cursor:pointer}button:disabled{opacity:.55;cursor:default}.secondary{background:#e5eef3;color:#16394a}.table-wrap{overflow:auto;border:1px solid #dbe5ea;border-radius:.8rem}table{width:100%;border-collapse:collapse;text-align:left}caption{text-align:left;padding:1rem;background:#eef5f8}th,td{padding:1rem;border-bottom:1px solid #e3ebef;vertical-align:top}td button{margin-top:.6rem;padding:.5rem .7rem}details p{max-width:24rem;overflow-wrap:anywhere}.editor,aside{padding:1.4rem;margin-top:1.5rem;border:1px solid #dbe5ea;border-radius:.9rem;background:#f7fafc}.fields{grid-template-columns:repeat(2,minmax(0,1fr));margin:1rem 0}.actions{justify-content:flex-start;margin-top:1rem}.rates{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:.8rem}.rates span{padding:.8rem;background:white;border-radius:.5rem}[role=alert]{color:#a32a36}h2{font-size:1.1rem}@media(max-width:700px){header{align-items:flex-start}.filters,.fields,.rates{grid-template-columns:1fr}.catalog-page{min-width:0}}
header>button{white-space:nowrap;flex-shrink:0}.table-wrap table{min-width:40rem}
</style>

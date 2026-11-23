<script setup lang="ts">
import StoreSelect from '../identity/StoreSelect.vue'
import { errorText } from '../../shared/presentation/zh'
import { uiText } from '../../shared/presentation/zh'
import { computed, ref, watch } from 'vue'
import { useStoreOptions } from '../../shared/components/useStoreOptions'
import { loadEmployeeRentalRequests } from '../customerrequest/employeeRequestApi'
import EquipmentPicker from '../equipment/EquipmentPicker.vue'
import { loadCatalog } from '../equipment/equipmentCatalogApi'
import { conciseEquipmentName } from '../equipment/equipmentDisplay'
import RentalInvoice from './RentalInvoice.vue'
import type { AvailableEquipment } from '../equipment/equipmentAvailabilityApi'
import { rentalApi, type Contract, type ChangeRequest, type TransactionInput, type RecordedTransaction } from './rentalApi'

const storeId = ref('')
const sourceRequestId = ref('')
const {
  options: requests, loading: requestsLoading, error: requestsError, refresh: refreshRequests,
} = useStoreOptions(storeId, async id => (await loadEmployeeRentalRequests(id)).filter(row => row.status === 'ACCEPTED'))
const selection = ref<AvailableEquipment[]>([])
const replacementSelection = ref<AvailableEquipment[]>([])
const contracts = ref<Contract[]>([])
const changes = ref<ChangeRequest[]>([])
const contract = ref<Contract | null>(null)
const pendingItems = ref<string[]>([])
const busy = ref(false)
const error = ref('')
const result = ref<TransactionInput>({ type: 'PAYMENT', externalId: '', amount: '', status: 'PENDING_CONFIRMATION', processedAt: null })
const chargeId = ref('')
const history = ref<RecordedTransaction[]>([])
const invoiceOpen = ref(false)
const invoiceVersion = ref(0)
const pickerGeneration = ref(0)
const replacementGeneration = ref(0)
const contractStore = computed(() => contract.value ? storeId.value : '')
const { options: catalog } = useStoreOptions(contractStore, id => loadCatalog(id, '', ''))
const equipmentById = computed(() => new Map(catalog.value.map(item => [item.equipmentId, item])))
const selectedRequest = computed(() => requests.value.find(row => row.requestId === sourceRequestId.value) ?? null)
const requestRequirements = computed(() => {
  const counts = new Map<string, number>()
  selectedRequest.value?.items.forEach(item => counts.set(
    item.equipmentCategory,
    (counts.get(item.equipmentCategory) ?? 0) + item.quantity,
  ))
  return [...counts].map(([equipmentCategory, quantity]) => ({ equipmentCategory, quantity }))
})
const selectionMatchesRequest = computed(() => {
  if (!selectedRequest.value) return false
  const actual = new Map<string, number>()
  selection.value.forEach(item => actual.set(
    item.equipmentCategory,
    (actual.get(item.equipmentCategory) ?? 0) + 1,
  ))
  return selection.value.length === requestRequirements.value.reduce((sum, item) => sum + item.quantity, 0)
    && selection.value.every(item => item.skillLevel === selectedRequest.value?.skillLevel)
    && requestRequirements.value.every(item => actual.get(item.equipmentCategory) === item.quantity)
})
watch(storeId, () => { sourceRequestId.value = ''; contracts.value = []; changes.value = []; contract.value = null; selection.value = []; replacementSelection.value = []; pendingItems.value = []; chargeId.value = ''; history.value = [] })
async function run(operation: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try { await operation() } catch (failure) { error.value = failure instanceof Error ? errorText(failure) : '操作失败，已保留您的输入。' }
  finally { busy.value = false }
}
function selectContract(value: Contract) {
  if (contract.value?.contractId !== value.contractId) {
    replacementSelection.value = []
    replacementGeneration.value++
    invoiceOpen.value = false
  }
  contract.value = value; pendingItems.value = []; history.value = []; chargeId.value = ''
  invoiceVersion.value++
}
function load() { return run(async () => {
  refreshRequests()
  const [listedContracts, listedChanges] = await Promise.all([rentalApi.list(storeId.value), rentalApi.changes(storeId.value)])
  contracts.value = listedContracts
  changes.value = listedChanges
}) }
function pickContract(event: Event) {
  const id = (event.target as HTMLSelectElement).value
  const chosen = contracts.value.find(row => row.contractId === id)
  if (chosen) selectContract(chosen)
}
function selectRequest(requestId: string) {
  if (sourceRequestId.value === requestId) return
  sourceRequestId.value = requestId
  selection.value = []
  pickerGeneration.value++
}
function create() { return run(async () => {
  if (requestsLoading.value || !selectionMatchesRequest.value) return
  selectContract(await rentalApi.create(storeId.value, sourceRequestId.value, selection.value.map(i => ({ equipmentId: i.equipmentId, expectedDailyRate: i.currentDailyRate }))))
  sourceRequestId.value = ''
  selection.value = []
  pickerGeneration.value++
  refreshRequests()
  contracts.value = await rentalApi.list(storeId.value)
}) }
function refresh() { return run(async () => { if (contract.value) selectContract(await rentalApi.read(contract.value.contractId)) }) }
function revise() { return run(async () => {
  if (!contract.value) return
  selectContract(await rentalApi.revise(contract.value.contractId, pendingItems.value, replacementSelection.value.map(i => ({ equipmentId: i.equipmentId, expectedDailyRate: i.currentDailyRate }))))
  replacementSelection.value = []
  replacementGeneration.value++
}) }
function equipmentLabel(equipmentId: string): string {
  const item = equipmentById.value.get(equipmentId)
  return item ? `${conciseEquipmentName(item.equipmentName)} · ${uiText(item.equipmentCategory)} · ${item.inventoryNumber}` : `雪具 ${equipmentId}`
}
function action(kind: 'submit' | 'cancel' | 'issue') { return run(async () => {
  if (!contract.value) return
  selectContract(kind === 'issue' ? await rentalApi.issue(contract.value.contractId, pendingItems.value) : await rentalApi[kind](contract.value.contractId))
}) }
function record() { return run(async () => {
  await rentalApi.record(chargeId.value, { ...result.value, processedAt: result.value.processedAt || null })
  history.value = await rentalApi.transactions(chargeId.value)
  if (contract.value) contract.value = await rentalApi.read(contract.value.contractId)
  invoiceVersion.value++
}) }
function showHistory() { return run(async () => { history.value = await rentalApi.transactions(chargeId.value) }) }
</script>
<template>
  <section>
    <h1>租赁办理</h1>
    <p>在授权门店办理租赁。顾客可在小程序查看发票并体验模拟付款；外部付款仍在此核实登记。</p>
    <StoreSelect
      v-model="storeId"
      label="门店编号"
      role="EMPLOYEE"
      :disabled="busy"
    />
    <button
      :disabled="busy || !storeId"
      @click="load"
    >
      查询合同与变更需求
    </button>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <h2>根据已受理需求创建合同</h2>
    <p v-if="requestsLoading">
      正在加载需求…
    </p>
    <p
      v-else-if="!requests.length"
      class="empty-state"
    >
      暂无已受理需求
    </p>
    <div
      v-else
      class="request-grid"
      aria-label="已受理需求"
    >
      <button
        v-for="row in requests"
        :key="row.requestId"
        type="button"
        class="request-choice"
        :class="{ selected: row.requestId === sourceRequestId }"
        :aria-label="`选择需求 ${row.requestNumber}`"
        :aria-pressed="row.requestId === sourceRequestId"
        :disabled="busy"
        @click="selectRequest(row.requestId)"
      >
        <span class="request-number">{{ row.requestNumber }}</span>
        <span class="request-meta">{{ row.rentalStartDate }} · {{ row.rentalDays }} 天 · {{ uiText(row.skillLevel) }}</span>
        <span class="request-thumbnails">
          <span
            v-for="(item, index) in row.items"
            :key="index"
            class="demand-thumbnail"
          >
            <span aria-hidden="true">{{ uiText(item.equipmentCategory).slice(0, 2) }}</span>
            {{ uiText(item.equipmentCategory) }} × {{ item.quantity }}
          </span>
        </span>
      </button>
    </div>
    <p
      v-if="requestsError"
      role="alert"
    >
      {{ requestsError }}
    </p>
    <article
      v-if="selectedRequest"
      class="request-detail"
      aria-label="已选需求详情"
    >
      <header>
        <div>
          <p class="eyebrow">
            已选需求
          </p>
          <h3>{{ selectedRequest.requestNumber }}</h3>
        </div>
        <p>顾客 {{ selectedRequest.customerId }}</p>
      </header>
      <dl>
        <dt>起租安排</dt><dd>{{ selectedRequest.rentalStartDate }} 起 · {{ selectedRequest.rentalDays }} 个整日</dd>
        <dt>技术等级</dt><dd>{{ uiText(selectedRequest.skillLevel) }}</dd>
        <template
          v-for="(item, index) in selectedRequest.items"
          :key="index"
        >
          <dt>{{ uiText(item.equipmentCategory) }}</dt>
          <dd>数量 {{ item.quantity }}<span v-if="item.fittingNote"> · {{ item.fittingNote }}</span></dd>
        </template>
      </dl>
      <details v-if="selectedRequest.supplements.length">
        <summary>顾客补充资料</summary>
        <ul>
          <li
            v-for="cycle in selectedRequest.supplements"
            :key="cycle.cycleNumber"
          >
            {{ cycle.reason }} · {{ cycle.customerResponse || '尚未回复' }}
          </li>
        </ul>
      </details>
      <p class="workflow-note">
        请按以上需求配齐雪具；合同经顾客确认并完成费用处理后，再办理实物发放。
      </p>
    </article>
    <EquipmentPicker
      v-if="selectedRequest"
      :key="pickerGeneration"
      :store-id="storeId"
      :disabled="busy"
      :requirements="requestRequirements"
      :preferred-skill-level="selectedRequest?.skillLevel"
      @selection="selection = $event"
    />
    <button
      :disabled="busy || requestsLoading || !selectionMatchesRequest"
      @click="create"
    >
      创建合同草稿
    </button>
    <label v-if="contracts.length">门店合同<select
      :value="contract?.contractId ?? ''"
      aria-label="门店合同"
      :disabled="busy"
      @change="pickContract"
    >
      <option
        value=""
        disabled
      >选择合同</option>
      <option
        v-for="c in contracts"
        :key="c.contractId"
        :value="c.contractId"
      >
        {{ c.contractNumber }} · {{ uiText(c.status) }} · {{ c.amountDue }} NOK
      </option>
    </select></label>
    <article v-if="contract">
      <h2>{{ contract.contractNumber }} · {{ uiText(contract.status) }}</h2>
      <p>顾客 {{ contract.customerId }} · {{ contract.rentalStartDate }} · {{ contract.rentalDays }} 整日 · 合同应付合计 {{ contract.amountDue }}</p>
      <button
        type="button"
        @click="invoiceOpen = !invoiceOpen"
      >
        {{ invoiceOpen ? '收起发票' : '查看发票与付款凭证' }}
      </button>
      <RentalInvoice
        v-if="invoiceOpen"
        :key="contract.contractId"
        :contract-id="contract.contractId"
        :version="invoiceVersion"
      />
      <p aria-label="计费依据">
        初始租金 = 逐件日租金快照 × 约定整日天数后求和。单价取建约时的装备定价，历史快照不随现价变动。
        按 Europe/Oslo 本地日历整日计费，不按小时折算。普通提前归还不自动退款；正式调整只退尚未开始的剩余整日，新增装备包含调整当日。调整补缴或退款另列费用，以服务端核定金额为准。合同应付合计包含已确认调整，不是当前未付余额。
      </p>
      <button
        :disabled="busy"
        @click="refresh"
      >
        刷新顾客确认状态
      </button>
      <button
        v-if="contract.status === 'DRAFT'"
        :disabled="busy"
        @click="action('submit')"
      >
        提交顾客确认
      </button>
      <button
        v-if="!contract.items.some(i => i.checkedOutAt !== null) && !['CANCELLED','ENDED'].includes(contract.status)"
        :disabled="busy"
        @click="action('cancel')"
      >
        发放前取消合同
      </button>
      <ul>
        <li
          v-for="i in contract.items"
          :key="i.itemId"
        >
          <label><input
            v-if="i.active && i.status === 'PENDING_ISSUE'"
            v-model="pendingItems"
            type="checkbox"
            :value="i.itemId"
          >{{ equipmentLabel(i.equipmentId) }} · 日租金快照 {{ i.dailyRateSnapshot }} / 天 · {{ uiText(i.status) }} {{ i.active ? '' : '（已失效历史项）' }} · 已发放 {{ i.checkedOutAt || '未发放' }} · 已归还 {{ i.returnedAt || '未归还' }}</label>
        </li>
      </ul>
      <EquipmentPicker
        v-if="contract.status === 'DRAFT'"
        v-show="pendingItems.length"
        :key="replacementGeneration"
        :store-id="storeId"
        :disabled="busy"
        @selection="replacementSelection = $event"
      />
      <button
        v-if="contract.status === 'DRAFT'"
        :disabled="busy || !pendingItems.length || !replacementSelection.length"
        @click="revise"
      >
        替换所选草稿雪具并重新确认
      </button>
      <button
        v-if="['PENDING_ISSUE','ACTIVE'].includes(contract.status)"
        :disabled="busy || pendingItems.length === 0"
        @click="action('issue')"
      >
        登记所选雪具发放
      </button>
      <h3>费用明细</h3>
      <label>费用<select v-model="chargeId"><option value="">选择费用项</option><option
        v-for="c in contract.charges"
        :key="c.chargeId"
        :value="c.chargeId"
      >{{ uiText(c.type) }} · {{ c.amount }} · {{ uiText(c.status) }}</option></select></label>
      <button
        :disabled="busy || !chargeId"
        @click="showHistory"
      >
        查看历史交易记录
      </button>
      <form @submit.prevent="record">
        <label>类型<select v-model="result.type"><option value="PAYMENT">付款</option><option value="SUPPLEMENT">补缴</option><option value="REFUND">退款</option></select></label>
        <label>外部交易编号<input
          v-model="result.externalId"
          maxlength="100"
          required
        ></label>
        <label>金额<input
          v-model="result.amount"
          placeholder="418.50"
          required
        ></label>
        <label>结果<select v-model="result.status"><option value="PENDING_CONFIRMATION">待确认</option><option value="SUCCEEDED">成功</option><option value="FAILED">失败</option></select></label>
        <label>第三方处理时间（UTC ISO）<input
          v-model="result.processedAt"
          placeholder="2026-10-10T09:00:00Z"
        ></label>
        <button :disabled="busy || !chargeId">
          登记外部交易结果
        </button>
      </form>
      <ul>
        <li
          v-for="t in history"
          :key="t.transactionId"
        >
          {{ t.source === 'SIMULATED' ? '顾客模拟付款（不扣款）' : '外部交易（员工登记）' }} · {{ t.externalId }} · {{ uiText(t.type) }} · {{ t.amount }} · {{ uiText(t.status) }} · {{ t.processedAt }}
        </li>
      </ul>
    </article>
    <h2>顾客提交的变更意向</h2>
    <ul>
      <li
        v-for="c in changes"
        :key="c.changeId"
      >
        {{ c.changeNumber }} · 合同 {{ c.contractId }} · {{ uiText(c.type) }} · {{ c.note }} · {{ c.requestedAt }}
      </li>
    </ul>
  </section>
</template>
<style scoped>
label { display: block; margin-block: .7rem; }
input, select, button { padding: .6rem; margin: .3rem; }
article { border: 1px solid #ccd4df; padding: 1rem; margin-block: 1rem; }
.request-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 16rem), 1fr)); gap: .75rem; margin-block: .75rem; }
.request-choice { display: grid; gap: .55rem; width: 100%; margin: 0; border: 1px solid #cbd8e3; border-radius: .8rem; background: #fff; color: #183b4b; text-align: left; overflow-wrap: anywhere; }
.request-choice:hover { border-color: #4d8098; background: #f5fafc; }
.request-choice.selected { border-color: #176f8c; box-shadow: 0 0 0 2px #cdebf5; background: #f2fbfe; }
.request-number { font-size: 1.05rem; font-weight: 750; }
.request-meta { color: #506675; font-size: .92rem; }
.request-thumbnails { display: flex; flex-wrap: wrap; gap: .4rem; }
.demand-thumbnail { display: inline-flex; align-items: center; gap: .35rem; font-size: .88rem; }
.demand-thumbnail > span { display: grid; place-items: center; width: 2rem; height: 2rem; border-radius: .45rem; background: #dff1f7; color: #145873; font-size: .72rem; font-weight: 700; }
.request-detail { border-color: #a9c9d7; border-radius: .8rem; background: linear-gradient(135deg, #f8fcfe, #eef7fa); }
.request-detail header { display: flex; justify-content: space-between; gap: 1rem; align-items: flex-start; }
.request-detail h3, .request-detail p { margin-block: 0 .45rem; }
.request-detail dl { display: grid; grid-template-columns: minmax(6rem, auto) 1fr; gap: .4rem 1rem; }
.request-detail dt { font-weight: 700; }
.request-detail dd { margin: 0; }
.eyebrow { color: #176f8c; font-size: .78rem; font-weight: 700; letter-spacing: .08em; }
.workflow-note { margin-top: .8rem !important; color: #4b6170; }
.empty-state { color: #596c78; }
[role="alert"] { color: #9b1c1c; }
</style>

<script setup lang="ts">
import StoreSelect from '../identity/StoreSelect.vue'
import { errorText } from '../../shared/presentation/zh'
import { uiText } from '../../shared/presentation/zh'
import { ref, watch } from 'vue'
import { useStoreOptions } from '../../shared/components/useStoreOptions'
import { loadEmployeeRentalRequests } from '../customerrequest/employeeRequestApi'
import EquipmentPicker from '../equipment/EquipmentPicker.vue'
import RentalInvoice from './RentalInvoice.vue'
import type { AvailableEquipment } from '../equipment/equipmentAvailabilityApi'
import { rentalApi, type Contract, type ChangeRequest, type TransactionInput, type RecordedTransaction } from './rentalApi'

const storeId = ref('')
const sourceRequestId = ref('')
const {
  options: requests, loading: requestsLoading, error: requestsError, refresh: refreshRequests,
} = useStoreOptions(storeId, async id => (await loadEmployeeRentalRequests(id)).filter(row => row.status === 'ACCEPTED'))
const selection = ref<AvailableEquipment[]>([])
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
watch(storeId, () => { sourceRequestId.value = ''; contracts.value = []; changes.value = []; contract.value = null; selection.value = []; pendingItems.value = []; chargeId.value = ''; history.value = [] })
async function run(operation: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try { await operation() } catch (failure) { error.value = failure instanceof Error ? errorText(failure) : '操作失败，已保留您的输入。' }
  finally { busy.value = false }
}
function selectContract(value: Contract) {
  if (contract.value?.contractId !== value.contractId) { selection.value = []; pickerGeneration.value++; invoiceOpen.value = false }
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
function create() { return run(async () => {
  if (!requests.value.some(row => row.requestId === sourceRequestId.value)) return
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
  selectContract(await rentalApi.revise(contract.value.contractId, pendingItems.value, selection.value.map(i => ({ equipmentId: i.equipmentId, expectedDailyRate: i.currentDailyRate }))))
  selection.value = []; pickerGeneration.value++
}) }
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
    <label>已受理需求<select
      v-model="sourceRequestId"
      aria-label="已受理需求"
      :disabled="busy || requestsLoading || !requests.length"
    >
      <option
        value=""
        disabled
      >{{ requestsLoading ? '正在加载需求…' : requests.length ? '选择需求单' : '暂无已受理需求' }}</option>
      <option
        v-for="row in requests"
        :key="row.requestId"
        :value="row.requestId"
      >
        {{ row.requestNumber }} · {{ row.rentalStartDate }} · {{ row.rentalDays }} 天
      </option>
    </select></label>
    <p
      v-if="requestsError"
      role="alert"
    >
      {{ requestsError }}
    </p>
    <EquipmentPicker
      :key="pickerGeneration"
      :store-id="storeId"
      :disabled="busy"
      @selection="selection = $event"
    />
    <button
      :disabled="busy || requestsLoading || !requests.some(row => row.requestId === sourceRequestId) || selection.length === 0"
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
          >雪具 {{ i.equipmentId }} · 日租金快照 {{ i.dailyRateSnapshot }} / 天 · {{ uiText(i.status) }} {{ i.active ? '' : '（已失效历史项）' }} · 已发放 {{ i.checkedOutAt || '未发放' }} · 已归还 {{ i.returnedAt || '未归还' }}</label>
        </li>
      </ul>
      <button
        v-if="contract.status === 'DRAFT'"
        :disabled="busy || !pendingItems.length || !selection.length"
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
[role="alert"] { color: #9b1c1c; }
</style>

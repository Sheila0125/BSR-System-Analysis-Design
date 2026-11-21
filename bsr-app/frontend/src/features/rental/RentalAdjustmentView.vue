<script setup lang="ts">
import StoreSelect from '../identity/StoreSelect.vue'
import { errorText } from '../../shared/presentation/zh'
import { uiText } from '../../shared/presentation/zh'
import { ref, watch } from 'vue'
import { useStoreOptions } from '../../shared/components/useStoreOptions'
import EquipmentPicker from '../equipment/EquipmentPicker.vue'
import type { AvailableEquipment } from '../equipment/equipmentAvailabilityApi'
import { rentalApi, type Adjustment, type ChangeRequest, type TransactionInput, type RecordedTransaction } from './rentalApi'

const storeId = ref(''), contractId = ref(''), changeId = ref(''), type = ref('REPLACE')
const { options: contracts, loading: contractsLoading, error: contractsError } = useStoreOptions(storeId, rentalApi.list)
const manualContract = ref(false), manualChange = ref(false)
const detail = ref<Adjustment | null>(null), queue = ref<ChangeRequest[]>([])
const selection = ref<AvailableEquipment[]>([]), returned = ref<string[]>([]), issued = ref<string[]>([])
const outcomes = ref<Record<string, string>>({}), notes = ref<Record<string, string>>({})
const busy = ref(false), error = ref(''), pickerGeneration = ref(0), chargeId = ref('')
const history = ref<RecordedTransaction[]>([])
const result = ref<TransactionInput>({ type: 'SUPPLEMENT', externalId: '', amount: '', status: 'PENDING_CONFIRMATION', processedAt: null })
watch(storeId, () => { contractId.value = ''; changeId.value = ''; detail.value = null; queue.value = []; selection.value = []; returned.value = []; issued.value = []; outcomes.value = {}; notes.value = {}; history.value = []; chargeId.value = '' })
async function run(operation: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await operation() } catch (failure) { error.value = failure instanceof Error ? errorText(failure) : '操作失败，已保留您的输入。' }
  finally { busy.value = false }
}
function apply(value: Adjustment) {
  if (value.change.requestedStoreId !== storeId.value) throw new Error('请先选择实际办理门店。')
  if (detail.value?.change.changeId !== value.change.changeId) {
    selection.value = []; returned.value = []; issued.value = []; outcomes.value = {}; notes.value = {}
    chargeId.value = ''; history.value = []; pickerGeneration.value++
  }
  detail.value = value; changeId.value = value.change.changeId; contractId.value = value.change.contractId
}
function load() { return run(async () => { apply(await rentalApi.adjustment(changeId.value)) }) }
function loadQueue() { return run(async () => { queue.value = await rentalApi.changes(storeId.value) }) }
function start() { return run(async () => { apply(await rentalApi.startAdjustment(contractId.value, storeId.value, type.value)) }) }
function receive() { return run(async () => {
  apply(await rentalApi.receiveAdjustment(detail.value!.change.changeId, returned.value.map(itemId => ({ itemId, outcome: outcomes.value[itemId] ?? 'NORMAL', faultDescription: notes.value[itemId] || null }))))
  returned.value = []
}) }
function quote() { return run(async () => {
  apply(await rentalApi.quoteAdjustment(detail.value!.change.changeId, selection.value.map(i => ({ equipmentId: i.equipmentId, expectedDailyRate: i.currentDailyRate }))))
  selection.value = []; pickerGeneration.value++
}) }
function stop() { return run(async () => { apply(await rentalApi.stopAdjustment(detail.value!.change.changeId)); selection.value = []; pickerGeneration.value++ }) }
function record() { return run(async () => {
  await rentalApi.record(chargeId.value, { ...result.value, processedAt: result.value.processedAt || null })
  history.value = await rentalApi.transactions(chargeId.value)
  apply(await rentalApi.adjustment(detail.value!.change.changeId))
}) }
function showHistory() { return run(async () => { history.value = await rentalApi.transactions(chargeId.value) }) }
function issue() { return run(async () => {
  await rentalApi.issueAtStore(detail.value!.change.contractId, storeId.value, issued.value)
  apply(await rentalApi.adjustment(detail.value!.change.changeId)); issued.value = []
}) }
</script>
<template>
  <section>
    <h1>正式租赁调整</h1>
    <p>先接收并检查实物，后续选具或外部交易失败不会撤销已提交的归还。最终报价由顾客在小程序确认。</p>
    <StoreSelect
      v-model="storeId"
      label="调整办理门店编号"
      role="EMPLOYEE"
      :disabled="busy"
    />
    <button
      :disabled="busy || !storeId"
      @click="loadQueue"
    >
      查询门店需求
    </button>
    <ul>
      <li
        v-for="c in queue"
        :key="c.changeId"
      >
        {{ c.changeNumber }} · {{ uiText(c.type) }} · {{ uiText(c.status) }} · {{ c.note }}
        <p
          v-if="c.requestedReturnItemIds?.length"
          :data-test="`return-intent-${c.changeId}`"
        >
          顾客拟退回项目：{{ c.requestedReturnItemIds.join('、') }}（仅意向，尚未归还）
        </p>
      </li>
    </ul>
    <h2>发起调整</h2>
    <label v-if="manualContract">合同编号<input
      v-model.trim="contractId"
      inputmode="numeric"
      :disabled="busy"
    ></label>
    <label v-else>合同<select
      v-model="contractId"
      aria-label="合同"
      :disabled="busy || contractsLoading || !contracts.length"
    >
      <option
        value=""
        disabled
      >{{ contractsLoading ? '正在加载合同…' : contracts.length ? '选择合同' : '本店暂无合同' }}</option>
      <option
        v-for="row in contracts"
        :key="row.contractId"
        :value="row.contractId"
      >
        {{ row.contractNumber }} · {{ uiText(row.status) }}
      </option>
    </select></label>
    <button
      type="button"
      :disabled="busy"
      @click="manualContract = !manualContract; contractId = detail?.change.contractId ?? ''"
    >
      {{ manualContract ? '返回本店合同列表' : '按合同编号办理' }}
    </button>
    <p
      v-if="!manualContract && contractsError"
      role="alert"
    >
      {{ contractsError }}
    </p>
    <label>类型<select
      v-model="type"
      :disabled="busy"
    ><option value="ADD">新增</option><option value="REMOVE">减少</option><option value="REPLACE">更换</option><option value="PARTIAL_RETURN">部分归还</option></select></label>
    <button
      :disabled="busy || !storeId || !contractId"
      @click="start"
    >
      开始办理调整
    </button>
    <h2>查询已提交的调整</h2>
    <label v-if="manualChange">变更编号<input
      v-model.trim="changeId"
      inputmode="numeric"
      :disabled="busy"
    ></label>
    <label v-else>门店变更需求<select
      v-model="changeId"
      aria-label="门店变更需求"
      :disabled="busy || !queue.length"
    >
      <option
        value=""
        disabled
      >{{ queue.length ? '选择变更单' : '请先查询门店需求' }}</option>
      <option
        v-for="row in queue"
        :key="row.changeId"
        :value="row.changeId"
      >
        {{ row.changeNumber }} · {{ uiText(row.type) }} · {{ uiText(row.status) }}
      </option>
    </select></label>
    <button
      type="button"
      :disabled="busy"
      @click="manualChange = !manualChange; changeId = detail?.change.changeId ?? ''"
    >
      {{ manualChange ? '返回门店变更列表' : '按变更编号查询' }}
    </button>
    <button
      data-test="load-adjustment"
      :disabled="busy || !storeId || !changeId"
      @click="load"
    >
      查询或刷新调整
    </button>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <article v-if="detail">
      <h2>{{ uiText(detail.change.type) }} · {{ uiText(detail.change.status) }}</h2>
      <p v-if="detail.change.requestedReturnItemIds?.length">
        申请时拟退回项目：{{ detail.change.requestedReturnItemIds.join('、') }}。以下实物接收请按实际检查结果另行选择。
      </p>
      <p>生效日期 {{ detail.change.effectiveDate }} · 最终差额 {{ detail.change.amountDelta ?? '尚未报价' }} · 员工 {{ detail.change.handledByEmployeeId }}</p>
      <p
        v-if="detail.items.some(i => i.active && i.status === 'RETURNED' && !i.effect)"
        data-test="follow-up-refund-help"
      >
        已归还雪具可以继续办理正式退款，无需重复接收或检查实物。
      </p>
      <ul>
        <li
          v-for="i in detail.items"
          :key="i.itemId"
        >
          <label><input
            v-if="!detail.change.amountDelta && detail.change.type !== 'ADD' && i.active && ['RENTED','RETURNED'].includes(i.status) && !i.effect"
            v-model="returned"
            type="checkbox"
            :value="i.itemId"
            :disabled="busy"
          >雪具 {{ i.equipmentId }} · {{ uiText(i.status) }} · {{ uiText(i.effect) }} · 日租金 {{ i.dailyRateSnapshot ?? '不计入本次报价' }}</label>
          <template v-if="returned.includes(i.itemId)">
            <select
              v-model="outcomes[i.itemId]"
              :disabled="busy"
            >
              <option value="NORMAL">
                正常
              </option><option value="FAULT">
                故障
              </option><option value="PENDING_REVIEW">
                待复核
              </option>
            </select><input
              v-model="notes[i.itemId]"
              placeholder="检查备注"
              :disabled="busy"
            >
          </template>
          <label v-if="detail.change.status === 'COMPLETED' && i.active && i.status === 'PENDING_ISSUE' && ['ADD','REPLACE_IN'].includes(i.effect ?? '')"><input
            v-model="issued"
            type="checkbox"
            :value="i.itemId"
            :disabled="busy"
          >选择实际发放的新雪具</label>
        </li>
      </ul>
      <button
        :disabled="busy || !returned.length || detail.change.amountDelta !== null"
        @click="receive"
      >
        确认实物接收与检查
      </button>
      <EquipmentPicker
        v-if="['ADD','REPLACE'].includes(detail.change.type) && detail.change.amountDelta === null"
        :key="pickerGeneration"
        :store-id="storeId"
        :disabled="busy"
        @selection="selection = $event"
      />
      <button
        data-test="quote-adjustment"
        :disabled="busy || !['SUBMITTED','PROCESSING'].includes(detail.change.status) || detail.change.amountDelta !== null || (['ADD','REPLACE'].includes(detail.change.type) && !selection.length)"
        @click="quote"
      >
        保存报价并等待顾客确认
      </button>
      <button
        v-if="['SUBMITTED','PROCESSING'].includes(detail.change.status)"
        :disabled="busy"
        @click="stop"
      >
        结束未完成调整并释放未发放雪具
      </button>
      <p v-if="detail.change.status === 'PROCESSING' && detail.change.amountDelta !== null">
        等待顾客确认调整内容与金额，暂不能发放新增雪具。
      </p>
      <label>调整费用<select v-model="chargeId"><option value="">选择费用项</option><option
        v-for="c in detail.charges"
        :key="c.chargeId"
        :value="c.chargeId"
      >{{ uiText(c.type) }} · {{ c.amount }} · {{ uiText(c.status) }}</option></select></label>
      <button
        :disabled="busy || !chargeId"
        @click="showHistory"
      >
        查看历史外部交易
      </button>
      <form @submit.prevent="record">
        <label>类型<select v-model="result.type"><option value="SUPPLEMENT">补缴</option><option value="REFUND">退款</option></select></label><label>外部交易编号<input
          v-model="result.externalId"
          maxlength="100"
          required
        ></label><label>金额<input
          v-model="result.amount"
          required
        ></label><label>结果<select v-model="result.status"><option value="PENDING_CONFIRMATION">待确认</option><option value="SUCCEEDED">成功</option><option value="FAILED">失败</option></select></label><label>第三方处理时间（UTC）<input
          v-model="result.processedAt"
          placeholder="2026-10-10T09:00:00Z"
        ></label><button :disabled="busy || !chargeId">
          登记已核实的外部交易结果
        </button>
      </form>
      <ul>
        <li
          v-for="t in history"
          :key="t.transactionId"
        >
          {{ t.externalId }} · {{ t.amount }} · {{ uiText(t.status) }} · {{ t.processedAt }}
        </li>
      </ul>
      <button
        :disabled="busy || !issued.length || detail.charges.some(c => c.type !== 'REFUND' && c.status !== 'SETTLED')"
        @click="issue"
      >
        登记所选雪具发放
      </button>
    </article>
  </section>
</template>

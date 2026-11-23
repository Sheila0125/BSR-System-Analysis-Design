<script setup lang="ts">
import StoreSelect from '../identity/StoreSelect.vue'
import { errorText } from '../../shared/presentation/zh'
import { uiText } from '../../shared/presentation/zh'
import { computed, ref, watch } from 'vue'
import { useStoreOptions } from '../../shared/components/useStoreOptions'
import { rentalApi, type ReturnReceipt } from './rentalApi'
const storeId = ref('')
const contractId = ref('')
const manualContract = ref(false)
const { options: contracts, loading: contractsLoading, error: contractsError } = useStoreOptions(storeId, rentalApi.list)
const returnable = computed(() => contracts.value.filter(row => row.items.some(item => item.active && item.status === 'RENTED')))
watch([storeId, manualContract], () => { contractId.value = '' })
const receipt = ref<ReturnReceipt | null>(null)
const selected = ref<string[]>([])
const returnableItems = computed(() => receipt.value?.items.filter(item => item.status === 'RENTED') ?? [])
const outcome = ref('NORMAL')
const note = ref('')
const busy = ref(false)
const error = ref('')
watch([storeId, contractId], () => {
  receipt.value = null
  selected.value = []
  outcome.value = 'NORMAL'
  note.value = ''
  error.value = ''
})
watch(contractId, id => {
  if (id && storeId.value && !manualContract.value) void lookup()
}, { flush: 'post' })
async function run(operation: () => Promise<ReturnReceipt>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try { receipt.value = await operation(); selected.value = [] }
  catch (failure) { error.value = failure instanceof Error ? errorText(failure) : '归还登记失败，请妥善保管实物，已保留您的输入。' }
  finally { busy.value = false }
}
function lookup() { return run(() => rentalApi.returnLookup(contractId.value, storeId.value)) }
function receive() { return run(async () => {
  const result = await rentalApi.returns(contractId.value, storeId.value, selected.value.map(itemId => ({
    itemId, outcome: outcome.value, faultDescription: note.value || null,
  })))
  outcome.value = 'NORMAL'
  note.value = ''
  return result
}) }
</script>
<template>
  <section>
    <h1>归还接收与检查</h1>
    <p>请选择实际接收门店，可办理跨店归还。普通提前归还不自动退款。</p>
    <StoreSelect
      v-model="storeId"
      label="接收门店编号"
      role="EMPLOYEE"
      :disabled="busy"
    />
    <label v-if="manualContract">合同编号<input
      v-model.trim="contractId"
      inputmode="numeric"
      :disabled="busy"
    ></label>
    <label v-else>待归还合同<select
      v-model="contractId"
      aria-label="待归还合同"
      :disabled="busy || contractsLoading || !returnable.length"
    >
      <option
        value=""
        disabled
      >{{ contractsLoading ? '正在加载合同…' : returnable.length ? '选择合同' : '本店暂无待归还合同' }}</option>
      <option
        v-for="row in returnable"
        :key="row.contractId"
        :value="row.contractId"
      >
        {{ row.contractNumber }} · {{ row.rentalStartDate }} · {{ uiText(row.status) }}
      </option>
    </select></label>
    <button
      type="button"
      :disabled="busy"
      @click="manualContract = !manualContract"
    >
      {{ manualContract ? '返回本店合同列表' : '跨店或按合同编号查询' }}
    </button>
    <p
      v-if="!manualContract && contractsError"
      role="alert"
    >
      {{ contractsError }}
    </p>
    <button
      :disabled="busy || !storeId || !contractId"
      @click="lookup"
    >
      {{ busy ? '正在处理…' : manualContract ? '查询归还所需信息' : '刷新归还所需信息' }}
    </button>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <article v-if="receipt">
      <h2>合同 {{ receipt.contractId }} · {{ uiText(receipt.status) }}</h2>
      <ul>
        <li
          v-for="i in receipt.items"
          :key="i.itemId"
        >
          <label><input
            v-if="i.status === 'RENTED'"
            v-model="selected"
            type="checkbox"
            :value="i.itemId"
          >雪具 {{ i.equipmentId }} · {{ uiText(i.status) }} · {{ i.returnedAt }}</label>
        </li>
      </ul>
      <button
        v-if="returnableItems.length"
        type="button"
        :disabled="busy"
        @click="selected = selected.length === returnableItems.length ? [] : returnableItems.map(item => item.itemId)"
      >
        {{ selected.length === returnableItems.length ? '清除归还选择' : '选中全部可归还雪具' }}
      </button>
      <label>检查结果<select v-model="outcome"><option value="NORMAL">正常</option><option value="FAULT">故障</option><option value="PENDING_REVIEW">待复核</option></select></label>
      <label>故障或差异说明<textarea
        v-model="note"
        maxlength="500"
      /></label>
      <button
        :disabled="busy || selected.length === 0"
        @click="receive"
      >
        确认实物接收与检查
      </button>
    </article>
  </section>
</template>

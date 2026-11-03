<script setup lang="ts">
import { errorText } from '../../shared/presentation/zh'
import { uiText } from '../../shared/presentation/zh'
import { ref, watch } from 'vue'
import { rentalApi, type ReturnReceipt } from './rentalApi'
const storeId = ref('')
const contractId = ref('')
const receipt = ref<ReturnReceipt | null>(null)
const selected = ref<string[]>([])
const outcome = ref('NORMAL')
const note = ref('')
const busy = ref(false)
const error = ref('')
watch([storeId, contractId], () => { receipt.value = null; selected.value = [] })
async function run(operation: () => Promise<ReturnReceipt>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try { receipt.value = await operation(); selected.value = [] }
  catch (failure) { error.value = failure instanceof Error ? errorText(failure) : '归还登记失败，请妥善保管实物，已保留您的输入。' }
  finally { busy.value = false }
}
function lookup() { return run(() => rentalApi.returnLookup(contractId.value, storeId.value)) }
function receive() { return run(() => rentalApi.returns(contractId.value, storeId.value, selected.value.map(itemId => ({ itemId, outcome: outcome.value, faultDescription: note.value || null })))) }
</script>
<template>
  <section>
    <h1>归还接收与检查</h1>
    <p>请填写实际接收门店，可办理跨店归还。普通提前归还不自动退款。</p>
    <label>接收门店编号<input v-model.trim="storeId"></label>
    <label>合同编号<input v-model.trim="contractId"></label>
    <button
      :disabled="busy"
      @click="lookup"
    >
      查询归还所需信息
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

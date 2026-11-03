<script setup lang="ts">
import { errorText } from '../../shared/presentation/zh'
import { uiText } from '../../shared/presentation/zh'
import { ref, watch } from 'vue'
import { storeTransferApi, type Transfer } from './storeTransferApi'

const storeId = ref('')
const transfers = ref<Transfer[]>([])
const notes = ref<Record<string, string>>({})
const busy = ref(false)
const error = ref('')
const notice = ref('')
watch(storeId, () => { transfers.value = []; notes.value = {} })
async function run(operation: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try { await operation() } catch (failure) { error.value = failure instanceof Error ? errorText(failure) : '交接操作失败，已保留您的输入。' }
  finally { busy.value = false }
}
function load() { return run(async () => { transfers.value = await storeTransferApi.listTransfers(storeId.value) }) }
function replace(updated: Transfer) { transfers.value = transfers.value.map(transfer => transfer.transferId === updated.transferId ? updated : transfer) }
function dispatch(transferId: string, itemId: string) { return run(async () => {
  replace(await storeTransferApi.dispatch(transferId, itemId)); notice.value = `雪具 ${itemId} 已发出。`
}) }
function receive(transferId: string, itemId: string) { return run(async () => {
  replace(await storeTransferApi.receive(transferId, itemId)); notice.value = `雪具 ${itemId} 已接收。`
}) }
function reportException(transfer: Transfer, itemId: string) { return run(async () => {
  const phase = storeId.value === transfer.sourceStoreId ? 'DISPATCH' : 'RECEIPT'
  replace(await storeTransferApi.reportException(transfer.transferId, itemId, phase, notes.value[itemId] ?? ''))
  notice.value = `雪具 ${itemId} 已登记异常，未登记为实物交接。`
}) }
</script>

<template>
  <section>
    <h1>雪具调拨交接</h1>
    <p>雪具逐件交接，调出门店员工发出，调入门店员工接收。</p>
    <label>交接门店编号<input
      v-model.trim="storeId"
      aria-label="交接门店编号"
    ></label>
    <button
      data-testid="load-handoffs"
      :disabled="busy || !storeId"
      @click="load"
    >
      查询调拨
    </button>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-if="notice"
      role="status"
    >
      {{ notice }}
    </p>
    <section
      v-for="transfer in transfers"
      :key="transfer.transferId"
    >
      <h2>{{ transfer.transferNumber }} · {{ uiText(transfer.status) }}</h2>
      <ul>
        <li
          v-for="item in transfer.items"
          :key="item.itemId"
        >
          雪具 {{ item.equipmentId }} · 发出 {{ uiText(item.dispatchResult) }} · 接收 {{ uiText(item.receiptResult) }}
          <span v-if="item.exceptionNote"> · 异常： {{ item.exceptionNote }}</span>
          <button
            v-if="storeId === transfer.sourceStoreId && transfer.status !== 'INITIATED' && item.dispatchResult !== 'DISPATCHED'"
            :data-testid="`dispatch-${item.itemId}`"
            :disabled="busy"
            @click="dispatch(transfer.transferId, item.itemId)"
          >
            发出
          </button>
          <button
            v-if="storeId === transfer.destinationStoreId && item.dispatchResult === 'DISPATCHED' && item.receiptResult !== 'RECEIVED'"
            :data-testid="`receive-${item.itemId}`"
            :disabled="busy"
            @click="receive(transfer.transferId, item.itemId)"
          >
            接收
          </button>
          <template v-if="(storeId === transfer.sourceStoreId && item.dispatchResult !== 'DISPATCHED') || (storeId === transfer.destinationStoreId && item.dispatchResult === 'DISPATCHED' && item.receiptResult !== 'RECEIVED')">
            <label>异常说明： {{ item.itemId }}<input
              v-model="notes[item.itemId]"
              :aria-label="`异常说明：${item.itemId}`"
            ></label>
            <button
              :data-testid="`exception-${item.itemId}`"
              :disabled="busy || !notes[item.itemId]?.trim()"
              @click="reportException(transfer, item.itemId)"
            >
              登记异常
            </button>
          </template>
        </li>
      </ul>
    </section>
  </section>
</template>

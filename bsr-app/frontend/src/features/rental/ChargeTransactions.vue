<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useStoreOptions } from '../../shared/components/useStoreOptions'
import { errorText, uiText } from '../../shared/presentation/zh'
import { rentalApi, type Charge, type TransactionInput } from './rentalApi'

const props = defineProps<{ charges: Charge[]; disabled: boolean; canRecord: boolean; version?: number }>()
const emit = defineEmits<{ saving: [value: boolean]; recorded: [] }>()
const chargeId = ref('')
const selected = computed(() => props.charges.find(charge => charge.chargeId === chargeId.value))
const { options: history, loading, error: historyError, refresh } = useStoreOptions(chargeId, rentalApi.transactions)
const externalId = ref('')
const amount = ref('')
const processedAt = ref('')
const status = ref<TransactionInput['status']>('PENDING_CONFIRMATION')
const saving = ref(false)
const saveError = ref('')
const saved = ref('')
const amountEdited = ref(false)

function cents(value: string) {
  const [whole, decimals = ''] = value.split('.')
  return BigInt(whole!) * 100n + BigInt(decimals.padEnd(2, '0'))
}

const remaining = computed(() => {
  if (!selected.value || loading.value || historyError.value) return ''
  const paid = history.value
    .filter(row => row.status === 'SUCCEEDED')
    .reduce((total, row) => total + cents(row.amount), 0n)
  const balance = cents(selected.value.amount) - paid
  if (balance <= 0n) return '0.00'
  return `${balance / 100n}.${String(balance % 100n).padStart(2, '0')}`
})
const transactionType = computed<TransactionInput['type']>(() => selected.value?.type === 'REFUND'
  ? 'REFUND' : selected.value?.type === 'SUPPLEMENT' ? 'SUPPLEMENT' : 'PAYMENT')
const openForEntry = computed(() => props.canRecord && !!selected.value && selected.value.status !== 'SETTLED' && remaining.value !== '0.00')

watch(() => props.charges, charges => {
  if (charges.some(charge => charge.chargeId === chargeId.value)) return
  const outstanding = charges.filter(charge => charge.status !== 'SETTLED')
  chargeId.value = outstanding.length === 1 ? outstanding[0]!.chargeId : charges.length === 1 ? charges[0]!.chargeId : ''
}, { immediate: true })
watch(chargeId, () => { resetDraft(); saved.value = '' })
watch(remaining, value => { if (!amountEdited.value) amount.value = value })
watch(() => props.version, refresh)

function resetDraft() {
  externalId.value = ''
  amountEdited.value = false
  amount.value = remaining.value
  processedAt.value = ''
  status.value = 'PENDING_CONFIRMATION'
  saveError.value = ''
}

async function record() {
  if (saving.value || props.disabled || !openForEntry.value || loading.value || historyError.value || !remaining.value) return
  const reference = externalId.value.trim()
  const localTime = processedAt.value ? new Date(processedAt.value) : null
  const value = amount.value.trim()
  saved.value = ''
  saveError.value = ''
  if (!reference) { saveError.value = '请填写真实收据上的交易编号。'; return }
  if (!/^\d+(\.\d{1,2})?$/.test(value) || cents(value) <= 0n || cents(value) > cents(remaining.value)) {
    saveError.value = '请输入实际交易金额，金额须大于 0 且不超过剩余金额。'
    return
  }
  if ((status.value === 'SUCCEEDED' && !localTime) || (localTime && !Number.isFinite(localTime.getTime()))) {
    saveError.value = '请填写收据上的实际处理时间。'
    return
  }
  saving.value = true
  emit('saving', true)
  try {
    await rentalApi.record(chargeId.value, {
      type: transactionType.value, externalId: reference, amount: value, status: status.value,
      processedAt: localTime ? localTime.toISOString().replace('.000Z', 'Z') : null,
    })
    resetDraft()
    saved.value = `已登记 ${reference}`
    refresh()
  } catch (failure) {
    saveError.value = failure instanceof Error ? errorText(failure) : '登记失败，已保留输入。'
  } finally {
    saving.value = false
    emit('saving', false)
  }
  if (saved.value) emit('recorded')
}
</script>

<template>
  <section aria-label="费用与交易">
    <h3>费用与交易</h3>
    <p v-if="!charges.length">
      暂无费用项
    </p>
    <template v-else>
      <label>费用<select
        v-model="chargeId"
        aria-label="费用"
        :disabled="disabled || saving"
      >
        <option value="">选择费用项</option>
        <option
          v-for="charge in charges"
          :key="charge.chargeId"
          :value="charge.chargeId"
        >{{ uiText(charge.type) }} · {{ charge.amount }} NOK · {{ uiText(charge.status) }}</option>
      </select></label>
      <div v-if="selected">
        <p v-if="remaining">
          {{ selected.type === 'REFUND' ? '尚待退款' : '尚待支付' }} {{ remaining }} NOK
        </p>
        <button
          type="button"
          :disabled="loading || disabled || saving"
          @click="refresh"
        >
          刷新交易记录
        </button>
        <p v-if="loading">
          正在读取交易记录…
        </p>
        <p
          v-else-if="historyError"
          role="alert"
        >
          {{ historyError }}
        </p>
        <ul
          v-else-if="history.length"
          aria-label="交易记录"
        >
          <li
            v-for="row in history"
            :key="row.transactionId"
          >
            {{ row.source === 'SIMULATED' ? '顾客模拟付款（不扣款）' : '外部交易' }} ·
            {{ row.externalId }} · {{ uiText(row.status) }} · {{ row.amount }} NOK
            <small v-if="row.processedAt"> · {{ row.processedAt }}</small>
          </li>
        </ul>
        <p v-if="openForEntry && transactionType !== 'REFUND'">
          课堂演示可由顾客在小程序完成模拟付款，刷新后即可查看系统生成的交易编号。
        </p>
        <form
          v-if="openForEntry"
          @submit.prevent="record"
        >
          <fieldset :disabled="disabled || saving || loading || !!historyError">
            <legend>登记已核实的外部{{ uiText(transactionType) }}</legend>
            <label>外部交易编号<input
              v-model="externalId"
              aria-label="外部交易编号"
              maxlength="100"
              placeholder="可粘贴收据编号"
              required
            ></label>
            <label>金额<input
              v-model="amount"
              aria-label="金额"
              inputmode="decimal"
              required
              @input="amountEdited = true"
            ></label>
            <label>结果<select v-model="status"><option value="PENDING_CONFIRMATION">待确认</option><option value="SUCCEEDED">成功</option><option value="FAILED">失败</option></select></label>
            <label>第三方处理时间（本机时区）<input
              v-model="processedAt"
              type="datetime-local"
              step="1"
              :required="status === 'SUCCEEDED'"
            ></label>
            <button :disabled="!remaining">
              登记外部交易结果
            </button>
          </fieldset>
        </form>
        <p
          v-if="saveError"
          role="alert"
        >
          {{ saveError }}
        </p>
        <p
          v-if="saved"
          role="status"
        >
          {{ saved }}
        </p>
      </div>
    </template>
  </section>
</template>

<style scoped>
section { margin-top: 1.2rem; }
label { display: block; margin: .55rem 0; }
input, select { max-width: 100%; margin: .35rem; }
fieldset { min-width: 0; max-width: 32rem; margin: .7rem 0; }
li { overflow-wrap: anywhere; }
[role="alert"] { color: #9b1c1c; }
</style>

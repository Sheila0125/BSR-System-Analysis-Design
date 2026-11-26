<script setup lang="ts">
import StoreSelect from '../identity/StoreSelect.vue'
import { errorText } from '../../shared/presentation/zh'
import { uiText } from '../../shared/presentation/zh'
import { computed, ref, watch } from 'vue'
import { useStoreOptions } from '../../shared/components/useStoreOptions'
import { loadCatalog } from '../equipment/equipmentCatalogApi'
import ContractCards from './ContractCards.vue'
import { contractKeywords } from './contractKeywords'
import { rentalApi, type ReturnReceipt } from './rentalApi'
const contractStoreId = ref('')
const returnStoreId = ref('')
const contractId = ref('')
const { options: contracts, loading: contractsLoading, error: contractsError } = useStoreOptions(contractStoreId, rentalApi.list)
const returnable = computed(() => contracts.value.filter(row => row.items.some(item => item.active && item.status === 'RENTED')))
const { options: catalog } = useStoreOptions(contractStoreId, id => loadCatalog(id, '', ''))
const categories = computed(() => new Map(catalog.value.map(item => [item.equipmentId, item.equipmentCategory])))
const selectedContract = computed(() => contracts.value.find(row => row.contractId === contractId.value))
watch(contractStoreId, () => {
  contractId.value = ''
  returnStoreId.value = ''
})
watch(returnable, options => {
  if (!contractId.value && options.length === 1) selectContract(options[0]!)
})
const receipt = ref<ReturnReceipt | null>(null)
const selected = ref<string[]>([])
const returnableItems = computed(() => receipt.value?.items.filter(item => item.status === 'RENTED') ?? [])
const outcome = ref('NORMAL')
const note = ref('')
const busy = ref(false)
const error = ref('')
watch([returnStoreId, contractId], () => {
  receipt.value = null
  selected.value = []
  outcome.value = 'NORMAL'
  note.value = ''
  error.value = ''
})
watch(contractId, id => {
  if (id && returnStoreId.value) void lookup()
}, { flush: 'post' })
watch(returnStoreId, id => {
  if (id && contractId.value) void lookup()
}, { flush: 'post' })
async function run(operation: () => Promise<ReturnReceipt>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try { receipt.value = await operation(); selected.value = [] }
  catch (failure) { error.value = failure instanceof Error ? errorText(failure) : '归还登记失败，请妥善保管实物，已保留您的输入。' }
  finally { busy.value = false }
}
function selectContract(contract: (typeof contracts.value)[number]) {
  contractId.value = contract.contractId
  returnStoreId.value = contract.serviceStoreId
}
function lookup() { return run(() => rentalApi.returnLookup(contractId.value, returnStoreId.value)) }
function receive() { return run(async () => {
  const result = await rentalApi.returns(contractId.value, returnStoreId.value, selected.value.map(itemId => ({
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
    <p>先选择合同所属门店和合同，再核对实际接收门店。接收门店可与合同门店不同。普通提前归还不自动退款。</p>
    <StoreSelect
      v-model="contractStoreId"
      label="合同所属门店"
      role="EMPLOYEE"
      :disabled="busy"
    />
    <ContractCards
      v-if="returnable.length"
      :key="contractStoreId"
      label="待归还合同"
      :contracts="returnable"
      :categories="categories"
      :selected-id="contractId"
      :disabled="busy || contractsLoading"
      @select="selectContract($event)"
    />
    <p
      v-else-if="contractStoreId && !contractsError"
    >
      {{ contractsLoading ? '正在加载合同…' : '本店暂无待归还合同' }}
    </p>
    <StoreSelect
      v-if="contractId"
      v-model="returnStoreId"
      label="实际接收门店"
      role="EMPLOYEE"
      :disabled="busy"
    />
    <p
      v-if="contractsError"
      role="alert"
    >
      {{ contractsError }}
    </p>
    <button
      :disabled="busy || !returnStoreId || !contractId"
      @click="lookup"
    >
      {{ busy ? '正在处理…' : '刷新归还清单' }}
    </button>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <article v-if="receipt">
      <h2>{{ selectedContract ? contractKeywords(selectedContract, categories) : '归还清单' }} · {{ uiText(receipt.status) }}</h2>
      <p>合同编号 {{ selectedContract?.contractNumber ?? receipt.contractId }}</p>
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

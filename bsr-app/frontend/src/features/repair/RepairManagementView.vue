<script setup lang="ts">
import StoreSelect from '../identity/StoreSelect.vue'
import { errorText } from '../../shared/presentation/zh'
import { ref, watch } from 'vue'
import { repairApi, type RepairContractor, type RepairRate } from './repairApi'

const storeId = ref('')
const contractors = ref<RepairContractor[]>([])
const selected = ref<RepairContractor | null>(null)
const rates = ref<RepairRate[]>([])
const name = ref('')
const contactName = ref('')
const email = ref('')
const phone = ref('')
const postalAddress = ref('')
const effectiveFrom = ref('')
const effectiveTo = ref('')
const hourlyRate = ref('')
const busy = ref(false)
const error = ref('')
const notice = ref('')

watch(storeId, () => { contractors.value = []; selected.value = null; rates.value = [] })
async function run(operation: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try { await operation() } catch (failure) { error.value = failure instanceof Error ? errorText(failure) : '管理操作失败，已保留您的输入。' }
  finally { busy.value = false }
}
function load() { return run(async () => { contractors.value = await repairApi.contractors(storeId.value) }) }
function select(event: Event) {
  const id = (event.target as HTMLSelectElement).value
  const contractor = contractors.value.find(row => row.contractorId === id)
  if (!contractor) {
    selected.value = null; rates.value = []
    name.value = ''; contactName.value = ''; email.value = ''; phone.value = ''; postalAddress.value = ''
    return
  }
  return run(async () => {
    selected.value = contractor; rates.value = []
    name.value = contractor.name; contactName.value = contractor.contactName ?? ''
    email.value = contractor.email; phone.value = contractor.phone ?? ''
    postalAddress.value = contractor.postalAddress ?? ''
    rates.value = await repairApi.rates(storeId.value, contractor.contractorId)
  })
}
function create() { return run(async () => {
  selected.value = await repairApi.createContractor(storeId.value, name.value, contactName.value || null, email.value, phone.value || null, postalAddress.value)
  contractors.value = await repairApi.contractors(storeId.value)
  rates.value = []
}) }
function update() { return run(async () => {
  if (!selected.value) return
  selected.value = await repairApi.updateContractor(storeId.value, selected.value.contractorId,
    name.value, contactName.value || null, email.value, phone.value || null, postalAddress.value)
  contractors.value = await repairApi.contractors(storeId.value)
}) }
function deactivate() { return run(async () => {
  if (!selected.value) return
  selected.value = await repairApi.deactivateContractor(storeId.value, selected.value.contractorId)
  contractors.value = await repairApi.contractors(storeId.value)
}) }
function addRate() { return run(async () => {
  if (!selected.value) return
  await repairApi.addRate(storeId.value, selected.value.contractorId, effectiveFrom.value,
    effectiveTo.value || null, hourlyRate.value)
  rates.value = await repairApi.rates(storeId.value, selected.value.contractorId)
  effectiveFrom.value = ''; effectiveTo.value = ''; hourlyRate.value = ''
  notice.value = '历史费率已保存，相关待计费工时与费用已更新。'
}) }
</script>

<template>
  <section>
    <h1>维修承包商管理</h1>
    <p>在授权门店管理承包商及费率。历史费率按起止日期计算，各区间不能重叠。</p>
    <StoreSelect
      v-model="storeId"
      label="门店编号"
      role="MANAGER"
      :disabled="busy"
    />
    <button
      data-testid="load-contractors"
      :disabled="busy || !storeId"
      @click="load"
    >
      查询承包商
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
    <label v-if="contractors.length">承包商档案<select
      :value="selected?.contractorId ?? ''"
      aria-label="承包商档案"
      :disabled="busy"
      @change="select"
    >
      <option value="">新增承包商</option>
      <option
        v-for="contractor in contractors"
        :key="contractor.contractorId"
        :value="contractor.contractorId"
      >
        {{ contractor.name }} · {{ contractor.active ? '有效' : '已停用' }}
      </option>
    </select></label>

    <h2>{{ selected ? '编辑承包商' : '新增承包商' }}</h2>
    <label>姓名<input
      v-model.trim="name"
      aria-label="承包商名称"
      maxlength="120"
    ></label>
    <label>联系人<input
      v-model.trim="contactName"
      aria-label="联系人"
      maxlength="100"
    ></label>
    <label>电子邮箱<input
      v-model.trim="email"
      aria-label="承包商邮箱"
      type="email"
      maxlength="255"
    ></label>
    <label>联系电话<input
      v-model.trim="phone"
      aria-label="承包商电话"
      maxlength="40"
    ></label>
    <label>邮寄地址（选填）<input
      v-model.trim="postalAddress"
      aria-label="邮寄地址"
      maxlength="255"
    ></label>
    <button
      v-if="!selected"
      :disabled="busy || !storeId || !name || !email"
      @click="create"
    >
      创建承包商
    </button>
    <template v-else>
      <button
        :disabled="busy || !name || !email"
        @click="update"
      >
        更新承包商
      </button>
      <button
        v-if="selected.active"
        :disabled="busy"
        @click="deactivate"
      >
        停用承包商
      </button>
    </template>

    <section
      v-if="selected"
      aria-label="历史维修费率"
    >
      <h2>历史费率： {{ selected.name }}</h2>
      <ul>
        <li
          v-for="rate in rates"
          :key="rate.rateId"
        >
          {{ rate.effectiveFrom }}–{{ rate.effectiveTo ?? '长期有效' }} · {{ rate.hourlyRate }} 每小时
        </li>
      </ul>
      <label>生效日期<input
        v-model="effectiveFrom"
        aria-label="生效日期"
        type="date"
      ></label>
      <label>失效日期（选填）<input
        v-model="effectiveTo"
        aria-label="失效日期"
        type="date"
      ></label>
      <label>小时费率<input
        v-model.trim="hourlyRate"
        aria-label="小时费率"
        inputmode="decimal"
      ></label>
      <button
        data-testid="add-rate"
        :disabled="busy || !effectiveFrom || !hourlyRate"
        @click="addRate"
      >
        新增历史费率
      </button>
    </section>
  </section>
</template>

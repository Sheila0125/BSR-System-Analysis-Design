<script setup lang="ts">
import StoreSelect from '../identity/StoreSelect.vue'
import { problemText } from '../../shared/presentation/zh'
import { ref } from 'vue'
import { ApiError } from '../../shared/http/apiClient'
import { findCustomer, reviewCustomerMeasurements, type MeasurementReview, type VerifiedCustomer } from './customerVerificationApi'

const storeId = ref('')
const identifier = ref('')
const field = ref<'customerNumber' | 'email'>('customerNumber')
const customer = ref<VerifiedCustomer | null>(null)
const verifiedStoreId = ref('')
const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const reviewing = ref(false)
const measurements = ref<MeasurementReview>({
  heightCm: null, weightKg: null, bootSize: null,
  heightEstimated: false, weightEstimated: false, bootSizeEstimated: false,
})

function useMeasurements(profile: VerifiedCustomer) {
  measurements.value = {
    heightCm: profile.heightCm,
    weightKg: profile.weightKg,
    bootSize: profile.bootSize,
    heightEstimated: profile.heightEstimated,
    weightEstimated: profile.weightEstimated,
    bootSizeEstimated: profile.bootSizeEstimated,
  }
}

async function search() {
  errorMessage.value = ''
  successMessage.value = ''
  customer.value = null
  verifiedStoreId.value = ''
  if (!/^[1-9]\d*$/.test(storeId.value)) {
    errorMessage.value = '请输入有效的正整数门店编号。'
    return
  }
  if (!identifier.value.trim()) {
    errorMessage.value = '请填写顾客编号或邮箱。'
    return
  }
  loading.value = true
  try {
    customer.value = await findCustomer(storeId.value, field.value, identifier.value.trim())
    verifiedStoreId.value = storeId.value
    useMeasurements(customer.value)
  } catch (error) {
    errorMessage.value = error instanceof ApiError
      ? problemText(error.problem)
      : '顾客查询失败，请检查连接后重试。'
  } finally {
    loading.value = false
  }
}

async function saveReview() {
  if (!customer.value) return
  errorMessage.value = ''
  successMessage.value = ''
  reviewing.value = true
  try {
    customer.value = await reviewCustomerMeasurements(verifiedStoreId.value, customer.value, measurements.value)
    useMeasurements(customer.value)
    successMessage.value = '尺寸核验信息已保存。'
  } catch (error) {
    errorMessage.value = error instanceof ApiError
      ? problemText(error.problem)
      : '尺寸核验保存失败，已保留您的输入，请重试。'
  } finally {
    reviewing.value = false
  }
}
</script>

<template>
  <section class="verification-page">
    <p class="eyebrow">
      员工业务
    </p>
    <h1>顾客核验</h1>
    <p>通过顾客编号或邮箱查询，请在办理租赁前与顾客核实身份。</p>

    <form
      class="verification-form"
      @submit.prevent="search"
    >
      <StoreSelect
        v-model="storeId"
        label="门店编号"
        role="EMPLOYEE"
        :disabled="loading"
      />
      <label>查询方式<select v-model="field">
        <option value="customerNumber">顾客编号</option>
        <option value="email">电子邮箱</option>
      </select></label>
      <label>{{ field === 'email' ? '电子邮箱' : '顾客编号' }}<input
        v-model="identifier"
        required
      ></label>
      <button
        type="submit"
        :disabled="loading"
      >
        {{ loading ? '查询中…' : '查询顾客' }}
      </button>
    </form>

    <p
      v-if="errorMessage"
      role="alert"
    >
      {{ errorMessage }}
    </p>
    <p
      v-if="successMessage"
      role="status"
    >
      {{ successMessage }}
    </p>
    <dl
      v-if="customer"
      class="verification-result"
    >
      <dt>顾客编号</dt><dd>{{ customer.customerId }}</dd>
      <dt>顾客编号</dt><dd>{{ customer.customerNumber }}</dd>
      <dt>姓名</dt><dd>{{ customer.fullName }}</dd>
      <dt>电子邮箱</dt><dd>{{ customer.email }}</dd>
      <dt>联系电话</dt><dd>{{ customer.phone }}</dd>
      <dt>身高</dt><dd>{{ customer.heightCm ?? '未记录' }}{{ customer.heightEstimated ? '（估计值，请与顾客核对）' : '' }}</dd>
      <dt>体重</dt><dd>{{ customer.weightKg ?? '未记录' }}{{ customer.weightEstimated ? '（估计值，请与顾客核对）' : '' }}</dd>
      <dt>雪鞋尺码</dt><dd>{{ customer.bootSize ?? '未记录' }}{{ customer.bootSizeEstimated ? '（估计值，请与顾客核对）' : '' }}</dd>
    </dl>
    <form
      v-if="customer"
      class="verification-form"
      @submit.prevent="saveReview"
    >
      <h2>核对尺寸信息</h2>
      <p>请与顾客核对尺寸，确认测量值后再取消对应的估计标记。</p>
      <label>身高（厘米）<input
        v-model="measurements.heightCm"
        inputmode="decimal"
        pattern="[0-9]{1,3}(\.[0-9]{1,2})?"
      ></label>
      <label><input
        v-model="measurements.heightEstimated"
        type="checkbox"
      > 身高尚为估计值</label>
      <label>体重（千克）<input
        v-model="measurements.weightKg"
        inputmode="decimal"
        pattern="[0-9]{1,3}(\.[0-9]{1,2})?"
      ></label>
      <label><input
        v-model="measurements.weightEstimated"
        type="checkbox"
      > 体重尚为估计值</label>
      <label>雪鞋尺码<input
        v-model="measurements.bootSize"
        inputmode="decimal"
        pattern="[0-9]{1,3}(\.[0-9])?"
      ></label>
      <label><input
        v-model="measurements.bootSizeEstimated"
        type="checkbox"
      > 雪鞋尺码尚为估计值</label>
      <button
        type="submit"
        :disabled="reviewing"
      >
        {{ reviewing ? '保存中…' : '保存核验信息' }}
      </button>
    </form>
  </section>
</template>

<style scoped>
.verification-page { max-width: 48rem; }
.verification-form, label { display: grid; gap: .5rem; }
.verification-form { gap: 1rem; margin-block: 1.5rem; max-width: 28rem; }
input, select { min-height: 2.75rem; padding: .65rem; }
button { width: fit-content; padding: .7rem 1.2rem; }
[role="alert"] { color: #9b1c1c; }
.verification-result { display: grid; grid-template-columns: minmax(9rem, auto) 1fr; gap: .5rem 1rem; }
.verification-result dt { font-weight: 700; }
.verification-result dd { margin: 0; overflow-wrap: anywhere; }
</style>

<script setup lang="ts">
import StoreSelect from './StoreSelect.vue'
import { problemText } from '../../shared/presentation/zh'
import { uiText } from '../../shared/presentation/zh'
import { computed, ref, watch } from 'vue'
import { ApiError } from '../../shared/http/apiClient'
import {
  createRoleGrant,
  revokeRoleGrant,
  type RoleCode,
  type RoleGrant,
} from './roleGrantApi'

const accountId = ref('')
const role = ref<RoleCode>('EMPLOYEE')
const storeId = ref('')
const validFrom = ref('')
const validTo = ref('')
const revokeId = ref('')
const submitting = ref(false)
const revoking = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const latestGrant = ref<RoleGrant | null>(null)

const roleNeedsStore = computed(() => role.value === 'EMPLOYEE' || role.value === 'MANAGER')

watch(roleNeedsStore, (needsStore) => {
  if (!needsStore) storeId.value = ''
})

function positiveId(value: string): boolean {
  return /^[1-9]\d*$/.test(value)
}

function describeError(error: unknown): string {
  return error instanceof ApiError
    ? problemText(error.problem)
    : '请求失败，请检查连接后重试。'
}

function validateGrant(): string | null {
  if (!positiveId(accountId.value)) return '请输入有效的正整数账号编号。'
  if (roleNeedsStore.value && !positiveId(storeId.value)) {
    return '员工和经理授权需要填写有效的门店编号。'
  }
  if (!validFrom.value) return '请填写生效时间。'
  if (validTo.value && validTo.value <= validFrom.value) return '失效时间必须晚于生效时间。'
  return null
}

async function submitGrant() {
  errorMessage.value = ''
  successMessage.value = ''
  const validationError = validateGrant()
  if (validationError) {
    errorMessage.value = validationError
    return
  }
  submitting.value = true
  try {
    latestGrant.value = await createRoleGrant({
      accountId: accountId.value,
      role: role.value,
      storeId: roleNeedsStore.value ? storeId.value : null,
      validFrom: validFrom.value,
      validTo: validTo.value || null,
    })
    revokeId.value = latestGrant.value.roleGrantId
    successMessage.value = `已创建授权 ${latestGrant.value.roleGrantId}。`
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    submitting.value = false
  }
}

async function revokeGrant() {
  errorMessage.value = ''
  successMessage.value = ''
  if (!positiveId(revokeId.value)) {
    errorMessage.value = '请输入有效的正整数授权编号。'
    return
  }
  revoking.value = true
  try {
    latestGrant.value = await revokeRoleGrant(revokeId.value)
    successMessage.value = `已撤销授权 ${latestGrant.value.roleGrantId}。`
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    revoking.value = false
  }
}
</script>

<template>
  <section class="management-page">
    <p class="eyebrow">
      经理业务
    </p>
    <h1>账号与权限</h1>
    <p>设置有期限的角色授权，不改变账号绑定的业务身份。</p>

    <form
      class="grant-form"
      @submit.prevent="submitGrant"
    >
      <label>账号编号<input
        v-model.trim="accountId"
        inputmode="numeric"
        required
      ></label>
      <label>角色<select v-model="role">
        <option value="CUSTOMER">顾客</option>
        <option value="EMPLOYEE">员工</option>
        <option value="MANAGER">门店经理</option>
        <option value="CONTRACTOR">维修承包商</option>
      </select></label>
      <StoreSelect
        v-if="roleNeedsStore"
        v-model="storeId"
        label="门店编号"
        role="MANAGER"
        :select-only-option="false"
        :disabled="submitting"
      />
      <label>生效时间<input
        v-model="validFrom"
        type="datetime-local"
        required
      ></label>
      <label>失效时间（选填）<input
        v-model="validTo"
        type="datetime-local"
      ></label>
      <button
        type="submit"
        :disabled="submitting"
      >
        {{ submitting ? '创建中…' : '创建授权' }}
      </button>
    </form>

    <form
      class="revoke-form"
      @submit.prevent="revokeGrant"
    >
      <h2>撤销有效授权</h2>
      <label>授权编号<input
        v-model.trim="revokeId"
        inputmode="numeric"
        required
      ></label>
      <button
        type="submit"
        :disabled="revoking"
      >
        {{ revoking ? '撤销中…' : '撤销授权' }}
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
      v-if="latestGrant"
      class="grant-result"
    >
      <dt>授权编号</dt><dd>{{ latestGrant.roleGrantId }}</dd>
      <dt>账号编号</dt><dd>{{ latestGrant.accountId }}</dd>
      <dt>角色</dt><dd>{{ uiText(latestGrant.role) }}</dd>
      <dt>门店</dt><dd>{{ latestGrant.storeId ?? '不限定门店' }}</dd>
      <dt>生效时间</dt><dd>{{ latestGrant.validFrom }}</dd>
      <dt>失效时间</dt><dd>{{ latestGrant.validTo ?? '长期有效' }}</dd>
    </dl>
  </section>
</template>

<style scoped>
.management-page { max-width: 48rem; }
.grant-form, .revoke-form, label { display: grid; gap: .5rem; }
.grant-form, .revoke-form { gap: 1rem; margin-block: 1.5rem; }
input, select { min-height: 2.75rem; padding: .65rem; }
button { width: fit-content; padding: .7rem 1.2rem; }
[role="alert"] { color: #9b1c1c; }
[role="status"] { color: #17633a; }
.grant-result { display: grid; grid-template-columns: minmax(8rem, auto) 1fr; gap: .5rem 1rem; }
.grant-result dt { font-weight: 700; }
.grant-result dd { margin: 0; overflow-wrap: anywhere; }
</style>

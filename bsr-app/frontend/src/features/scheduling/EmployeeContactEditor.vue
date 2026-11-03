<script setup lang="ts">
import { ref, watch } from 'vue'
import { employeeContactApi, type EmployeeContact } from './employeeContactApi'
import { errorText } from '../../shared/presentation/zh'
const props = defineProps<{ storeId: string; employeeId: string; disabled?: boolean }>()
const contact = ref<EmployeeContact | null>(null)
const address = ref(''), note = ref(''), busy = ref(false), error = ref(''), notice = ref('')
let generation = 0
watch(() => [props.storeId, props.employeeId], () => {
  generation++; contact.value = null; address.value = ''; note.value = ''; error.value = ''; notice.value = ''
})
async function run(save: boolean) {
  if (busy.value || props.disabled || !props.storeId || !props.employeeId || save && !contact.value) return
  const current = generation, storeId = props.storeId, employeeId = props.employeeId
  busy.value = true; error.value = ''; notice.value = ''
  try {
    const updated = save ? await employeeContactApi.update(storeId, employeeId, address.value, note.value)
      : await employeeContactApi.read(storeId, employeeId)
    if (generation !== current) return
    contact.value = updated; address.value = updated.address ?? ''; note.value = updated.note ?? ''
    if (save) notice.value = '员工地址和备注已保存。'
  } catch (failure) {
    if (generation === current) error.value = failure instanceof Error ? errorText(failure) : '员工资料操作失败，已保留输入。'
  } finally { busy.value = false }
}
</script>

<template>
  <section aria-label="员工联系资料">
    <h2>员工资料补充</h2>
    <p>使用上方门店及员工编号，维护该门店授权员工的地址和备注。不改变账号、权限、在职状态或班次。</p>
    <button
      data-testid="load-employee-contact"
      :disabled="busy || disabled || !storeId || !employeeId"
      @click="run(false)"
    >
      读取员工资料
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
    <template v-if="contact">
      <p>{{ contact.employeeNumber }} · {{ contact.fullName }} · {{ contact.email }}</p>
      <label>地址（选填）<input
        v-model="address"
        aria-label="员工地址"
        maxlength="255"
        :disabled="busy || disabled"
      ></label>
      <label>备注（选填）<textarea
        v-model="note"
        aria-label="员工备注"
        maxlength="500"
        :disabled="busy || disabled"
      /></label>
      <button
        data-testid="save-employee-contact"
        :disabled="busy || disabled"
        @click="run(true)"
      >
        保存员工资料
      </button>
    </template>
  </section>
</template>

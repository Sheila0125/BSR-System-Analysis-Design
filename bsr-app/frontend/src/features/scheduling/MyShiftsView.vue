<script setup lang="ts">
import { errorText } from '../../shared/presentation/zh'
import { uiText } from '../../shared/presentation/zh'
import { computed, ref } from 'vue'
import { schedulingApi, type Shift } from './schedulingApi'

const shifts = ref<Shift[]>([])
const busy = ref(false)
const error = ref('')
const from = ref(''), to = ref('')
const rangeError = computed(() => from.value && to.value && from.value > to.value ? '开始日期不能晚于结束日期。' : '')
const visibleShifts = computed(() => rangeError.value ? shifts.value : shifts.value.filter(shift =>
  (!from.value || shift.shiftDate >= from.value) && (!to.value || shift.shiftDate <= to.value)))

async function load() {
  if (busy.value) return
  if (rangeError.value) { error.value = rangeError.value; return }
  busy.value = true; error.value = ''
  try { shifts.value = await schedulingApi.myShifts() }
  catch (failure) { error.value = failure instanceof Error ? errorText(failure) : '班次加载失败，请稍后重试。' }
  finally { busy.value = false }
}
</script>

<template>
  <section>
    <h1>我的班次</h1>
    <p>仅显示属于您本人身份的班次。</p>
    <label>开始日期（选填）<input
      v-model="from"
      type="date"
      aria-label="班次开始日期"
    ></label>
    <label>结束日期（选填）<input
      v-model="to"
      type="date"
      aria-label="班次结束日期"
    ></label>
    <button
      data-testid="load-my-shifts"
      :disabled="busy"
      @click="load"
    >
      查询我的班次
    </button>
    <p
      v-if="error || rangeError"
      role="alert"
    >
      {{ error || rangeError }}
    </p>
    <ul>
      <li
        v-for="shift in visibleShifts"
        :key="shift.shiftId"
      >
        {{ shift.shiftNumber }} · {{ shift.shiftDate }} · 门店 {{ shift.storeId }} ·
        {{ shift.startTime }}–{{ shift.endTime }} · {{ uiText(shift.status) }}
        <span v-if="shift.cancellationReason"> · {{ shift.cancellationReason }}</span>
      </li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import { useStoreOptions } from '../../shared/components/useStoreOptions'
import StoreSelect from '../identity/StoreSelect.vue'
import { errorText } from '../../shared/presentation/zh'
import { uiText } from '../../shared/presentation/zh'
import { computed, ref, watch } from 'vue'
import { schedulingApi, type Shift, type ShiftInput } from './schedulingApi'
import EmployeeContactEditor from './EmployeeContactEditor.vue'
import { employeeChoices } from './employeeContactApi'

const storeId = ref('')
const { options: employees, loading: employeesLoading, error: employeesError, refresh: refreshEmployees } =
  useStoreOptions(storeId, employeeChoices)
const employeeId = ref('')
const manualEmployee = ref(false)
const shiftDate = ref('')
const startTime = ref('08:00')
const endTime = ref('22:00')
const selectedShiftId = ref<string | null>(null)
const loadedStoreId = ref('')
const shifts = ref<Shift[]>([])
const reasons = ref<Record<string, string>>({})
const busy = ref(false)
const error = ref('')
const notice = ref('')
watch(storeId, () => {
  if (!selectedShiftId.value) employeeId.value = ''
})
const canSave = computed(() => Boolean(storeId.value && /^[1-9][0-9]{0,18}$/.test(employeeId.value)
  && (manualEmployee.value || selectedShiftId.value || employees.value.some(row => row.employeeId === employeeId.value))
  && shiftDate.value && startTime.value && endTime.value && !employeesLoading.value))

async function run(operation: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try { await operation() } catch (failure) { error.value = failure instanceof Error ? errorText(failure) : '班次操作失败，已保留您的输入。' }
  finally { busy.value = false }
}

function schedule(): ShiftInput {
  return { shiftDate: shiftDate.value, storeId: storeId.value, employeeId: employeeId.value,
    startTime: startTime.value, endTime: endTime.value }
}

function replace(updated: Shift) {
  if (!loadedStoreId.value) loadedStoreId.value = updated.storeId
  const rest = shifts.value.filter(shift => shift.shiftId !== updated.shiftId)
  shifts.value = updated.storeId === loadedStoreId.value ? [updated, ...rest] : rest
}

function load() { return run(async () => {
  const requestedStoreId = storeId.value
  refreshEmployees()
  const rows = await schedulingApi.storeShifts(requestedStoreId)
  if (storeId.value !== requestedStoreId) return
  shifts.value = rows
  loadedStoreId.value = requestedStoreId
}) }
function save() { return run(async () => {
  const updated = selectedShiftId.value
    ? await schedulingApi.adjust(selectedShiftId.value, schedule())
    : await schedulingApi.create(schedule())
  replace(updated)
  selectedShiftId.value = null
  notice.value = `班次 ${updated.shiftNumber} 已保存。`
}) }
function edit(shift: Shift) {
  selectedShiftId.value = shift.shiftId
  manualEmployee.value = false
  shiftDate.value = shift.shiftDate
  storeId.value = shift.storeId
  employeeId.value = shift.employeeId
  startTime.value = shift.startTime
  endTime.value = shift.endTime
}
function cancel(shiftId: string) { return run(async () => {
  const updated = await schedulingApi.cancel(shiftId, reasons.value[shiftId] ?? '')
  replace(updated)
  notice.value = `班次 ${updated.shiftNumber} 已取消，原排班记录已保留。`
}) }
</script>

<template>
  <section>
    <h1>员工排班</h1>
    <p>同一员工每天最多一个有效班次（跨店同样适用），班次时间须在 08:00—22:00 内。</p>
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
    <StoreSelect
      v-model="storeId"
      label="门店编号"
      role="MANAGER"
      :disabled="busy"
    />
    <button
      data-testid="load-shifts"
      :disabled="busy || !storeId"
      @click="load"
    >
      查询门店班次
    </button>
    <section>
      <h2>{{ selectedShiftId ? '调整班次' : '创建班次' }}</h2>
      <label v-if="manualEmployee">员工编号<input
        v-model.trim="employeeId"
        inputmode="numeric"
        :disabled="busy"
      ></label>
      <label v-else>员工<select
        v-model="employeeId"
        aria-label="员工"
        :disabled="busy || employeesLoading"
      >
        <option
          value=""
          disabled
        >{{ employeesLoading ? '正在加载员工…' : employees.length ? '选择员工' : '本店暂无员工候选' }}</option>
        <option
          v-if="selectedShiftId && employeeId && !employees.some(row => row.employeeId === employeeId)"
          :value="employeeId"
        >
          当前班次员工 · {{ employeeId }}
        </option>
        <option
          v-for="employee in employees"
          :key="employee.employeeId"
          :value="employee.employeeId"
        >
          {{ employee.employeeNumber }} · {{ employee.fullName }}
        </option>
      </select></label>
      <button
        type="button"
        :disabled="busy || !storeId"
        @click="manualEmployee = !manualEmployee"
      >
        {{ manualEmployee ? '返回本店员工列表' : '按员工编号办理' }}
      </button>
      <p
        v-if="!manualEmployee && employeesError"
        role="alert"
      >
        {{ employeesError }}
      </p>
      <label>排班日期<input
        v-model="shiftDate"
        aria-label="排班日期"
        type="date"
      ></label>
      <label>开始时间<input
        v-model="startTime"
        aria-label="开始时间"
        type="time"
        min="08:00"
        max="22:00"
      ></label>
      <label>结束时间<input
        v-model="endTime"
        aria-label="结束时间"
        type="time"
        min="08:00"
        max="22:00"
      ></label>
      <button
        data-testid="save-shift"
        :disabled="busy || !canSave"
        @click="save"
      >
        {{ selectedShiftId ? '保存调整' : '创建班次' }}
      </button>
      <button
        v-if="selectedShiftId"
        data-testid="clear-selection"
        :disabled="busy"
        @click="selectedShiftId = null"
      >
        新增班次
      </button>
    </section>
    <section>
      <h2>门店班次 <small v-if="loadedStoreId">门店 {{ loadedStoreId }}</small></h2>
      <ul>
        <li
          v-for="shift in shifts"
          :key="shift.shiftId"
        >
          {{ shift.shiftNumber }} · {{ shift.shiftDate }} · 员工 {{ shift.employeeId }} ·
          {{ shift.startTime }}–{{ shift.endTime }} · {{ uiText(shift.status) }}
          <span v-if="shift.cancellationReason"> · {{ shift.cancellationReason }}</span>
          <template v-if="shift.status === 'ACTIVE'">
            <button
              :data-testid="`edit-${shift.shiftId}`"
              :disabled="busy"
              @click="edit(shift)"
            >
              编辑
            </button>
            <label>取消原因： {{ shift.shiftId }}<input
              v-model="reasons[shift.shiftId]"
              :aria-label="`取消原因：${shift.shiftId}`"
            ></label>
            <button
              :data-testid="`cancel-${shift.shiftId}`"
              :disabled="busy || !reasons[shift.shiftId]?.trim()"
              @click="cancel(shift.shiftId)"
            >
              取消
            </button>
          </template>
        </li>
      </ul>
    </section>
    <EmployeeContactEditor
      :store-id="storeId"
      :employee-id="employeeId"
      :disabled="busy"
    />
  </section>
</template>

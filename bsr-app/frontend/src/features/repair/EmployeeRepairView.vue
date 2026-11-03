<script setup lang="ts">
import { errorText } from '../../shared/presentation/zh'
import { uiText } from '../../shared/presentation/zh'
import { ref, watch } from 'vue'
import { repairApi, type RepairTask, type RepairLabor, type RepairCharge } from './repairApi'

const storeId = ref('')
const equipmentId = ref('')
const faultDescription = ref('')
const initialContractorId = ref('')
const assignmentId = ref('')
const rejectionReason = ref('')
const tasks = ref<RepairTask[]>([])
const selected = ref<RepairTask | null>(null)
const labors = ref<RepairLabor[]>([])
const charge = ref<RepairCharge | null>(null)
const error = ref('')
const busy = ref(false)

watch(storeId, () => { tasks.value = []; selected.value = null; labors.value = []; charge.value = null })
async function run(operation: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await operation() } catch (failure) { error.value = failure instanceof Error ? errorText(failure) : '维修操作失败，已保留您的输入。' }
  finally { busy.value = false }
}
function load() { return run(async () => { tasks.value = await repairApi.employeeTasks(storeId.value) }) }
function create() { return run(async () => {
  selected.value = await repairApi.create(storeId.value, equipmentId.value, faultDescription.value, initialContractorId.value || null)
  tasks.value = await repairApi.employeeTasks(storeId.value)
  equipmentId.value = ''; faultDescription.value = ''; initialContractorId.value = ''
}) }
function select(task: RepairTask) { return run(async () => {
  selected.value = task; assignmentId.value = task.currentContractorId ?? ''
  labors.value = await repairApi.employeeLabors(task.taskId)
  charge.value = await repairApi.employeeCharge(task.taskId)
}) }
function assign() { return run(async () => {
  if (!selected.value) return
  selected.value = await repairApi.assign(selected.value.taskId, assignmentId.value)
  tasks.value = await repairApi.employeeTasks(storeId.value)
}) }
function accept() { return run(async () => {
  if (!selected.value) return
  selected.value = await repairApi.accept(selected.value.taskId)
  charge.value = await repairApi.employeeCharge(selected.value.taskId)
}) }
function reject() { return run(async () => {
  if (!selected.value) return
  selected.value = await repairApi.reject(selected.value.taskId, rejectionReason.value)
  rejectionReason.value = ''
}) }
function confirmCharge() { return run(async () => {
  if (!selected.value) return
  selected.value = await repairApi.confirmCharge(selected.value.taskId)
  charge.value = await repairApi.employeeCharge(selected.value.taskId)
}) }
</script>

<template>
  <section>
    <h1>维修办理</h1>
    <p>办理本门店的维修任务，实物验收与费用确认分别处理。</p>
    <label>门店编号<input
      v-model.trim="storeId"
      aria-label="门店编号"
    ></label>
    <button
      data-testid="load-repairs"
      :disabled="busy || !storeId"
      @click="load"
    >
      查询维修任务
    </button>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>

    <h2>创建维修任务</h2>
    <label>雪具编号<input
      v-model.trim="equipmentId"
      aria-label="雪具编号"
    ></label>
    <label>故障描述<input
      v-model.trim="faultDescription"
      aria-label="故障描述"
      maxlength="500"
    ></label>
    <label>初始承包商编号（选填）<input
      v-model.trim="initialContractorId"
      aria-label="初始承包商编号"
    ></label>
    <button
      data-testid="create-repair"
      :disabled="busy || !storeId || !equipmentId || !faultDescription"
      @click="create"
    >
      创建维修任务
    </button>

    <h2>门店维修任务</h2>
    <ul>
      <li
        v-for="task in tasks"
        :key="task.taskId"
      >
        <button
          :data-testid="`select-repair-${task.taskId}`"
          :disabled="busy"
          @click="select(task)"
        >
          {{ task.number }} · {{ uiText(task.status, 'repair') }} · 雪具 {{ task.equipmentId }}
        </button>
      </li>
    </ul>

    <section
      v-if="selected"
      aria-label="所选维修任务"
    >
      <h2>{{ selected.number }} · {{ uiText(selected.status, 'repair') }}</h2>
      <p>雪具 {{ selected.equipmentId }} · {{ selected.faultDescription }}</p>
      <label>承包商编号<input
        v-model.trim="assignmentId"
        aria-label="承包商编号"
      ></label>
      <button
        v-if="['UNASSIGNED', 'PENDING', 'IN_PROGRESS', 'RETURNED'].includes(selected.status)"
        :disabled="busy || !assignmentId"
        @click="assign"
      >
        分派承包商
      </button>
      <template v-if="selected.status === 'PENDING_ACCEPTANCE'">
        <button
          data-testid="accept-repair"
          :disabled="busy"
          @click="accept"
        >
          维修验收通过
        </button>
        <label>退回原因<input
          v-model.trim="rejectionReason"
          aria-label="退回原因"
          maxlength="500"
        ></label>
        <button
          :disabled="busy || !rejectionReason"
          @click="reject"
        >
          退回返工
        </button>
      </template>
      <p v-if="selected.status === 'PENDING_CHARGE'">
        待确认费用。雪具已完成验收，可能仍需补齐历史工时费率。
      </p>
      <p v-if="charge">
        服务费用 {{ charge.serviceAmount }} · {{ uiText(charge.status) }}
      </p>
      <button
        v-if="selected.status === 'PENDING_CHARGE'"
        data-testid="confirm-charge"
        :disabled="busy"
        @click="confirmCharge"
      >
        确认服务费用
      </button>
      <h3>工时记录</h3>
      <ul>
        <li
          v-for="labor in labors"
          :key="labor.laborId"
        >
          {{ labor.repairDate }} · {{ labor.durationHours }} 小时 · {{ labor.laborAmount ?? '费率待补齐' }}
        </li>
      </ul>
    </section>
  </section>
</template>

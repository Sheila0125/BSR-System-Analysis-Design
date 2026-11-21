<script setup lang="ts">
import StoreSelect from '../identity/StoreSelect.vue'
import { errorText } from '../../shared/presentation/zh'
import { uiText } from '../../shared/presentation/zh'
import { computed, ref, watch } from 'vue'
import { useStoreOptions } from '../../shared/components/useStoreOptions'
import { loadCatalog } from '../equipment/equipmentCatalogApi'
import { repairApi, type RepairTask, type RepairLabor, type RepairCharge } from './repairApi'

const storeId = ref('')
const equipmentId = ref('')
const { options: equipment, loading: equipmentLoading, error: equipmentError, refresh: refreshEquipment } =
  useStoreOptions(storeId, id => loadCatalog(id, 'WAITING_REPAIR', ''))
const { options: contractors, loading: contractorsLoading, error: contractorsError, refresh: refreshContractors } =
  useStoreOptions(storeId, repairApi.assignmentChoices)
const faultDescription = ref('')
const initialContractorId = ref('')
const assignmentId = ref('')
const rejectionReason = ref('')
const tasks = ref<RepairTask[]>([])
const selected = ref<RepairTask | null>(null)
const labors = ref<RepairLabor[]>([])
const charge = ref<RepairCharge | null>(null)
const repairableEquipment = computed(() => equipment.value.filter(row =>
  !tasks.value.some(task => task.equipmentId === row.equipmentId && task.status !== 'COMPLETED')))
const error = ref('')
const busy = ref(false)

watch(storeId, () => { equipmentId.value = ''; initialContractorId.value = ''; assignmentId.value = ''; tasks.value = []; selected.value = null; labors.value = []; charge.value = null })
async function run(operation: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await operation() } catch (failure) { error.value = failure instanceof Error ? errorText(failure) : '维修操作失败，已保留您的输入。' }
  finally { busy.value = false }
}
function load() { return run(async () => {
  refreshEquipment()
  refreshContractors()
  tasks.value = await repairApi.employeeTasks(storeId.value)
}) }
function create() { return run(async () => {
  const created = await repairApi.create(storeId.value, equipmentId.value, faultDescription.value, initialContractorId.value || null)
  setTask(created)
  tasks.value = [created, ...tasks.value]
  equipmentId.value = ''; faultDescription.value = ''; initialContractorId.value = ''
  refreshEquipment()
  tasks.value = await repairApi.employeeTasks(storeId.value)
}) }
function setTask(task: RepairTask) {
  selected.value = task; assignmentId.value = task.currentContractorId ?? ''
  labors.value = []; charge.value = null; rejectionReason.value = ''
}
function select(event: Event) {
  const id = (event.target as HTMLSelectElement).value
  const task = tasks.value.find(row => row.taskId === id)
  if (!task) return
  return run(async () => {
    setTask(task)
    labors.value = await repairApi.employeeLabors(task.taskId)
    charge.value = await repairApi.employeeCharge(task.taskId)
  })
}
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
    <StoreSelect
      v-model="storeId"
      label="门店编号"
      role="EMPLOYEE"
      :disabled="busy"
    />
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
    <label>待维修雪具<select
      v-model="equipmentId"
      aria-label="待维修雪具"
      :disabled="busy || equipmentLoading || !repairableEquipment.length"
    >
      <option
        value=""
        disabled
      >{{ equipmentLoading ? '正在加载雪具…' : repairableEquipment.length ? '选择雪具' : '暂无可创建任务的雪具' }}</option>
      <option
        v-for="row in repairableEquipment"
        :key="row.equipmentId"
        :value="row.equipmentId"
      >
        {{ row.inventoryNumber }} · {{ row.equipmentName }}
      </option>
    </select></label>
    <p
      v-if="equipmentError"
      role="alert"
    >
      {{ equipmentError }}
    </p>
    <label>故障描述<input
      v-model.trim="faultDescription"
      aria-label="故障描述"
      maxlength="500"
    ></label>
    <label>初始承包商（选填）<select
      v-model="initialContractorId"
      aria-label="初始承包商（选填）"
      :disabled="busy || contractorsLoading"
    >
      <option value="">稍后分派</option>
      <option
        v-for="row in contractors"
        :key="row.contractorId"
        :value="row.contractorId"
      >
        {{ row.name }} · {{ row.number }}
      </option>
    </select></label>
    <p
      v-if="contractorsError"
      role="alert"
    >
      {{ contractorsError }}
    </p>
    <button
      data-testid="create-repair"
      :disabled="busy || !repairableEquipment.some(row => row.equipmentId === equipmentId) || !faultDescription"
      @click="create"
    >
      创建维修任务
    </button>

    <h2>门店维修任务</h2>
    <label>维修任务<select
      :value="selected?.taskId ?? ''"
      aria-label="维修任务"
      :disabled="busy || !tasks.length"
      @change="select"
    >
      <option
        value=""
        disabled
      >{{ tasks.length ? '选择维修单' : '暂无已加载的维修任务' }}</option>
      <option
        v-for="task in tasks"
        :key="task.taskId"
        :value="task.taskId"
      >
        {{ task.number }} · {{ uiText(task.status, 'repair') }} · 雪具 {{ task.equipmentId }}
      </option>
    </select></label>

    <section
      v-if="selected"
      aria-label="所选维修任务"
    >
      <h2>{{ selected.number }} · {{ uiText(selected.status, 'repair') }}</h2>
      <p>雪具 {{ selected.equipmentId }} · {{ selected.faultDescription }}</p>
      <label v-if="['UNASSIGNED', 'PENDING', 'IN_PROGRESS', 'RETURNED'].includes(selected.status)">承包商<select
        v-model="assignmentId"
        aria-label="承包商"
        :disabled="busy || contractorsLoading || !contractors.length"
      >
        <option
          value=""
          disabled
        >{{ contractorsLoading ? '正在加载承包商…' : contractors.length ? '选择承包商' : '暂无可分派承包商' }}</option>
        <option
          v-for="row in contractors"
          :key="row.contractorId"
          :value="row.contractorId"
        >
          {{ row.name }} · {{ row.number }}
        </option>
      </select></label>
      <button
        v-if="['UNASSIGNED', 'PENDING', 'IN_PROGRESS', 'RETURNED'].includes(selected.status)"
        :disabled="busy || !contractors.some(row => row.contractorId === assignmentId)"
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

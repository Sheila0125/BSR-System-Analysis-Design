<script setup lang="ts">
import { errorText } from '../../shared/presentation/zh'
import { uiText } from '../../shared/presentation/zh'
import { computed, ref } from 'vue'
import { repairApi, type RepairTask, type RepairLabor } from './repairApi'

type View = 'unstarted' | 'active' | 'submitted' | 'fees'
const view = ref<View>('unstarted')
const tasks = ref<RepairTask[]>([])
const selected = ref<RepairTask | null>(null)
const labors = ref<RepairLabor[]>([])
const fees = ref<RepairLabor[]>([])
const repairDate = ref('')
const durationHours = ref('')
const handlingResult = ref('')
const note = ref('')
const busy = ref(false)
const error = ref('')
const validDuration = computed(() => /^(?:0\.(?:25|50|75)|[1-9]\d{0,5}\.(?:00|25|50|75))$/.test(durationHours.value))

async function run(operation: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await operation() } catch (failure) { error.value = failure instanceof Error ? errorText(failure) : '维修操作失败，已保留您的输入。' }
  finally { busy.value = false }
}
function openView(next: View) { return run(async () => {
  view.value = next; selected.value = null; labors.value = []
  if (next === 'fees') fees.value = await repairApi.feeHistory()
  else tasks.value = await repairApi.mine(next)
}) }
function select(task: RepairTask) { return run(async () => {
  selected.value = task
  labors.value = await repairApi.myLabors(task.taskId)
}) }
function start() { return run(async () => {
  if (!selected.value) return
  selected.value = await repairApi.start(selected.value.taskId)
}) }
function addLabor() { return run(async () => {
  if (!selected.value || !validDuration.value) return
  await repairApi.addLabor(selected.value.taskId, repairDate.value,
    durationHours.value, handlingResult.value, note.value || null)
  labors.value = await repairApi.myLabors(selected.value.taskId)
  repairDate.value = ''; durationHours.value = ''; handlingResult.value = ''; note.value = ''
}) }
function submit() { return run(async () => {
  if (!selected.value) return
  selected.value = await repairApi.submit(selected.value.taskId)
}) }
</script>

<template>
  <section>
    <h1>我的维修任务</h1>
    <p>仅可处理当前分派给自己的任务，费用历史仅展示本人工时（含重新分派前的记录）。</p>
    <nav aria-label="维修任务视图">
      <button
        data-testid="view-unstarted"
        :disabled="busy"
        @click="openView('unstarted')"
      >
        待开始
      </button>
      <button
        data-testid="view-active"
        :disabled="busy"
        @click="openView('active')"
      >
        进行中
      </button>
      <button
        data-testid="view-submitted"
        :disabled="busy"
        @click="openView('submitted')"
      >
        已提交及历史
      </button>
      <button
        data-testid="view-fees"
        :disabled="busy"
        @click="openView('fees')"
      >
        我的费用
      </button>
    </nav>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <template v-if="view === 'fees'">
      <h2>我的工时费用</h2>
      <ul>
        <li
          v-for="fee in fees"
          :key="fee.laborId"
        >
          任务 {{ fee.taskId }} · {{ fee.repairDate }} · {{ fee.durationHours }} 小时 · {{ fee.laborAmount ?? '费率待补齐' }}
        </li>
      </ul>
    </template>
    <template v-else>
      <ul>
        <li
          v-for="task in tasks"
          :key="task.taskId"
        >
          <button
            :data-testid="`select-task-${task.taskId}`"
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
        <p>{{ selected.faultDescription }}</p>
        <button
          v-if="['PENDING', 'RETURNED'].includes(selected.status)"
          data-testid="start-repair"
          :disabled="busy"
          @click="start"
        >
          开始维修
        </button>
        <template v-if="selected.status === 'IN_PROGRESS'">
          <h3>登记已完成工时</h3>
          <label>维修日期<input
            v-model="repairDate"
            aria-label="维修日期"
            type="date"
          ></label>
          <label>工时（小时）<input
            v-model.trim="durationHours"
            aria-label="工时（小时）"
            inputmode="decimal"
          ></label>
          <p v-if="durationHours && !validDuration">
            请输入正数，按 0.25 小时递增并保留两位小数，系统不会自动取整。
          </p>
          <label>处理结果<input
            v-model.trim="handlingResult"
            aria-label="处理结果"
            maxlength="500"
          ></label>
          <label>备注<input
            v-model.trim="note"
            aria-label="备注"
            maxlength="500"
          ></label>
          <button
            data-testid="add-labor"
            :disabled="busy || !repairDate || !validDuration || !handlingResult"
            @click="addLabor"
          >
            保存工时
          </button>
          <button
            :disabled="busy || labors.length === 0"
            @click="submit"
          >
            提交维修完成
          </button>
        </template>
        <h3>我在此任务的工时</h3>
        <ul>
          <li
            v-for="labor in labors"
            :key="labor.laborId"
          >
            {{ labor.repairDate }} · {{ labor.durationHours }} 小时 · {{ labor.laborAmount ?? '费率待补齐' }}
          </li>
        </ul>
      </section>
    </template>
  </section>
</template>

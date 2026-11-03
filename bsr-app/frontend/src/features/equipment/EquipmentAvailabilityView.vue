<script setup lang="ts">
import { problemText } from '../../shared/presentation/zh'
import { uiText } from '../../shared/presentation/zh'
import { computed, ref } from 'vue'
import { ApiError } from '../../shared/http/apiClient'
import { loadAvailableEquipment, type AvailableEquipment } from './equipmentAvailabilityApi'

const storeId = ref('')
const category = ref('')
const skillLevel = ref('')
const equipment = ref<AvailableEquipment[]>([])
const selectedIds = ref<string[]>([])
const loaded = ref(false)
const loading = ref(false)
const errorMessage = ref('')
const selected = computed(() => equipment.value.filter(item => selectedIds.value.includes(item.equipmentId)))

async function search() {
  errorMessage.value = ''
  loaded.value = false
  equipment.value = []
  selectedIds.value = []
  if (!/^[1-9]\d*$/.test(storeId.value)) {
    errorMessage.value = '请输入有效的正整数门店编号。'
    return
  }
  loading.value = true
  try {
    equipment.value = await loadAvailableEquipment(storeId.value, category.value, skillLevel.value)
    loaded.value = true
  } catch (error) {
    errorMessage.value = error instanceof ApiError
      ? problemText(error.problem)
      : '雪具查询失败，请检查连接后重试。'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="equipment-page">
    <p class="eyebrow">
      门店雪具
    </p>
    <h1>可租雪具</h1>
    <p>查询授权门店的可租雪具。当前选择不会预留库存，创建合同时将再次核验。</p>

    <form
      class="equipment-search"
      @submit.prevent="search"
    >
      <label>门店编号<input
        v-model.trim="storeId"
        inputmode="numeric"
        required
      ></label>
      <label>雪具类别<input
        v-model.trim="category"
        maxlength="40"
        placeholder="选填，例如 SKI（双板）"
      ></label>
      <label>技术等级<input
        v-model.trim="skillLevel"
        maxlength="40"
        placeholder="选填，例如 INTERMEDIATE（中级）"
      ></label>
      <button
        type="submit"
        :disabled="loading"
      >
        {{ loading ? '查询中…' : '查询可租雪具' }}
      </button>
    </form>

    <p
      v-if="errorMessage"
      role="alert"
    >
      {{ errorMessage }}
    </p>
    <p v-if="loaded && equipment.length === 0">
      没有符合条件的可租雪具。
    </p>
    <p
      v-if="loaded && equipment.length > 0"
      role="status"
    >
      {{ equipment.length }} 件可租雪具。
    </p>

    <table v-if="equipment.length > 0">
      <caption>当前可租雪具（选择不代表预留）</caption>
      <thead>
        <tr>
          <th scope="col">
            选择
          </th><th scope="col">
            库存编号
          </th><th scope="col">
            雪具
          </th><th scope="col">
            雪具类别
          </th><th scope="col">
            技术等级
          </th><th scope="col">
            日租金
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="item in equipment"
          :key="item.equipmentId"
        >
          <td>
            <input
              v-model="selectedIds"
              type="checkbox"
              :value="item.equipmentId"
              :aria-label="`选择 ${item.inventoryNumber}`"
            >
          </td>
          <td>{{ item.inventoryNumber }}</td>
          <td>{{ item.equipmentName }}</td>
          <td>{{ uiText(item.equipmentCategory) }}</td>
          <td>{{ uiText(item.skillLevel) }}</td>
          <td>{{ item.currentDailyRate }}</td>
        </tr>
      </tbody>
    </table>

    <section
      v-if="selected.length > 0"
      aria-label="已选雪具"
    >
      <h2>已选雪具</h2>
      <p>{{ selected.map(item => item.inventoryNumber).join(', ') }}</p>
      <p>选择不会锁定或预留雪具，创建合同时将再次核验可用状态。</p>
    </section>
  </section>
</template>

<style scoped>
.equipment-page { max-width: 70rem; }
.equipment-search, label { display: grid; gap: .5rem; }
.equipment-search { gap: 1rem; margin-block: 1.5rem; max-width: 30rem; }
input { min-height: 2.75rem; padding: .65rem; }
input[type='checkbox'] { min-height: auto; }
button { width: fit-content; padding: .7rem 1.2rem; }
[role='alert'] { color: #9b1c1c; }
table { border-collapse: collapse; width: 100%; }
th, td { border-bottom: 1px solid #ddd; padding: .7rem; text-align: left; }
</style>

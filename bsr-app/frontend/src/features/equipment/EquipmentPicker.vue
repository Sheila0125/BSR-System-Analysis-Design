<script setup lang="ts">
import { computed, ref, toRef, watch } from 'vue'
import { uiText } from '../../shared/presentation/zh'
import { useStoreOptions } from '../../shared/components/useStoreOptions'
import { conciseEquipmentName, conciseInventoryNumber } from './equipmentDisplay'
import { loadAvailableEquipment, type AvailableEquipment } from './equipmentAvailabilityApi'

interface EquipmentRequirement {
  equipmentCategory: string
  quantity: number
}

const props = defineProps<{
  storeId: string
  disabled?: boolean
  transfer?: boolean
  requirements?: EquipmentRequirement[]
  preferredSkillLevel?: string
}>()
const emit = defineEmits<{ selection: [items: AvailableEquipment[]] }>()
const { options: equipment, loading, error, refresh } = useStoreOptions(toRef(props, 'storeId'),
  id => loadAvailableEquipment(id, '', ''))
const chosenId = ref('')
const selectedIds = ref<string[]>([])
const selected = computed(() => selectedIds.value.flatMap(id => {
  const item = equipment.value.find(row => row.equipmentId === id)
  return item ? [item] : []
}))
const requiredQuantities = computed(() => {
  const counts = new Map<string, number>()
  props.requirements?.forEach(item => counts.set(
    item.equipmentCategory,
    (counts.get(item.equipmentCategory) ?? 0) + item.quantity,
  ))
  return counts
})
const selectedQuantities = computed(() => {
  const counts = new Map<string, number>()
  selected.value.forEach(item => counts.set(
    item.equipmentCategory,
    (counts.get(item.equipmentCategory) ?? 0) + 1,
  ))
  return counts
})
const remaining = computed(() => equipment.value.filter(row => {
  if (selectedIds.value.includes(row.equipmentId)) return false
  if (props.preferredSkillLevel && row.skillLevel !== props.preferredSkillLevel) return false
  if (!requiredQuantities.value.size) return true
  const required = requiredQuantities.value.get(row.equipmentCategory) ?? 0
  return required > (selectedQuantities.value.get(row.equipmentCategory) ?? 0)
}))
const progress = computed(() => [...requiredQuantities.value].map(([equipmentCategory, quantity]) => ({
  equipmentCategory,
  quantity,
  selected: selectedQuantities.value.get(equipmentCategory) ?? 0,
})))

watch(() => props.storeId, clearSelection)
watch(selectedIds, () => emit('selection', selected.value))
function clearSelection() {
  chosenId.value = ''
  selectedIds.value = []
}
function reload() {
  clearSelection()
  refresh()
}
function add() {
  if (!remaining.value.some(row => row.equipmentId === chosenId.value)) return
  selectedIds.value = [...selectedIds.value, chosenId.value]
  chosenId.value = ''
}
</script>

<template>
  <fieldset :disabled="disabled || !storeId">
    <legend>{{ transfer ? '选择调拨雪具' : '选择具体雪具（不预留库存）' }}</legend>
    <ul
      v-if="progress.length"
      class="picking-progress"
      aria-label="配货进度"
    >
      <li
        v-for="item in progress"
        :key="item.equipmentCategory"
        :class="{ complete: item.selected === item.quantity }"
      >
        {{ uiText(item.equipmentCategory) }} {{ item.selected }} / {{ item.quantity }}
      </li>
    </ul>
    <label>
      {{ transfer ? '可调拨雪具' : '可租雪具' }}
      <select
        v-model="chosenId"
        :aria-label="transfer ? '可调拨雪具' : '可租雪具'"
        :disabled="loading || !!error || !remaining.length"
      >
        <option
          value=""
          disabled
        >
          {{ loading ? '正在加载雪具…' : remaining.length ? '选择雪具' : '暂无可选雪具' }}
        </option>
        <option
          v-for="item in error ? [] : remaining"
          :key="item.equipmentId"
          :value="item.equipmentId"
          :title="item.inventoryNumber"
        >
          {{ conciseEquipmentName(item.equipmentName) }} · {{ uiText(item.equipmentCategory) }} · {{ uiText(item.skillLevel) }} · {{ conciseInventoryNumber(item.inventoryNumber) }}{{ transfer ? '' : ` · ${item.currentDailyRate} NOK / 日` }}
        </option>
      </select>
    </label>
    <div class="picker-actions">
      <button
        type="button"
        :disabled="loading || !!error || !chosenId"
        @click="add"
      >
        添加雪具
      </button>
      <button
        type="button"
        :disabled="loading"
        @click="reload"
      >
        {{ transfer ? '刷新可调拨雪具' : '刷新可租雪具' }}
      </button>
    </div>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <ul
      v-if="selected.length"
      aria-label="已选雪具"
    >
      <li
        v-for="item in selected"
        :key="item.equipmentId"
        :title="item.inventoryNumber"
      >
        {{ conciseEquipmentName(item.equipmentName) }} · {{ uiText(item.equipmentCategory) }} · {{ conciseInventoryNumber(item.inventoryNumber) }}{{ transfer ? '' : ` · ${item.currentDailyRate} NOK / 日` }}
        <button
          type="button"
          :aria-label="`移除 ${item.inventoryNumber}`"
          @click="selectedIds = selectedIds.filter(id => id !== item.equipmentId)"
        >
          移除
        </button>
      </li>
    </ul>
  </fieldset>
</template>

<style scoped>
fieldset { display: grid; gap: .65rem; min-width: 0; }
label, select { min-width: 0; max-width: 100%; }
select { width: 100%; }
.picker-actions { display: flex; flex-wrap: wrap; gap: .5rem; }
.picking-progress { display: flex; flex-wrap: wrap; gap: .5rem; list-style: none; margin: 0; padding: 0; }
.picking-progress li { border: 1px solid #b8c7d5; border-radius: 999px; padding: .35rem .7rem; background: #f4f8fb; }
.picking-progress li.complete { border-color: #2f7d62; background: #eaf7f0; color: #17633a; }
li { overflow-wrap: anywhere; }
</style>

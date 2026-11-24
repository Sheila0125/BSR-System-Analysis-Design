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
const quantity = ref(1)
const removedNumbers = ref<string[]>([])
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
function groupKey(item: AvailableEquipment) {
  return props.transfer ? item.equipmentId
    : JSON.stringify([item.equipmentName, item.equipmentCategory, item.skillLevel, item.currentDailyRate])
}
const groups = computed(() => {
  const result = new Map<string, { first: AvailableEquipment; items: AvailableEquipment[] }>()
  for (const item of remaining.value) {
    const key = groupKey(item)
    const group = result.get(key)
    if (group) group.items.push(item)
    else result.set(key, { first: item, items: [item] })
  }
  return [...result.values()]
})
const chosenGroup = computed(() => groups.value.find(group => group.first.equipmentId === chosenId.value))
const limit = computed(() => {
  const group = chosenGroup.value
  if (!group) return 0
  const category = group.first.equipmentCategory
  const required = requiredQuantities.value.get(category)
  if (required === undefined) return group.items.length
  return Math.min(group.items.length, required - (selectedQuantities.value.get(category) ?? 0))
})
const canAdd = computed(() => !loading.value && !error.value && Number.isInteger(quantity.value)
  && quantity.value > 0 && quantity.value <= limit.value)
const progress = computed(() => [...requiredQuantities.value].map(([equipmentCategory, quantity]) => ({
  equipmentCategory,
  quantity,
  selected: selectedQuantities.value.get(equipmentCategory) ?? 0,
})))

watch(() => props.storeId, clearSelection)
watch(chosenId, () => { quantity.value = 1 })
watch(equipment, (rows, previous) => {
  const available = new Set(rows.map(item => item.equipmentId))
  const missing = selectedIds.value.filter(id => !available.has(id))
  if (missing.length) {
    removedNumbers.value = previous.filter(row => missing.includes(row.equipmentId)).map(row => row.inventoryNumber)
    selectedIds.value = selectedIds.value.filter(id => available.has(id))
  }
})
watch([selected, loading, error], () => emit('selection', loading.value || error.value ? [] : selected.value))
function clearSelection() {
  chosenId.value = ''
  selectedIds.value = []
  removedNumbers.value = []
}
function reload() {
  chosenId.value = ''
  removedNumbers.value = []
  refresh()
}
function add() {
  if (!canAdd.value || !chosenGroup.value) return
  const added = chosenGroup.value.items.slice(0, quantity.value)
  selectedIds.value = [...selectedIds.value, ...added.map(item => item.equipmentId)]
  chosenId.value = ''
}
function alternatives(item: AvailableEquipment) {
  const key = groupKey(item)
  return equipment.value.filter(row => groupKey(row) === key
    && (row.equipmentId === item.equipmentId || !selectedIds.value.includes(row.equipmentId)))
}
function swap(item: AvailableEquipment, event: Event) {
  const id = (event.target as HTMLSelectElement).value
  if (alternatives(item).some(row => row.equipmentId === id)) {
    selectedIds.value = selectedIds.value.map(current => current === item.equipmentId ? id : current)
  }
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
          v-for="group in error ? [] : groups"
          :key="group.first.equipmentId"
          :value="group.first.equipmentId"
        >
          {{ conciseEquipmentName(group.first.equipmentName) }} · {{ uiText(group.first.equipmentCategory) }} · {{ uiText(group.first.skillLevel) }}{{ transfer ? '' : ` · ${group.first.currentDailyRate} NOK / 日` }} · {{ group.items.length === 1 ? conciseInventoryNumber(group.first.inventoryNumber) : `可选 ${group.items.length} 件` }}
        </option>
      </select>
    </label>
    <label v-if="chosenGroup && !transfer">
      配货数量
      <input
        v-model.number="quantity"
        type="number"
        aria-label="配货数量"
        min="1"
        :max="limit"
        step="1"
      >
      <small>本次最多 {{ limit }} 件</small>
    </label>
    <div class="picker-actions">
      <button
        type="button"
        :disabled="!canAdd"
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
    <p
      v-if="removedNumbers.length"
      role="status"
    >
      已移除不可用的库存编号：{{ removedNumbers.join('、') }}。
    </p>
    <ul
      v-if="selected.length && !error"
      aria-label="已选雪具"
    >
      <li
        v-for="item in selected"
        :key="item.equipmentId"
        :title="item.inventoryNumber"
      >
        {{ conciseEquipmentName(item.equipmentName) }} · {{ uiText(item.equipmentCategory) }} · {{ conciseInventoryNumber(item.inventoryNumber) }}{{ transfer ? '' : ` · ${item.currentDailyRate} NOK / 日` }}
        <label v-if="!transfer && alternatives(item).length > 1">
          更换实物编号
          <select
            :value="item.equipmentId"
            :aria-label="`更换 ${item.inventoryNumber}`"
            :disabled="loading"
            @change="swap(item, $event)"
          >
            <option
              v-for="option in alternatives(item)"
              :key="option.equipmentId"
              :value="option.equipmentId"
            >
              {{ option.inventoryNumber }}
            </option>
          </select>
        </label>
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

<script setup lang="ts">
import { computed, ref, toRef, watch } from 'vue'
import { uiText } from '../../shared/presentation/zh'
import { useStoreOptions } from '../../shared/components/useStoreOptions'
import { loadAvailableEquipment, type AvailableEquipment } from './equipmentAvailabilityApi'

const props = defineProps<{ storeId: string; disabled?: boolean; transfer?: boolean }>()
const emit = defineEmits<{ selection: [items: AvailableEquipment[]] }>()
const { options: equipment, loading, error, refresh } = useStoreOptions(toRef(props, 'storeId'),
  id => loadAvailableEquipment(id, '', ''))
const chosenId = ref('')
const selectedIds = ref<string[]>([])
const selected = computed(() => selectedIds.value.flatMap(id => {
  const item = equipment.value.find(row => row.equipmentId === id)
  return item ? [item] : []
}))
const remaining = computed(() => equipment.value.filter(row => !selectedIds.value.includes(row.equipmentId)))

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
        >
          {{ item.inventoryNumber }} · {{ item.equipmentName }} · {{ uiText(item.equipmentCategory) }} · {{ uiText(item.skillLevel) }}{{ transfer ? '' : ` · ${item.currentDailyRate} NOK / 日` }}
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
      >
        {{ item.inventoryNumber }} · {{ item.equipmentName }}{{ transfer ? '' : ` · ${item.currentDailyRate} NOK / 日` }}
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
li { overflow-wrap: anywhere; }
</style>

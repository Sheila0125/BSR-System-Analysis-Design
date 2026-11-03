<script setup lang="ts">
import { errorText } from '../../shared/presentation/zh'
import { uiText } from '../../shared/presentation/zh'
import { ref, watch } from 'vue'
import { loadAvailableEquipment, type AvailableEquipment } from './equipmentAvailabilityApi'

const props = defineProps<{ storeId: string }>()
const emit = defineEmits<{ selection: [items: AvailableEquipment[]] }>()
const equipment = ref<AvailableEquipment[]>([])
const selected = ref<string[]>([])
const loading = ref(false)
const error = ref('')
function reset() {
  equipment.value = []
  selected.value = []
  emit('selection', [])
}
watch(() => props.storeId, reset)
watch(selected, () => emit('selection', equipment.value.filter(item => selected.value.includes(item.equipmentId))))
async function search() {
  if (loading.value) return
  reset()
  error.value = ''
  if (!/^[1-9]\d*$/.test(props.storeId)) {
    error.value = '请先填写已获授权的门店编号。'
    return
  }
  loading.value = true
  const observedStore = props.storeId
  try {
    const result = await loadAvailableEquipment(observedStore, '', '')
    if (observedStore === props.storeId) equipment.value = result
  } catch (failure) {
    error.value = failure instanceof Error ? errorText(failure) : '雪具查询失败。'
  } finally { loading.value = false }
}
</script>

<template>
  <fieldset>
    <legend>选择具体雪具（不预留库存）</legend>
    <button
      type="button"
      :disabled="loading"
      @click="search"
    >
      刷新可租雪具
    </button>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <label
      v-for="item in equipment"
      :key="item.equipmentId"
    >
      <input
        v-model="selected"
        type="checkbox"
        :value="item.equipmentId"
      >
      {{ item.inventoryNumber }} · {{ uiText(item.equipmentCategory) }} · {{ uiText(item.skillLevel) }} · {{ item.currentDailyRate }} / 日
    </label>
  </fieldset>
</template>

<style scoped>
fieldset { display: grid; gap: .65rem; min-width: 0; }
button { justify-self: start; }
label {
  display: flex;
  align-items: flex-start;
  gap: .65rem;
  padding: .65rem;
  border: 1px solid #e1e9f5;
  border-radius: .5rem;
  background: #f8fbff;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
input[type="checkbox"] { flex: 0 0 auto; width: 1.1rem; height: 1.1rem; min-height: 0; margin: .15rem 0 0; }
</style>

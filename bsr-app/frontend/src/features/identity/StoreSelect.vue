<script setup lang="ts">
import { computed, inject, watch } from 'vue'
import { sessionActorKey, storeChoicesKey, type StoreRole } from './sessionStores'

const value = defineModel<string>({ required: true })
const props = withDefaults(defineProps<{
  label?: string
  role: StoreRole | 'EITHER'
  disabled?: boolean
  exclude?: string
  selectOnlyOption?: boolean
}>(), { label: '门店编号', exclude: '', selectOnlyOption: true })
const actor = inject(sessionActorKey, null)
const names = inject(storeChoicesKey, null)
const ids = computed(() => [...new Set((actor?.value?.identityType === 'EMPLOYEE' ? actor.value.grants : []).flatMap(grant =>
  grant.storeId && grant.storeId !== props.exclude &&
  (grant.role === props.role || (props.role === 'EITHER' && ['EMPLOYEE', 'MANAGER'].includes(grant.role)))
    ? [grant.storeId] : []))])
const nameById = computed(() => new Map(names?.value.map(store =>
  [store.storeId, `${store.storeName} · ${store.storeNumber}`])))

watch(ids, options => {
  if (!options.includes(value.value)) {
    value.value = props.selectOnlyOption && options.length === 1 ? options[0]! : ''
  }
}, { immediate: true })
</script>

<template>
  <label>
    {{ label }}
    <select
      v-model="value"
      :aria-label="label"
      :disabled="disabled || !ids.length"
    >
      <option
        value=""
        disabled
      >
        {{ ids.length ? '请选择门店' : '暂无授权门店' }}
      </option>
      <option
        v-for="id in ids"
        :key="id"
        :value="id"
      >
        {{ nameById.get(id) ?? `门店 ${id}` }}
      </option>
    </select>
  </label>
</template>

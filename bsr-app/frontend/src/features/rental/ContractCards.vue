<script setup lang="ts">
import { computed, ref } from 'vue'
import { uiText } from '../../shared/presentation/zh'
import { contractKeywords } from './contractKeywords'
import type { Contract } from './rentalApi'

const props = defineProps<{
  contracts: Contract[]
  categories: ReadonlyMap<string, string>
  label: string
  disabled?: boolean
  selectedId?: string
}>()
const emit = defineEmits<{ select: [contract: Contract] }>()
const query = ref('')
const visible = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase()
  return props.contracts
    .filter(row => [contractKeywords(row, props.categories), row.contractNumber, row.customerId, uiText(row.status)]
      .some(value => value.toLocaleLowerCase().includes(keyword)))
    .sort((a, b) => b.rentalStartDate.localeCompare(a.rentalStartDate))
})
</script>

<template>
  <section :aria-label="label">
    <h3>{{ label }}</h3>
    <input
      v-if="contracts.length > 1"
      v-model="query"
      type="search"
      aria-label="搜索合同"
      placeholder="搜索雪具、状态、合同号或顾客编号"
      :disabled="disabled"
    >
    <div class="contract-grid">
      <button
        v-for="row in visible"
        :key="row.contractId"
        type="button"
        :data-test="`contract-${row.contractId}`"
        :aria-pressed="selectedId === row.contractId"
        :disabled="disabled"
        @click="emit('select', row)"
      >
        <span class="card-title">{{ contractKeywords(row, categories) }}</span>
        <span class="card-meta">{{ row.rentalStartDate }} · {{ row.rentalDays }} 天 · 顾客 {{ row.customerId }}</span>
        <span class="card-status">{{ uiText(row.status) }}</span>
      </button>
    </div>
    <p v-if="!visible.length">
      没有符合关键词的合同。
    </p>
  </section>
</template>

<style scoped>
.contract-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 14rem), 1fr)); gap: .65rem; margin: .65rem 0; }
button { display: grid; gap: .3rem; margin: 0; text-align: left; border: 1px solid #cbd8e3; background: white; color: #183b4b; border-radius: .7rem; overflow-wrap: anywhere; }
button:hover, button[aria-pressed="true"] { border-color: #28738d; background: #f0f9fd; }
.card-meta { color: #506675; font-size: .85rem; }
.card-title { font-weight: 750; }
.card-status { color: #176f8c; font-size: .85rem; }
input { width: min(100%, 26rem); }
</style>

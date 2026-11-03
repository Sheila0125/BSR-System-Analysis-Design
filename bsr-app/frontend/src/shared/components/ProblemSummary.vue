<script setup lang="ts">
import type { ApiProblem } from '../http/apiClient'
import { problemText } from '../presentation/zh'

defineProps<{
  problem: ApiProblem
}>()
</script>

<template>
  <section
    role="alert"
    aria-live="polite"
  >
    <h2>操作未完成</h2>
    <p>{{ problemText(problem) }}</p>
    <ul v-if="Object.keys(problem.fieldErrors).length > 0">
      <li
        v-for="(message, field) in problem.fieldErrors"
        :key="field"
      >
        <strong>{{ field }}</strong>: {{ message }}
      </li>
    </ul>
    <p>追踪编号： {{ problem.traceId }}</p>
  </section>
</template>

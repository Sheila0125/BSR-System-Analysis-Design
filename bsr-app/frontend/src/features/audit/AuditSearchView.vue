<script setup lang="ts">
import { problemText } from '../../shared/presentation/zh'
import { uiText } from '../../shared/presentation/zh'
import { ref } from 'vue'
import { ApiError } from '../../shared/http/apiClient'
import { findAuditEvents, type AuditEvent } from './auditApi'

const objectType = ref('ROLE_GRANT')
const objectId = ref('')
const events = ref<AuditEvent[]>([])
const searched = ref(false)
const loading = ref(false)
const errorMessage = ref('')

async function search() {
  errorMessage.value = ''
  searched.value = false
  if (!/^[A-Z][A-Z0-9_]{0,39}$/.test(objectType.value)) {
    errorMessage.value = '业务对象类型须填写大写编码，例如 ROLE_GRANT。'
    return
  }
  if (!/^[1-9]\d*$/.test(objectId.value)) {
    errorMessage.value = '请输入有效的正整数业务对象编号。'
    return
  }
  loading.value = true
  try {
    events.value = await findAuditEvents(objectType.value, objectId.value)
    searched.value = true
  } catch (error) {
    events.value = []
    errorMessage.value = error instanceof ApiError
      ? problemText(error.problem)
      : '审计查询失败，请检查连接后重试。'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="audit-page">
    <p class="eyebrow">
      经理业务
    </p>
    <h1>审计日志</h1>
    <p>查询授权范围内业务对象的操作记录。</p>

    <form
      class="audit-search"
      @submit.prevent="search"
    >
      <label>业务对象类型<input
        v-model.trim="objectType"
        maxlength="40"
        required
      ></label>
      <label>业务对象编号<input
        v-model.trim="objectId"
        inputmode="numeric"
        required
      ></label>
      <button
        type="submit"
        :disabled="loading"
      >
        {{ loading ? '查询中…' : '查询审计记录' }}
      </button>
    </form>

    <p
      v-if="errorMessage"
      role="alert"
    >
      {{ errorMessage }}
    </p>
    <p
      v-else-if="searched && events.length === 0"
      role="status"
    >
      暂无审计记录。
    </p>

    <div
      v-if="events.length"
      class="table-scroll"
    >
      <table>
        <caption>{{ objectType }} {{ objectId }} — 最新记录在前</caption>
        <thead>
          <tr>
            <th>时间</th><th>操作</th><th>结果</th><th>操作账号</th><th>摘要</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="event in events"
            :key="event.auditEventId"
          >
            <td><time :datetime="event.occurredAt">{{ event.occurredAt.replace('T', ' ') }}</time></td>
            <td>{{ event.actionCode }}</td>
            <td>{{ uiText(event.resultCode) }}</td>
            <td>{{ event.actorAccountId }}</td>
            <td>{{ event.detailSummary }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.audit-page { max-width: 68rem; }
.audit-search, label { display: grid; gap: .5rem; }
.audit-search { gap: 1rem; margin-block: 1.5rem; max-width: 36rem; }
input { min-height: 2.75rem; padding: .65rem; }
button { width: fit-content; padding: .7rem 1.2rem; }
[role="alert"] { color: #9b1c1c; }
.table-scroll { overflow-x: auto; }
table { border-collapse: collapse; width: 100%; }
caption { text-align: left; font-weight: 700; margin-block: 1rem; }
th, td { border-bottom: 1px solid #d8dee8; padding: .75rem; text-align: left; vertical-align: top; }
</style>

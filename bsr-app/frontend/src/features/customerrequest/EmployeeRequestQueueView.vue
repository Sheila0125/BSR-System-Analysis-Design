<script setup lang="ts">
import StoreSelect from '../identity/StoreSelect.vue'
import { problemText } from '../../shared/presentation/zh'
import { uiText } from '../../shared/presentation/zh'
import { computed, ref, watch } from 'vue'
import { ApiError } from '../../shared/http/apiClient'
import { useStoreOptions } from '../../shared/components/useStoreOptions'
import {
  acceptRentalRequest,
  loadEmployeeRentalRequests,
  markRentalRequestUnavailable,
  requestMoreInformation,
  type EmployeeRentalRequest,
} from './employeeRequestApi'

const storeId = ref('')
const { options: requests, loading, error: queueError, refresh } = useStoreOptions(storeId, loadEmployeeRentalRequests)
const newestFirst = computed(() => [...requests.value].reverse())
const processingId = ref('')
const errorMessage = ref('')
const successMessage = ref('')
const reasonById = ref<Record<string, string>>({})

watch(storeId, () => {
  reasonById.value = {}
  successMessage.value = ''
  errorMessage.value = ''
})

function describeError(error: unknown): string {
  return error instanceof ApiError
    ? problemText(error.problem)
    : '请求失败，请检查连接后重试。'
}

function loadQueue() {
  errorMessage.value = ''
  successMessage.value = ''
  if (storeId.value && !loading.value && !processingId.value) refresh()
}

async function process(
  request: EmployeeRentalRequest,
  action: (requestId: string) => Promise<EmployeeRentalRequest>,
  message: string,
) {
  errorMessage.value = ''
  successMessage.value = ''
  processingId.value = request.requestId
  try {
    const updated = await action(request.requestId)
    if (updated.status === 'UNAVAILABLE' || updated.status === 'CONVERTED') {
      requests.value = requests.value.filter(item => item.requestId !== updated.requestId)
    } else {
      requests.value = requests.value.map(item => item.requestId === updated.requestId ? updated : item)
    }
    successMessage.value = `${message} ${updated.requestNumber}.`
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    processingId.value = ''
  }
}

async function requestSupplement(request: EmployeeRentalRequest) {
  const reason = (reasonById.value[request.requestId] ?? '').trim()
  if (!reason || reason.length > 500) {
    errorMessage.value = '请填写补充资料的原因，最多 500 字。'
    return
  }
  await process(request, id => requestMoreInformation(id, reason), '已要求补充资料：')
  if (!errorMessage.value) reasonById.value[request.requestId] = ''
}
</script>

<template>
  <section class="queue-page">
    <p class="eyebrow">
      员工业务
    </p>
    <h1>租赁需求队列</h1>
    <p>顾客新提交的申请会出现在这里。选门店后自动加载，最新的申请排在最前。</p>

    <form
      class="queue-search"
      @submit.prevent="loadQueue"
    >
      <StoreSelect
        v-model="storeId"
        label="门店编号"
        role="EMPLOYEE"
        :disabled="!!processingId"
      />
      <button
        type="submit"
        :disabled="!storeId || loading || !!processingId"
      >
        {{ loading ? '加载中…' : '刷新需求' }}
      </button>
    </form>

    <p
      v-if="errorMessage || queueError"
      role="alert"
    >
      {{ errorMessage || queueError }}
    </p>
    <p
      v-if="successMessage"
      role="status"
    >
      {{ successMessage }}
    </p>
    <p
      v-if="storeId && !loading && !queueError && !requests.length"
      class="empty-state"
    >
      该门店暂无待处理的租赁需求。
    </p>

    <article
      v-for="request in newestFirst"
      :key="request.requestId"
      class="request-card"
    >
      <header>
        <div>
          <h2>{{ request.requestNumber }}</h2>
          <p>来源需求编号（数字 ID）：{{ request.requestId }}</p>
          <p>顾客 {{ request.customerId }} · {{ uiText(request.status) }}</p>
        </div>
        <time :datetime="request.submittedAt">{{ request.submittedAt.replace('T', ' ') }}</time>
      </header>
      <dl>
        <dt>起租日期</dt><dd>{{ request.rentalStartDate }}</dd>
        <dt>整日天数</dt><dd>{{ request.rentalDays }}</dd>
        <dt>当前技术等级</dt><dd>{{ uiText(request.skillLevel) }}</dd>
      </dl>
      <ul>
        <li
          v-for="item in request.items"
          :key="`${item.equipmentCategory}-${item.quantity}-${item.fittingNote}`"
        >
          {{ uiText(item.equipmentCategory) }} × {{ item.quantity }}<span v-if="item.fittingNote"> — {{ item.fittingNote }}</span>
        </li>
      </ul>
      <section
        v-if="request.supplements?.length"
        class="supplement-history"
      >
        <h3>资料补充记录</h3>
        <ol>
          <li
            v-for="cycle in request.supplements"
            :key="cycle.cycleNumber"
          >
            <strong>轮次 {{ cycle.cycleNumber }}:</strong> {{ cycle.reason }}
            <span v-if="cycle.customerResponse"> — 顾客： {{ cycle.customerResponse }}</span>
            <span v-else> — 等待顾客回复</span>
          </li>
        </ol>
      </section>
      <label
        v-if="request.status === 'PENDING'"
        class="reason-field"
      >
        需要顾客补充什么信息？
        <textarea
          v-model="reasonById[request.requestId]"
          maxlength="500"
          rows="3"
        />
      </label>
      <div class="actions">
        <button
          v-if="request.status === 'PENDING'"
          type="button"
          :disabled="loading || !!processingId"
          @click="requestSupplement(request)"
        >
          要求补充资料
        </button>
        <button
          v-if="request.status === 'PENDING'"
          type="button"
          :disabled="loading || !!processingId"
          @click="process(request, acceptRentalRequest, '已受理')"
        >
          受理需求
        </button>
        <button
          v-if="request.status === 'PENDING' || request.status === 'ACCEPTED'"
          type="button"
          :disabled="loading || !!processingId"
          @click="process(request, markRentalRequestUnavailable, '已标记无法满足')"
        >
          无法满足
        </button>
      </div>
    </article>
  </section>
</template>

<style scoped>
.queue-page { max-width: 68rem; }
.queue-search, label { display: grid; gap: .5rem; }
.queue-search { gap: 1rem; margin-block: 1.5rem; max-width: 24rem; }
input { min-height: 2.75rem; padding: .65rem; }
textarea { min-height: 5rem; padding: .65rem; resize: vertical; }
.reason-field { margin-top: 1rem; }
.supplement-history { margin-top: 1rem; }
button { width: fit-content; padding: .7rem 1.2rem; }
[role="alert"] { color: #9b1c1c; }
[role="status"] { color: #17633a; }
.request-card { border: 1px solid #d8dee8; border-radius: .5rem; margin-block: 1rem; padding: 1rem; }
.request-card header { display: flex; justify-content: space-between; gap: 1rem; }
.request-card h2, .request-card p { margin-block: 0 .4rem; }
dl { display: grid; grid-template-columns: minmax(8rem, auto) 1fr; gap: .35rem 1rem; }
dt { font-weight: 700; }
dd { margin: 0; }
.actions { display: flex; flex-wrap: wrap; gap: .75rem; margin-top: 1rem; }
.empty-state { color: #4b5563; }
</style>

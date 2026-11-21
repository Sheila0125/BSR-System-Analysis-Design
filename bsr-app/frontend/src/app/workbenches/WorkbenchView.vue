<script setup lang="ts">
import { errorText } from '../../shared/presentation/zh'
import { computed, inject, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import SkiIcon from '../../shared/components/SkiIcon.vue'
import { uiText } from '../../shared/presentation/zh'
import { currentActor, type CurrentActor } from '../../features/identity/authApi'
import { storeChoicesKey } from '../../features/identity/sessionStores'
import { deactivateStore, operations, type OperationsState } from './operationsApi'

const props = defineProps<{ mode: 'employee' | 'manager' }>()
const actor = ref<CurrentActor | null>(null)
const error = ref('')
const storeId = ref('')
const state = ref<OperationsState | null>(null)
const busy = ref(false)
const confirmStop = ref(false)
const stoppedStores = ref<string[]>([])
const notice = ref('')

const grants = computed(() => actor.value?.identityType === 'EMPLOYEE'
  ? actor.value.grants.filter(grant => grant.role === (props.mode === 'manager' ? 'MANAGER' : 'EMPLOYEE') && grant.storeId)
  : [])
const stores = computed(() => [...new Set(grants.value.map(grant => grant.storeId as string))])
const storeChoices = inject(storeChoicesKey, null)
function storeLabel(id: string) {
  const store = storeChoices?.value.find(choice => choice.storeId === id)
  return store ? `${store.storeName} · ${store.storeNumber}` : `门店 · ${id}`
}
const links = computed(() => props.mode === 'employee' ? [
  { label: '租赁需求', to: '/customer-requests/employee-queue' },
  { label: '顾客核验', to: '/customer-requests/verify-customer' },
  { label: '可租雪具', to: '/equipment/available' },
  { label: '雪具档案', to: '/equipment/catalog' },
  { label: '租赁办理', to: '/rental/desk' },
  { label: '租赁调整', to: '/rental/adjustments' },
  { label: '归还检查', to: '/rental/returns' },
  { label: '维修办理', to: '/repair/employee' },
  { label: '调拨交接', to: '/storetransfer/handoff' },
  { label: '我的班次', to: '/scheduling/my-shifts' },
] : [
  { label: '雪具档案', to: '/equipment/catalog' },
  { label: '可租雪具', to: '/equipment/available' },
  { label: '门店与调拨', to: '/storetransfer/management' },
  { label: '员工排班', to: '/scheduling/management' },
  { label: '维修承包商管理', to: '/repair/management' },
  { label: '账号与权限', to: '/identity/role-grants' },
  { label: '审计日志', to: '/audit/events' },
])

watch(storeId, () => { state.value = null; error.value = ''; notice.value = ''; confirmStop.value = false })
onMounted(async () => {
  try {
    actor.value = await currentActor()
    if (props.mode === 'manager' && stores.value[0]) { storeId.value = stores.value[0]; await loadOperations() }
  }
  catch (failure) { error.value = failure instanceof Error ? errorText(failure) : '账号信息加载失败。' }
})

async function loadOperations() {
  if (busy.value || !stores.value.includes(storeId.value)) return
  busy.value = true
  error.value = ''
  state.value = null
  const requestedStore = storeId.value
  try {
    const result = await operations(requestedStore)
    if (storeId.value === requestedStore) state.value = result
  } catch (failure) {
    if (storeId.value === requestedStore) error.value = failure instanceof Error ? errorText(failure) : '运营状态加载失败。'
  }
  finally { busy.value = false }
}

function statusRows(counts: Record<string, number>): string {
  return Object.entries(counts).map(([name, count]) => `${uiText(name)}：${count}`).join(', ') || '暂无记录'
}

async function stopStore() {
  if (busy.value || !confirmStop.value || !stores.value.includes(storeId.value) || stoppedStores.value.includes(storeId.value)) return
  busy.value = true
  error.value = ''
  notice.value = ''
  const requestedStore = storeId.value
  try {
    const result = await deactivateStore(requestedStore)
    stoppedStores.value.push(requestedStore)
    if (storeId.value === requestedStore) {
      state.value = null
      notice.value = `门店 ${result.storeId} 已停用。`
    }
  } catch (failure) {
    if (storeId.value === requestedStore) error.value = failure instanceof Error ? errorText(failure) : '门店停用失败。'
  } finally { busy.value = false; confirmStop.value = false }
}

const total = (counts: Record<string, number>) => Object.values(counts).reduce((sum, n) => sum + n, 0)
const stats = computed(() => [
  { label: '可租雪具', value: state.value?.equipmentStatuses.AVAILABLE ?? 0, unit: '件', icon: 'ski', foot: `在店雪具共 ${state.value ? total(state.value.equipmentStatuses) : '—'} 件` },
  { label: '待发放合同', value: state.value?.rentalStatuses.PENDING_ISSUE ?? 0, unit: '份', icon: 'file', foot: '按当前合同状态统计' },
  { label: '待处理维修', value: state.value ? total(Object.fromEntries(Object.entries(state.value.repairStatuses).filter(([key]) => key !== 'COMPLETED'))) : 0, unit: '项', icon: 'wrench', foot: '未完成的维修任务' },
  { label: '待处理调拨', value: state.value ? total(Object.fromEntries(Object.entries(state.value.transferStatuses).filter(([key]) => !['COMPLETED','CANCELLED'].includes(key)))) : 0, unit: '项', icon: 'transfer', foot: '未结束的门店调拨' },
])
const rows = computed(() => state.value ? [
  { name: '租赁合同', icon: 'file', counts: state.value.rentalStatuses },
  { name: '雪具装备', icon: 'ski', counts: state.value.equipmentStatuses },
  { name: '维修任务', icon: 'wrench', counts: state.value.repairStatuses },
  { name: '门店调拨', icon: 'transfer', counts: state.value.transferStatuses },
  { name: '员工排班', icon: 'calendar', counts: state.value.shiftStatuses },
] : [])
function linkIcon(to: string) {
  return to.includes('repair') ? 'wrench' : to.includes('scheduling') ? 'calendar' : to.includes('storetransfer') ? 'transfer' : to.includes('equipment') ? 'ski' : to.includes('customer') ? 'users' : to.includes('audit') ? 'clock' : to.includes('identity') ? 'shield' : 'file'
}
</script>

<template>
  <section class="workbench-page">
    <div class="page-head">
      <div><h1>{{ mode === 'manager' ? '雪场工作台' : '员工工作台' }}</h1><p>门店运营一目了然，把更多时间留给雪场。</p></div>
      <div
        v-if="grants.length && mode === 'manager'"
        class="store-controls"
      >
        <label
          class="sr-only"
          for="operations-store"
        >授权门店</label><select
          id="operations-store"
          v-model="storeId"
          aria-label="授权门店"
          :disabled="busy"
        >
          <option value="">
            请选择门店
          </option><option
            v-for="store in stores"
            :key="store"
            :value="store"
          >
            {{ storeLabel(store) }}
          </option>
        </select>
        <button
          class="subtle-button"
          data-testid="load-operations"
          :disabled="busy || !storeId"
          @click="loadOperations"
        >
          <SkiIcon name="refresh" />{{ busy ? '加载中…' : '刷新状态' }}
        </button>
      </div>
    </div>
    <p v-if="!error && !actor">
      正在加载账号权限…
    </p>
    <p
      v-if="actor && !grants.length"
      role="alert"
    >
      需要门店的{{ mode === 'manager' ? '经理授权' : '员工授权' }}。
    </p>
    <template v-if="grants.length">
      <div class="hero">
        <div class="hero-copy">
          <div class="hero-eyebrow">
            雪具租赁 · 运营管理
          </div><h2>雪季正好，准备出发。</h2><p>租赁、雪具与门店事务，一处掌握。</p><RouterLink
            class="primary"
            :to="mode === 'manager' ? '/equipment/available' : '/rental/desk'"
          >
            {{ mode === 'manager' ? '查看可租雪具' : '进入租赁办理' }}<SkiIcon name="arrow" />
          </RouterLink>
        </div><div class="hero-note">
          <SkiIcon />每一次出发，皆有准备
        </div>
      </div>
      <div
        v-if="mode === 'manager'"
        class="stats"
      >
        <article
          v-for="stat in stats"
          :key="stat.label"
          class="stat"
        >
          <div class="stat-title">
            {{ stat.label }}<span
              class="icon-box"
              :class="stat.icon"
            ><SkiIcon :name="stat.icon" /></span>
          </div><div class="stat-value">
            {{ state ? stat.value : '—' }}<small>{{ stat.unit }}</small>
          </div><div class="stat-foot">
            {{ state ? stat.foot : '选择门店并刷新状态' }}
          </div>
        </article>
      </div>
      <div
        class="columns"
        :class="{ 'employee-columns': mode === 'employee' }"
      >
        <section
          v-if="mode === 'manager'"
          class="panel operations-panel"
        >
          <div class="panel-head">
            <h2>门店运营概览</h2><span>{{ state ? `当前门店 · ${state.storeId}` : '尚未加载门店状态' }}</span>
          </div><table
            v-if="state"
            class="status-table"
          >
            <thead><tr><th>业务类型</th><th>运营状态</th><th>数量</th></tr></thead><tbody>
              <tr
                v-for="row in rows"
                :key="row.name"
              >
                <td><span class="module-cell"><SkiIcon :name="row.icon" />{{ row.name }}</span></td><td><span class="pill">{{ statusRows(row.counts) }}</span></td><td class="count-cell">
                  {{ total(row.counts) }}
                </td>
              </tr>
            </tbody>
          </table><p
            v-else
            class="panel-empty"
          >
            {{ busy ? '正在获取门店状态…' : '请选择授权门店并刷新。' }}
          </p>
        </section>
        <div>
          <section class="panel">
            <div class="panel-head">
              <h2>常用办理</h2><span>快捷进入</span>
            </div><nav
              class="quick-grid"
              aria-label="常用办理"
            >
              <RouterLink
                v-for="link in links"
                :key="link.to"
                class="quick-item"
                :to="link.to"
              >
                <span class="quick-icon"><SkiIcon :name="linkIcon(link.to)" /></span>{{ link.label }}
              </RouterLink>
            </nav>
          </section><RouterLink
            class="mini-card"
            to="/entry/miniprogram"
          >
            <SkiIcon name="phone" /><div><b>顾客微信小程序</b><p>查看顾客自助入口与小程序码</p></div><SkiIcon name="arrow" />
          </RouterLink>
        </div>
      </div>
      <details
        v-if="mode === 'manager'"
        class="danger-zone"
      >
        <summary>门店停用管理</summary><p>未结束的合同、调拨或未来班次会阻止门店停用。请选择授权门店并确认操作。</p><label><input
          v-model="confirmStop"
          type="checkbox"
          aria-label="确认停用门店"
        >确认停用所选门店 {{ storeId }}</label><button
          class="danger-button"
          data-testid="deactivate-store"
          :disabled="busy || !storeId || !confirmStop || stoppedStores.includes(storeId)"
          @click="stopStore"
        >
          停用门店
        </button><p
          v-if="notice"
          role="status"
        >
          {{ notice }}
        </p>
      </details>
    </template>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
  </section>
</template>

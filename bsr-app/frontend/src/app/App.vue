<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { currentActor, type CurrentActor } from '../features/identity/authApi'
import { featureMenuItems } from './routing/featureRegistry'
import { authorizedMenu } from './routing/authorizedNavigation'
import SkiIcon from '../shared/components/SkiIcon.vue'
import { uiText } from '../shared/presentation/zh'

const route = useRoute()
const actor = ref<CurrentActor | null>(null)
const menuOpen = ref(false)
const menuItems = computed(() => authorizedMenu(actor.value, featureMenuItems))
const isLogin = computed(() => route.fullPath.split('?')[0] === '/identity/login')
const pageTitle = computed(() => menuItems.value.find(item => item.to === route.path)?.label ?? '雪具租赁管理系统')
const groups = computed(() => {
  const items = menuItems.value
  const buckets = [
    { label: '运营概览', prefix: '/workbenches/', icon: 'grid' },
    { label: '租赁业务', prefix: '/rental/', icon: 'file' },
    { label: '顾客与需求', prefix: '/customer-requests/', icon: 'users' },
    { label: '雪具装备', prefix: '/equipment/', icon: 'ski' },
    { label: '维修管理', prefix: '/repair/', icon: 'wrench' },
    { label: '门店与调拨', prefix: '/storetransfer/', icon: 'transfer' },
    { label: '员工排班', prefix: '/scheduling/', icon: 'calendar' },
  ]
  return buckets.map(bucket => ({ ...bucket, items: items.filter(item => item.to.startsWith(bucket.prefix)) })).filter(bucket => bucket.items.length)
})
const systemItems = computed(() => menuItems.value.filter(item => ['identity-session','identity-role-grants','audit-events','miniprogram-entry'].includes(item.id)))
const roles = computed(() => [...new Set(actor.value?.grants.map(grant => uiText(grant.role)) ?? [])].join(' / '))
const today = new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
function iconFor(path: string) {
  if (path.includes('workbenches')) return 'grid'
  if (path.includes('equipment')) return 'ski'
  if (path.includes('repair')) return 'wrench'
  if (path.includes('scheduling')) return 'calendar'
  if (path.includes('storetransfer')) return 'transfer'
  if (path.includes('customer')) return 'users'
  if (path.includes('audit')) return 'clock'
  if (path.includes('miniprogram')) return 'phone'
  if (path.includes('identity')) return 'shield'
  return 'file'
}
let requestVersion = 0
watch(() => route.fullPath, async () => {
  menuOpen.value = false
  const currentVersion = ++requestVersion
  try {
    const current = await currentActor()
    if (currentVersion === requestVersion) actor.value = current
  } catch {
    if (currentVersion === requestVersion) actor.value = null
  }
}, { immediate: true })
</script>

<template>
  <div
    v-if="isLogin"
    class="login-shell"
  >
    <RouterView />
  </div>
  <div
    v-else
    class="app-shell"
  >
    <button
      v-if="menuOpen"
      class="nav-scrim"
      aria-label="关闭导航"
      @click="menuOpen = false"
    />
    <aside
      class="sidebar"
      :class="{ 'is-open': menuOpen }"
    >
      <RouterLink
        class="brand"
        to="/"
      >
        <SkiIcon class="brand-mark" /><span><b class="brand-name">BSR</b><span class="brand-sub">雪具租赁管理系统</span></span>
      </RouterLink>
      <nav
        aria-label="主导航"
        class="side-navigation"
      >
        <RouterLink
          v-if="!actor"
          class="nav-item"
          to="/identity/login"
        >
          <SkiIcon name="user" />登录
        </RouterLink>
        <p
          v-if="groups.length"
          class="nav-label"
        >
          雪场运营
        </p>
        <template
          v-for="group in groups"
          :key="group.label"
        >
          <RouterLink
            v-if="group.items.length === 1"
            class="nav-item"
            :to="group.items[0]!.to"
          >
            <SkiIcon :name="group.icon" /><span>{{ group.items[0]!.label }}</span>
          </RouterLink>
          <details
            v-else
            class="nav-group"
            :open="route.path?.startsWith(group.prefix)"
          >
            <summary
              class="nav-item"
              :class="{ 'group-active': route.path?.startsWith(group.prefix) }"
            >
              <SkiIcon :name="group.icon" /><span>{{ group.label }}</span><span class="nav-chevron">⌄</span>
            </summary>
            <RouterLink
              v-for="item in group.items"
              :key="item.id"
              class="nav-item nav-child"
              :to="item.to"
            >
              {{ item.label }}
            </RouterLink>
          </details>
        </template>
        <p
          v-if="systemItems.length"
          class="nav-label"
        >
          系统与账号
        </p>
        <RouterLink
          v-for="item in systemItems"
          :key="item.id"
          class="nav-item"
          :to="item.to"
        >
          <SkiIcon :name="iconFor(item.to)" /><span>{{ item.label }}</span>
        </RouterLink>
      </nav>
      <div class="sidebar-bottom">
        <div class="season-card">
          <SkiIcon name="snow" /><b>专注每一次雪上体验</b><p>从雪具准备，到安全归还<br>让服务更有条理</p>
        </div>
        <RouterLink
          class="side-account"
          :to="actor ? '/identity/session' : '/identity/login'"
        >
          <span class="avatar">{{ actor ? '雪' : '访' }}</span><div><b>{{ actor?.loginName ?? '欢迎来到 BSR' }}</b><span>{{ roles || '登录后开始办理' }}</span></div>
        </RouterLink>
      </div>
    </aside>
    <div class="layout">
      <header class="topbar">
        <div class="crumb">
          <button
            class="mobile-menu"
            aria-label="展开导航"
            :aria-expanded="menuOpen"
            @click="menuOpen = !menuOpen"
          >
            ☰
          </button><RouterLink to="/">
            工作台
          </RouterLink><span>/</span><b>{{ pageTitle }}</b>
        </div>
        <div class="top-actions">
          <span class="date">{{ today }}</span><RouterLink
            class="top-account"
            :to="actor ? '/identity/session' : '/identity/login'"
          >
            <span class="avatar">{{ actor ? '雪' : '访' }}</span><span>{{ actor ? '我的账号' : '登录' }}</span>
          </RouterLink>
        </div>
      </header>
      <main class="content">
        <RouterView /><footer class="footer">
          <span>BSR · 雪具租赁管理系统</span><span>租赁 · 维修 · 调拨 · 排班</span>
        </footer>
      </main>
    </div>
  </div>
</template>

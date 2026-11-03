<script setup lang="ts">
import { problemText } from '../../shared/presentation/zh'
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import SkiIcon from '../../shared/components/SkiIcon.vue'
import { ApiError } from '../../shared/http/apiClient'
import { login } from './authApi'

const router = useRouter()
const loginName = ref('')
const password = ref('')
const errorMessage = ref('')
const submitting = ref(false)

async function submit() {
  errorMessage.value = ''
  submitting.value = true
  try {
    const actor = await login(loginName.value, password.value)
    password.value = ''
    await router.push(actor.identityType === 'EMPLOYEE' && actor.grants.some(g => g.role === 'MANAGER' && g.storeId) ? '/workbenches/manager' : actor.identityType === 'EMPLOYEE' && actor.grants.some(g => g.role === 'EMPLOYEE' && g.storeId) ? '/workbenches/employee' : actor.identityType === 'CONTRACTOR' ? '/repair/contractor' : '/entry/miniprogram')
  } catch (error) {
    password.value = ''
    errorMessage.value = error instanceof ApiError
      ? problemText(error.problem)
      : '登录失败，请检查连接后重试。'
  } finally {
    submitting.value = false
  }
}
</script>
<template>
  <section class="login-page">
    <header class="login-top">
      <RouterLink
        class="brand"
        to="/"
      >
        <SkiIcon class="brand-mark" /><span><b class="brand-name">BSR</b><span class="brand-sub">雪具租赁管理系统</span></span>
      </RouterLink><RouterLink to="/entry/miniprogram">
        顾客微信小程序 ↗
      </RouterLink>
    </header>
    <div class="login-body">
      <div class="login-message">
        <div class="line" /><h1>奔赴雪场，<br>把管理留给 BSR。</h1><p>从一副雪具到一次出发，<br>让每个环节，都井然有序。</p><div class="login-tags">
          <span>雪具租赁</span><span>维修管理</span><span>门店调拨</span><span>员工排班</span>
        </div>
      </div>
      <div class="login-card">
        <SkiIcon class="small-mountain" /><h2>欢迎回来</h2><p class="subtitle">
          登录 BSR 雪具租赁管理系统
        </p>
        <form @submit.prevent="submit">
          <label class="field-label">账号<input
            v-model="loginName"
            autocomplete="username"
            required
            maxlength="100"
            placeholder="请输入账号"
            :disabled="submitting"
          ></label>
          <label class="field-label">密码<input
            v-model="password"
            type="password"
            autocomplete="current-password"
            required
            maxlength="255"
            placeholder="请输入密码"
            :disabled="submitting"
          ></label>
          <p
            v-if="errorMessage"
            role="alert"
          >
            {{ errorMessage }}
          </p>
          <button
            class="primary"
            type="submit"
            :disabled="submitting"
          >
            {{ submitting ? '登录中…' : '登录工作台' }}<SkiIcon name="arrow" />
          </button>
        </form>
        <p class="login-help">
          请使用已分配的员工、经理或维修承包商账号登录
        </p>
        <RouterLink
          class="login-mini"
          to="/entry/miniprogram"
        >
          <SkiIcon name="phone" />顾客请使用微信小程序<SkiIcon name="arrow" />
        </RouterLink>
      </div>
    </div>
    <footer class="login-footer">
      <span>BSR · 雪具租赁管理系统</span><span>雪季的每一次出发，都值得用心准备。</span>
    </footer>
  </section>
</template>

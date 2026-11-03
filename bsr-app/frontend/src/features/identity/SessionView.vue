<script setup lang="ts">
import { uiText } from '../../shared/presentation/zh'
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { currentActor, logout, type CurrentActor } from './authApi'

const router = useRouter()
const actor = ref<CurrentActor | null>(null)
const errorMessage = ref('')

onMounted(async () => {
  try { actor.value = await currentActor() } catch { await router.replace('/identity/login') }
})

async function signOut() {
  errorMessage.value = ''
  try {
    await logout()
    await router.replace('/identity/login')
  } catch {
    errorMessage.value = '退出登录失败，请重试。'
  }
}
</script>

<template>
  <section>
    <p class="eyebrow">
      当前身份
    </p>
    <h1>账号信息</h1>
    <div
      v-if="actor"
      class="account-actions"
    >
      <RouterLink
        v-if="actor.identityType === 'EMPLOYEE' && actor.grants.some(g => g.role === 'MANAGER' && g.storeId)"
        to="/workbenches/manager"
      >
        进入雪场工作台 →
      </RouterLink><RouterLink
        v-if="actor.identityType === 'EMPLOYEE' && actor.grants.some(g => g.role === 'EMPLOYEE' && g.storeId)"
        to="/workbenches/employee"
      >
        进入员工工作台 →
      </RouterLink><RouterLink
        v-if="actor.identityType === 'CONTRACTOR'"
        to="/repair/contractor"
      >
        进入我的维修任务 →
      </RouterLink>
    </div><dl v-if="actor">
      <dt>登录账号</dt><dd>{{ actor.loginName }}</dd>
      <dt>业务身份</dt><dd>{{ uiText(actor.identityType) }} · {{ actor.identityId }}</dd>
      <dt>有效授权</dt><dd>{{ actor.grants.map((grant) => `${uiText(grant.role)}${grant.storeId ? ` · 门店 ${grant.storeId}` : ''}`).join(', ') }}</dd>
    </dl>
    <p
      v-if="errorMessage"
      role="alert"
    >
      {{ errorMessage }}
    </p>
    <button
      type="button"
      @click="signOut"
    >
      退出登录
    </button>
  </section>
</template>

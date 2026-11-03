<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { currentActor } from '../features/identity/authApi'
const router = useRouter()
onMounted(async () => {
  try {
    const actor = await currentActor()
    const target = actor.identityType === 'EMPLOYEE' && actor.grants.some(g => g.role === 'MANAGER' && g.storeId) ? '/workbenches/manager' : actor.identityType === 'EMPLOYEE' && actor.grants.some(g => g.role === 'EMPLOYEE' && g.storeId) ? '/workbenches/employee' : actor.identityType === 'CONTRACTOR' ? '/repair/contractor' : '/entry/miniprogram'
    await router.replace(target)
  } catch { await router.replace('/identity/login') }
})
</script>
<template>
  <section class="home-page">
    <div class="hero">
      <div class="hero-copy">
        <div class="hero-eyebrow">
          BSR · 雪具租赁管理系统
        </div><h2>雪季正好，准备出发。</h2><p>正在为您打开工作台…</p><div class="home-actions">
          <RouterLink
            class="primary"
            to="/identity/login"
          >
            登录工作台 →
          </RouterLink><RouterLink
            class="primary"
            to="/entry/miniprogram"
          >
            顾客微信小程序 →
          </RouterLink>
        </div>
      </div>
    </div>
  </section>
</template>

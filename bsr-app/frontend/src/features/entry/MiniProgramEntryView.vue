<script setup lang="ts">
import trialCode from './bsr-wechat-trial-20261128.png'

const configured = import.meta.env.VITE_MINIPROGRAM_QR_IMAGE?.trim()
const trialExpiresAt = Date.parse('2026-11-28T00:00:00+08:00')
const image = configured === undefined
  ? (Date.now() < trialExpiresAt ? trialCode : '')
  : ((/^\/(?!\/)/.test(configured) || /^https:\/\//.test(configured)) ? configured : '')
const showTrialCode = image === trialCode
</script>

<template>
  <section class="customer-entry">
    <h1>顾客微信小程序</h1>
    <p v-if="configured === undefined && !image">
      本期体验码已到期，新码暂未配置。请联系演示人获取最新体验码。
    </p>
    <p v-else-if="!image">
      顾客小程序码暂未配置，请向门店获取官方微信小程序码。
    </p>
    <div
      v-else
      class="scan-panel"
    >
      <img
        :src="image"
        :alt="showTrialCode ? 'BSR 滑雪装备管理体验版二维码' : '官方顾客小程序码'"
      >
      <div v-if="showTrialCode">
        <h2>扫码体验装备租赁</h2>
        <p class="deadline">
          此体验码在 11 月 28 日前有效。
        </p>
        <ol>
          <li>使用已添加为体验成员的微信号扫码。</li>
          <li>域名仍在提交审核阶段。点右上角「…」→「打开调试」，按提示重启。若菜单显示「关闭调试」，可直接登录。</li>
          <li>用顾客账号登录，进入「租赁申请」，选择门店、装备和租期。提交后可在申请详情查看进度。</li>
        </ol>
      </div>
      <p v-else>
        请用微信扫描小程序码，进入顾客端。
      </p>
    </div>
    <a href="/identity/login">进入员工、经理或维修承包商工作台 →</a>
  </section>
</template>

<style scoped>
.customer-entry { max-width: 52rem; margin-inline: auto; }
.scan-panel { display: grid; grid-template-columns: minmax(12rem, 16rem) 1fr; align-items: center; gap: clamp(1rem, 4vw, 2.5rem); margin-block: 1.25rem; padding: clamp(1rem, 3vw, 2rem); border: 1px solid #cbdde5; border-radius: 1rem; background: white; }
.scan-panel img { width: 100%; height: auto; justify-self: center; max-width: 16rem; }
.scan-panel h2 { margin: 0; color: #173d50; }
.deadline { color: #315c6b; }
.scan-panel ol { padding-inline-start: 1.4rem; line-height: 1.65; }
.scan-panel li + li { margin-top: .55rem; }
.customer-entry a { color: #155470; text-underline-offset: .2em; }
@media (max-width: 640px) { .scan-panel { grid-template-columns: 1fr; } }
</style>

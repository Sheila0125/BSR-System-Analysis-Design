<script setup lang="ts">
import trialCode from './bsr-wechat-trial-20261123.png'

const configured = import.meta.env.VITE_MINIPROGRAM_QR_IMAGE?.trim()
const trialExpiresAt = Date.parse('2026-11-23T00:00:00+08:00')
const image = configured === undefined
  ? (Date.now() < trialExpiresAt ? trialCode : '')
  : ((/^\/(?!\/)/.test(configured) || /^https:\/\//.test(configured)) ? configured : '')
const showTrialCode = image === trialCode
</script>

<template>
  <section>
    <h1>顾客微信小程序</h1>
    <p v-if="!image">
      顾客小程序码暂未配置，请向门店获取官方微信小程序码。
    </p>
    <template v-else>
      <p v-if="showTrialCode">请用已添加为体验成员的微信扫码。此体验码在 11 月 23 日前有效。</p>
      <p v-else>请用微信扫描下方小程序码，进入顾客端。</p>
      <img
        :src="image"
        :alt="showTrialCode ? 'BSR 滑雪装备管理体验版二维码' : '官方顾客小程序码'"
      >
    </template>
  </section>
</template>

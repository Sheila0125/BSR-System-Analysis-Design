<script setup lang="ts">
const configured = import.meta.env.VITE_MINIPROGRAM_QR_IMAGE?.trim() ?? ''
const image = (/^\/(?!\/)/.test(configured) || /^https:\/\//.test(configured)) ? configured : ''
</script>

<template>
  <section>
    <h1>顾客微信小程序</h1>
    <p v-if="!image">
      顾客小程序码暂未配置，请向门店获取官方微信小程序码。
    </p>
    <template v-else>
      <p>请用微信扫描下方小程序码，进入顾客端。</p>
      <img
        :src="image"
        alt="官方顾客小程序码"
      >
    </template>
  </section>
</template>

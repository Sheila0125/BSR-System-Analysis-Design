<script setup lang="ts">
import { errorText } from '../../shared/presentation/zh'
import { uiText } from '../../shared/presentation/zh'
import { ref } from 'vue'
import { storeTransferApi, type Store, type Transfer, type TransferType } from './storeTransferApi'

const sourceStoreId = ref('')
const destinationStoreId = ref('')
const transferType = ref<TransferType>('TEMPORARY')
const equipmentIds = ref('')
const transfers = ref<Transfer[]>([])
const authorityStoreId = ref('')
const managedStoreId = ref('')
const storeNumber = ref('')
const storeName = ref('')
const address = ref('')
const openingTime = ref('')
const closingTime = ref('')
const store = ref<Store | null>(null)
const busy = ref(false)
const error = ref('')
const notice = ref('')

async function run(operation: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try { await operation() } catch (failure) { error.value = failure instanceof Error ? errorText(failure) : '管理操作失败，已保留您的输入。' }
  finally { busy.value = false }
}
function loadTransfers() { return run(async () => { transfers.value = await storeTransferApi.listTransfers(sourceStoreId.value) }) }
function createTransfer() { return run(async () => {
  const ids = equipmentIds.value.split(/[\s,]+/).filter(Boolean)
  const created = await storeTransferApi.createTransfer(sourceStoreId.value, destinationStoreId.value, transferType.value, ids)
  transfers.value = [created, ...transfers.value.filter(transfer => transfer.transferId !== created.transferId)]
  notice.value = `调拨 ${created.transferNumber} 已发起，请另行批准。`
}) }
function approve(transferId: string) { return run(async () => {
  const approved = await storeTransferApi.approve(transferId)
  transfers.value = transfers.value.map(transfer => transfer.transferId === transferId ? approved : transfer)
  notice.value = `调拨 ${approved.transferNumber} 已批准。`
}) }
function loadStore() { return run(async () => {
  store.value = await storeTransferApi.getStore(managedStoreId.value)
  storeNumber.value = store.value.storeNumber
  storeName.value = store.value.storeName
  address.value = store.value.address ?? ''
  openingTime.value = store.value.openingTime
  closingTime.value = store.value.closingTime
}) }
function createStore() { return run(async () => {
  store.value = await storeTransferApi.createStore(authorityStoreId.value, storeNumber.value, storeName.value,
    address.value || null, openingTime.value, closingTime.value)
  notice.value = `门店 ${store.value.storeNumber} 已创建，未自动授予经理权限。`
}) }
function updateStore() { return run(async () => {
  store.value = await storeTransferApi.updateStore(managedStoreId.value, storeName.value,
    address.value || null, openingTime.value, closingTime.value)
  notice.value = `门店 ${store.value.storeNumber} 已更新。`
}) }
</script>

<template>
  <section>
    <h1>门店与调拨</h1>
    <p>调出与调入门店均需经理授权，发起与批准为两个独立操作。</p>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-if="notice"
      role="status"
    >
      {{ notice }}
    </p>
    <section>
      <h2>调拨记录</h2>
      <label>调出门店编号<input
        v-model.trim="sourceStoreId"
        aria-label="调出门店编号"
      ></label>
      <label>调入门店编号<input
        v-model.trim="destinationStoreId"
        aria-label="调入门店编号"
      ></label>
      <label>调拨类型<select
        v-model="transferType"
        aria-label="调拨类型"
      ><option value="TEMPORARY">临时调拨</option><option value="PERMANENT">永久调拨</option></select></label>
      <label>雪具编号<input
        v-model="equipmentIds"
        aria-label="雪具编号"
        placeholder="雪具编号（用英文逗号分隔）"
      ></label>
      <button
        data-testid="create-transfer"
        :disabled="busy || !sourceStoreId || !destinationStoreId || !equipmentIds.trim()"
        @click="createTransfer"
      >
        发起调拨
      </button>
      <button
        data-testid="load-transfers"
        :disabled="busy || !sourceStoreId"
        @click="loadTransfers"
      >
        查询调拨
      </button>
      <ul>
        <li
          v-for="transfer in transfers"
          :key="transfer.transferId"
        >
          {{ transfer.transferNumber }} · {{ uiText(transfer.status) }} · {{ transfer.items?.length ?? 0 }} 件
          <button
            v-if="transfer.status === 'INITIATED'"
            :data-testid="`approve-${transfer.transferId}`"
            :disabled="busy"
            @click="approve(transfer.transferId)"
          >
            批准
          </button>
        </li>
      </ul>
    </section>
    <section>
      <h2>门店详情</h2>
      <label>授权门店编号<input
        v-model.trim="authorityStoreId"
        aria-label="授权门店编号"
      ></label>
      <label>管理门店编号<input
        v-model.trim="managedStoreId"
        aria-label="管理门店编号"
      ></label>
      <button
        data-testid="load-store"
        :disabled="busy || !managedStoreId"
        @click="loadStore"
      >
        查询门店
      </button>
      <label>门店编号<input
        v-model.trim="storeNumber"
        aria-label="门店编号"
      ></label>
      <label>门店名称<input
        v-model.trim="storeName"
        aria-label="门店名称"
      ></label>
      <label>地址<input
        v-model.trim="address"
        aria-label="地址"
      ></label>
      <label>营业开始时间<input
        v-model="openingTime"
        aria-label="营业开始时间"
        type="time"
      ></label>
      <label>营业结束时间<input
        v-model="closingTime"
        aria-label="营业结束时间"
        type="time"
      ></label>
      <button
        data-testid="create-store"
        :disabled="busy || !authorityStoreId || !storeNumber || !storeName"
        @click="createStore"
      >
        创建门店
      </button>
      <button
        data-testid="update-store"
        :disabled="busy || !managedStoreId || !storeName"
        @click="updateStore"
      >
        更新门店
      </button>
      <p v-if="store">
        {{ store.storeNumber }} · {{ uiText(store.businessStatus) }}
      </p>
    </section>
  </section>
</template>

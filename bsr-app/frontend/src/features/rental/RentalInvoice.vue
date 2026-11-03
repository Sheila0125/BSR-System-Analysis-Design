<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { errorText, uiText } from '../../shared/presentation/zh'
import { rentalApi, type RentalInvoice } from './rentalApi'

const props=withDefaults(defineProps<{contractId:string; version?:number}>(),{version:0})
const invoice=ref<RentalInvoice | null>(null)
const loading=ref(false)
const error=ref('')
let generation=0
async function refresh() {
  const current=++generation
  invoice.value=null; loading.value=true; error.value=''
  try { const value=await rentalApi.invoice(props.contractId); if(current===generation) invoice.value=value }
  catch(failure) { if(current===generation) error.value=failure instanceof Error ? errorText(failure) : '无法读取发票，请重新登录或稍后重试。' }
  finally { if(current===generation) loading.value=false }
}
function time(value:string | null) { return value ? new Date(value).toLocaleString('zh-CN',{hour12:false}) : '待确认' }
watch(()=>[props.contractId,props.version],()=>void refresh(),{immediate:true})
onBeforeUnmount(()=>{generation++})
</script>
<template>
  <section
    class="rental-invoice"
    aria-label="电子发票"
  >
    <div class="invoice-toolbar">
      <div><h3>电子发票</h3><p>课程演示电子账单 · 模拟付款不扣款</p></div>
      <button
        type="button"
        :disabled="loading"
        @click="refresh"
      >
        {{ loading ? '正在读取…' : '刷新发票' }}
      </button>
    </div>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <template v-if="invoice">
      <div class="invoice-identity">
        <div>
          <span class="invoice-brand">BSR</span><p class="invoice-number">
            {{ invoice.invoiceNumber }}
          </p>
        </div>
        <span class="stage">{{ uiText(invoice.contractStatus) }}</span>
      </div>
      <dl class="invoice-metadata">
        <div><dt>顾客</dt><dd>{{ invoice.customerName }} · {{ invoice.customerNumber }}</dd></div>
        <div><dt>服务门店</dt><dd>{{ invoice.storeName }}</dd></div>
        <div><dt>合同编号</dt><dd>{{ invoice.contractNumber }}</dd></div>
        <div><dt>账单日期</dt><dd>{{ invoice.contractDate }}</dd></div>
        <div><dt>约定租期</dt><dd>{{ invoice.rentalStartDate }} 起 · {{ invoice.rentalDays }} 天</dd></div>
      </dl>
      <div class="invoice-balances">
        <div data-testid="invoice-total">
          <span>合同应付合计</span><strong>{{ invoice.amountDue }}</strong><small>{{ invoice.currency }}</small>
        </div>
        <div data-testid="invoice-paid">
          <span>成功付款合计</span><strong>{{ invoice.totalPaid }}</strong><small>{{ invoice.currency }}</small>
        </div>
        <div
          class="balance-due"
          data-testid="invoice-outstanding"
        >
          <span>待支付</span><strong>{{ invoice.outstandingPayment }}</strong><small>{{ invoice.currency }}</small>
        </div>
        <div data-testid="invoice-refund">
          <span>待退款</span><strong>{{ invoice.outstandingRefund }}</strong><small>{{ invoice.currency }}</small>
        </div>
      </div>
      <p class="invoice-note">
        已成功退款 {{ invoice.totalRefunded }} {{ invoice.currency }}。失败流水不计入付款或退款合计。
      </p>
      <h4>费用明细与付款凭证</h4>
      <p v-if="!invoice.charges.length">
        本合同没有需支付的费用。
      </p>
      <div
        v-for="charge in invoice.charges"
        :key="charge.chargeId"
        class="invoice-charge"
      >
        <div class="charge-heading">
          <strong>{{ uiText(charge.type) }} · {{ charge.amount }} {{ invoice.currency }}</strong><span class="stage">{{ uiText(charge.status) }}</span>
        </div>
        <p>{{ charge.type === 'REFUND' ? '尚待退款' : '尚待支付' }} {{ charge.outstandingAmount }} {{ invoice.currency }}</p>
        <ul class="invoice-transactions">
          <li
            v-for="transaction in charge.transactions"
            :key="transaction.transactionId"
          >
            <div class="transaction-heading">
              <strong>{{ transaction.source === 'SIMULATED' ? '顾客模拟付款 · 不扣款' : '外部交易 · 员工登记' }}</strong><span>{{ uiText(transaction.status) }} · {{ transaction.amount }} {{ invoice.currency }}</span>
            </div>
            <span class="transaction-id">{{ transaction.externalId }}</span>
            <small>处理时间（本地） {{ time(transaction.processedAt || transaction.recordedAt) }}</small>
          </li>
        </ul>
      </div>
      <h4>雪具与价格快照</h4>
      <ul class="invoice-items">
        <li
          v-for="item in invoice.items"
          :key="item.itemId"
        >
          <span>装备 {{ item.equipmentId }}</span><span>{{ item.dailyRateSnapshot }} {{ invoice.currency }} / 天 · {{ uiText(item.status) }}</span>
        </li>
      </ul>
      <p class="invoice-note">
        初始租金按逐件日租金快照 × 约定整日天数计算；已确认调整的补缴和退款另列费用。本页为课程演示凭证，不属于税务发票。
      </p>
    </template>
  </section>
</template>
<style scoped>
.rental-invoice { margin-block:1.2rem; padding:clamp(1rem,3vw,2rem); background:#fff; border:1px solid #dce7ee; border-top:5px solid #164c6c; border-radius:14px; color:#224358; max-width:100%; overflow-wrap:anywhere; }
.invoice-toolbar,.invoice-identity,.charge-heading,.transaction-heading { display:flex; justify-content:space-between; gap:1rem; align-items:flex-start; }
.invoice-toolbar h3 { margin:0; font-size:1.35rem; }
.invoice-toolbar p,.invoice-note { color:#647e8e; font-size:.85rem; line-height:1.7; }
.invoice-toolbar button { flex-shrink:0; padding:.55rem .9rem; border:1px solid #c9dce7; border-radius:8px; background:#f3f8fb; color:#235777; cursor:pointer; }
.invoice-identity { margin-top:1.8rem; padding-bottom:1rem; border-bottom:1px solid #e2ecf3; }
.invoice-brand { font-size:2.3rem; font-weight:750; letter-spacing:.3rem; color:#164c6c; }
.invoice-number { margin:.5rem 0 0; font-size:.85rem; }
.stage { display:inline-block; padding:.25rem .6rem; font-size:.8rem; border-radius:6px; background:#eaf3f8; color:#35647c; flex-shrink:0; }
.invoice-metadata { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:1rem 2rem; margin-block:1.4rem; }
.invoice-metadata div { min-width:0; }
dt { color:#7892a2; font-size:.8rem; }
dd { margin:.3rem 0 0; font-size:.95rem; }
.invoice-balances { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:.8rem; }
.invoice-balances > div { padding:1rem; border:1px solid #dfeaf1; border-radius:12px; min-width:0; }
.invoice-balances span,.invoice-balances small { display:block; font-size:.8rem; opacity:.75; }
.invoice-balances strong { display:block; margin:.4rem 0; font-size:clamp(1.3rem,3vw,1.8rem); line-height:1.3; }
.invoice-balances .balance-due { background:#164c6c; border-color:#164c6c; color:#fff; }
h4 { margin:1.8rem 0 .8rem; }
.invoice-charge { padding:1rem 0; border-top:1px solid #e4edf3; }
.invoice-charge > p { color:#708998; font-size:.85rem; }
.invoice-transactions,.invoice-items { margin:0; padding:0; list-style:none; }
.invoice-transactions li { background:#f3f7fa; border-radius:9px; padding:1rem; margin-top:.65rem; }
.transaction-heading { font-size:.85rem; flex-wrap:wrap; }
.transaction-id,.invoice-transactions small { display:block; margin-top:.45rem; color:#6a8495; font-size:.78rem; }
.invoice-items li { display:flex; justify-content:space-between; flex-wrap:wrap; gap:.5rem 1rem; padding:.8rem 0; border-bottom:1px solid #edf3f7; font-size:.9rem; }
[role="alert"] { padding:.8rem; color:#9b1c1c; background:#fff3f0; border-radius:8px; }
@media (max-width:600px) { .invoice-balances { grid-template-columns:repeat(2,minmax(0,1fr)); } .invoice-metadata { grid-template-columns:minmax(0,1fr); } .invoice-toolbar { flex-wrap:wrap; } }
</style>

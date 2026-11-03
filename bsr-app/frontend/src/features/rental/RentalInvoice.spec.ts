import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import RentalInvoice from './RentalInvoice.vue'

const api=vi.hoisted(()=>({invoice:vi.fn()}))
vi.mock('./rentalApi',()=>({rentalApi:api}))
const invoice=()=>({invoiceNumber:'INV-RC-7',documentType:'COURSE_DEMO_INVOICE',currency:'NOK',contractId:'7',contractNumber:'RC-7',customerId:'2',customerName:'测试顾客',customerNumber:'C-2',serviceStoreId:'3',storeName:'雪场门店',contractDate:'2026-10-27',rentalStartDate:'2026-12-20',rentalDays:3,contractStatus:'PENDING_PAYMENT',amountDue:'418.50',totalPaid:'100.00',totalRefunded:'0.00',outstandingPayment:'318.50',outstandingRefund:'0.00',items:[{itemId:'8',equipmentId:'9007199254740993',dailyRateSnapshot:'99.50',status:'PENDING_ISSUE',active:true}],charges:[{chargeId:'11',type:'INITIAL',amount:'418.50',status:'PENDING',outstandingAmount:'318.50',canSimulatePayment:true,transactions:[{transactionId:'21',chargeId:'11',type:'PAYMENT',externalId:'SIM-123',amount:'100.00',status:'SUCCEEDED',processedAt:'2026-10-27T08:00:00Z',recordedAt:'2026-10-27T08:00:00Z',recordedByEmployeeId:null,source:'SIMULATED',simulatedByAccountId:'2'}]}]})
beforeEach(()=>vi.resetAllMocks())
it('shows invoice identity, exact independent balances and clearly marked customer simulation',async()=>{
  api.invoice.mockResolvedValue(invoice()); const wrapper=mount(RentalInvoice,{props:{contractId:'7'}}); await flushPromises()
  expect(wrapper.text()).toContain('INV-RC-7'); expect(wrapper.text()).toContain('测试顾客'); expect(wrapper.text()).toContain('雪场门店')
  expect(wrapper.get('[data-testid="invoice-total"]').text()).toContain('418.50')
  expect(wrapper.get('[data-testid="invoice-paid"]').text()).toContain('100.00')
  expect(wrapper.get('[data-testid="invoice-outstanding"]').text()).toContain('318.50')
  expect(wrapper.text()).toContain('9007199254740993'); expect(wrapper.text()).toContain('顾客模拟付款')
  expect(wrapper.text()).toContain('不扣款'); expect(wrapper.text()).toContain('课程演示')
})
it('switching contracts clears prior data and ignores an old response that arrives late',async()=>{
  let old:(value:unknown)=>void=()=>undefined
  api.invoice.mockImplementationOnce(()=>new Promise(resolve=>{old=resolve})).mockResolvedValueOnce({...invoice(),invoiceNumber:'INV-RC-8',contractId:'8'})
  const wrapper=mount(RentalInvoice,{props:{contractId:'7'}})
  await wrapper.setProps({contractId:'8'}); await flushPromises(); old(invoice()); await flushPromises()
  expect(wrapper.text()).toContain('INV-RC-8'); expect(wrapper.text()).not.toContain('INV-RC-7')
})
it('refresh failure removes old financial details and presents the error',async()=>{
  api.invoice.mockResolvedValueOnce(invoice()); const wrapper=mount(RentalInvoice,{props:{contractId:'7'}}); await flushPromises()
  api.invoice.mockRejectedValueOnce(new Error('登录已失效'))
  await wrapper.get('button').trigger('click'); await flushPromises()
  expect(wrapper.find('[data-testid="invoice-total"]').exists()).toBe(false)
  expect(wrapper.get('[role="alert"]').text()).toContain('登录已失效')
})

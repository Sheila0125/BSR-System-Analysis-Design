import { afterEach, beforeEach, expect, it, vi } from 'vitest';

const api=vi.hoisted(()=>({ invoice:vi.fn(), simulate:vi.fn() }));
const navigate=vi.hoisted(()=>vi.fn());
vi.mock('../../pages/rental/configuredApi',()=>({ configuredRentalApi:()=>api }));
type Harness={
  data:Record<string,unknown>; setData(patch:Record<string,unknown>):void;
  onLoad(options:Record<string,string>):void; onShow():Promise<void>;
  openPayment(event:{currentTarget:{dataset:{id:string}}}):void;
  cancelPayment():void; pay():Promise<void>; viewInvoice():void;
};
let page:Harness;
const invoice=()=>({ invoiceNumber:'INV-RC-7',documentType:'COURSE_DEMO_INVOICE',currency:'NOK',contractId:'7',contractNumber:'RC-7',customerId:'2',customerName:'测试顾客',customerNumber:'C-2',serviceStoreId:'3',storeName:'雪场门店',contractDate:'2026-10-27',rentalStartDate:'2026-12-20',rentalDays:3,contractStatus:'PENDING_PAYMENT',amountDue:'418.50',totalPaid:'0.00',totalRefunded:'0.00',outstandingPayment:'418.50',outstandingRefund:'0.00',items:[],charges:[{chargeId:'11',type:'INITIAL',amount:'418.50',status:'PENDING',outstandingAmount:'418.50',canSimulatePayment:true,transactions:[]}] });
async function load() { const name='invoice'; await import(`../../pages/rental/${name}.ts`); page.onLoad({id:'7'}); await page.onShow(); }
beforeEach(()=>{
  vi.resetModules(); vi.resetAllMocks();
  vi.stubGlobal('Page',(options:Harness)=>{ page={...options,data:structuredClone(options.data),setData(patch){Object.assign(this.data,patch);}}; });
  vi.stubGlobal('wx',{navigateTo:navigate});
  api.invoice.mockResolvedValue(invoice());
});
afterEach(()=>vi.unstubAllGlobals());

it('opens the dedicated invoice for the currently viewed contract',async()=>{
  const name='detail'; await import(`../../pages/rental/${name}.ts`); page.onLoad({id:'9007199254740993'});
  expect(typeof page.viewInvoice).toBe('function'); page.viewInvoice();
  expect(navigate).toHaveBeenCalledWith({url:'/pages/rental/invoice?id=9007199254740993'});
});
it('loads server balances and cancels the virtual cashier without creating a transaction',async()=>{
  await load(); expect(page.data['invoice']).toEqual(invoice());
  page.openPayment({currentTarget:{dataset:{id:'11'}}});
  expect((page.data['selectedCharge'] as {outstandingAmount:string}).outstandingAmount).toBe('418.50');
  page.cancelPayment(); expect(page.data['selectedCharge']).toBeNull(); expect(api.simulate).not.toHaveBeenCalled();
});
it('suppresses double taps and reuses an uncertain request key before refreshing a successful invoice',async()=>{
  await load(); page.openPayment({currentTarget:{dataset:{id:'11'}}});
  let reject:(error:unknown)=>void=()=>undefined;
  api.simulate.mockImplementationOnce(()=>new Promise((_resolve,failed)=>{reject=failed;}));
  const pending=page.pay(); await page.pay(); expect(api.simulate).toHaveBeenCalledTimes(1);
  const first=api.simulate.mock.calls[0]; reject(new Error('Network response lost')); await pending;
  expect(page.data['attemptKey']).toBe(first?.[1]);
  api.simulate.mockResolvedValueOnce({status:'SUCCEEDED',amount:'418.50',source:'SIMULATED'});
  api.invoice.mockResolvedValueOnce({...invoice(),totalPaid:'418.50',outstandingPayment:'0.00',charges:[]});
  await page.pay(); expect(api.simulate.mock.calls[1]).toEqual(first);
  expect((page.data['invoice'] as {outstandingPayment:string}).outstandingPayment).toBe('0.00');
  expect(page.data['attemptKey']).toBe(''); expect(page.data['selectedCharge']).toBeNull();
  expect(page.data['paymentMessage']).toContain('成功');
});
it('a definitive failed simulation can start a new attempt without counting it as paid',async()=>{
  await load(); page.openPayment({currentTarget:{dataset:{id:'11'}}});
  page.setData({outcome:'FAILED'}); api.simulate.mockResolvedValueOnce({status:'FAILED',amount:'418.50',source:'SIMULATED'});
  await page.pay(); expect(page.data['paymentMessage']).toContain('失败');
  expect((page.data['invoice'] as {totalPaid:string}).totalPaid).toBe('0.00');
  page.openPayment({currentTarget:{dataset:{id:'11'}}}); api.simulate.mockResolvedValueOnce({status:'SUCCEEDED',amount:'418.50',source:'SIMULATED'});
  api.invoice.mockResolvedValueOnce({...invoice(),totalPaid:'418.50',outstandingPayment:'0.00',charges:[]});
  await page.pay(); expect(api.simulate.mock.calls[1]?.[1]).not.toBe(api.simulate.mock.calls[0]?.[1]);
});
it('an expired or forbidden payment clears invoice and retry data',async()=>{
  await load(); page.openPayment({currentTarget:{dataset:{id:'11'}}});
  api.simulate.mockRejectedValueOnce({status:403,detail:'Access revoked'}); await page.pay();
  expect(page.data['invoice']).toBeNull(); expect(page.data['selectedCharge']).toBeNull();
  expect(page.data['attemptKey']).toBe(''); expect(page.data['error']).toBe('Access revoked');
});

import { rentalAccessLost, rentalProblem, type RentalInvoice, type InvoiceCharge } from './rentalApi';
import { configuredRentalApi } from './configuredApi';

// A request identifier, never an authentication token. The server also serializes the charge balance.
function requestKey(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, char => {
    const value=Math.floor(Math.random()*16);
    return (char==='x' ? value : (value & 3) | 8).toString(16);
  });
}
Page({
  data: { id:'', invoice:null as RentalInvoice | null, selectedCharge:null as InvoiceCharge | null,
    outcome:'SUCCEEDED' as 'SUCCEEDED' | 'FAILED', attemptKey:'', loading:false, paying:false, error:'', paymentMessage:'' },
  onLoad(options:Record<string,string | undefined>) { this.setData({id:options['id'] ?? ''}); },
  onShow() { return this.refreshInvoice(); },
  async refreshInvoice() {
    if(this.data.loading || this.data.paying) return;
    this.setData({loading:true,invoice:null,error:''});
    try {
      const invoice=await configuredRentalApi().invoice(this.data.id);
      const selected=invoice.charges.find(c=>c.chargeId===this.data.selectedCharge?.chargeId && c.canSimulatePayment) ?? null;
      this.setData({invoice,selectedCharge:selected,...(!selected ? {attemptKey:''} : {})});
    } catch(error) {
      this.setData({error:rentalProblem(error),...(rentalAccessLost(error) ? {invoice:null,selectedCharge:null,attemptKey:''} : {})});
    } finally { this.setData({loading:false}); }
  },
  openPayment(event:{currentTarget:{dataset:{id?:string}}}) {
    if(this.data.loading || this.data.paying) return;
    const charge=this.data.invoice?.charges.find(c=>c.chargeId===event.currentTarget.dataset.id && c.canSimulatePayment);
    if(!charge) return;
    if(this.data.attemptKey && this.data.selectedCharge?.chargeId!==charge.chargeId) {
      this.setData({error:'上一笔付款结果尚未确认，请先重试或刷新账单。'}); return;
    }
    this.setData({selectedCharge:charge,error:'',paymentMessage:'',...(!this.data.attemptKey ? {outcome:'SUCCEEDED' as const} : {})});
  },
  chooseOutcome(event:{detail:{value:string}}) {
    if(this.data.paying || this.data.attemptKey) return;
    if(event.detail.value==='SUCCEEDED' || event.detail.value==='FAILED') this.setData({outcome:event.detail.value});
  },
  cancelPayment() {
    if(this.data.paying) return;
    if(this.data.attemptKey) { this.setData({error:'付款结果尚未确认，请先刷新账单或按原请求重试。'}); return; }
    this.setData({selectedCharge:null,error:''});
  },
  async pay() {
    const charge=this.data.selectedCharge;
    if(this.data.loading || this.data.paying || !charge?.canSimulatePayment) return;
    const key=this.data.attemptKey || requestKey();
    this.setData({paying:true,attemptKey:key,error:'',paymentMessage:''});
    try {
      const result=await configuredRentalApi().simulate(charge.chargeId,key,this.data.outcome);
      this.setData({attemptKey:'',selectedCharge:null,invoice:null,paymentMessage:result.status==='SUCCEEDED'
        ? '模拟付款成功，结果已保存。本次不扣款，可由门店继续办理发放。'
        : '本次模拟付款失败，未计入已付款，可以重新尝试。'});
      this.setData({invoice:await configuredRentalApi().invoice(this.data.id)});
    } catch(error) {
      const status=(error as {status?:number})?.status;
      this.setData({error:rentalProblem(error)});
      if(rentalAccessLost(error)) this.setData({invoice:null,selectedCharge:null,attemptKey:''});
      else if(status && status>=400 && status<500) {
        this.setData({attemptKey:'',selectedCharge:null,invoice:null});
      }
      // Network/5xx outcomes may already have committed: retain the exact key and outcome for retry.
    } finally { this.setData({paying:false}); }
  },
});

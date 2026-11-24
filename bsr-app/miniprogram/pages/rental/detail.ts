import { rentalProblem, rentalAccessLost, type RentalContract, type RentalChange, type RentalAdjustment, type ExternalResult } from './rentalApi';
import { configuredRentalApi } from './configuredApi';
import { requestKey } from './paymentKey';

Page({
  data: { id: '', paymentMessage: '', paymentKey: '', payable: '', contract: null as RentalContract | null, changes: [] as ReadonlyArray<RentalChange>, transactions: [] as ReadonlyArray<ExternalResult>, proposal: null as RentalAdjustment | null, deciding: false, loading: false, error: '' },
  onLoad(options: Record<string, string | undefined>) { this.setData({ id: options['id'] ?? '' }); },
  async onShow() {
    if (this.data.loading || this.data.deciding) return;
    this.setData({ loading: true, contract: null, changes: [], transactions: [], proposal: null, error: '' });
    try {
      const api = configuredRentalApi();
      const contract = await api.read(this.data.id);
      const changes = await api.changes(this.data.id);
      const histories = await Promise.all(contract.charges.map(c => api.transactions(c.chargeId)));
      const transactions = histories.flat();
      let payable = '';
      if (contract.status === 'PENDING_CONFIRMATION') payable = contract.amountDue;
      if (contract.status === 'PENDING_PAYMENT') {
        const invoice = await api.invoice(this.data.id);
        payable = invoice.charges.find(row => row.type === 'INITIAL' && row.canSimulatePayment)?.outstandingAmount ?? '';
      }
      this.setData({ contract, changes, transactions, payable });
    } catch (error) { this.setData({ error: rentalProblem(error) }); }
    finally { this.setData({ loading: false }); }
  },
  async reviewChange(event: { currentTarget: { dataset: { id?: string } } }) {
    if (this.data.loading || this.data.deciding) return;
    this.setData({ loading: true, proposal: null, error: '' });
    try { this.setData({ proposal: await configuredRentalApi().adjustment(event.currentTarget.dataset.id ?? '') }); }
    catch (error) { this.setData({ error: rentalProblem(error), ...(rentalAccessLost(error) ? { contract: null, changes: [], transactions: [], proposal: null } : {}) }); }
    finally { this.setData({ loading: false }); }
  },
  confirmAdjustment() { return this.decideAdjustment(true); },
  rejectAdjustment() { return this.decideAdjustment(false); },
  async decideAdjustment(confirm: boolean) {
    const proposal = this.data.proposal;
    if (this.data.loading || this.data.deciding || !proposal || proposal.change.status !== 'PROCESSING' || proposal.change.amountDelta === null) return;
    this.setData({ deciding: true, error: '' });
    try {
      const api = configuredRentalApi();
      this.setData({ proposal: await api.decideAdjustment(proposal.change.changeId, confirm) });
      const contract = await api.read(this.data.id);
      const changes = await api.changes(this.data.id);
      this.setData({ contract, changes });
    } catch (error) { this.setData({ error: rentalProblem(error), ...(rentalAccessLost(error) ? { contract: null, changes: [], transactions: [], proposal: null } : {}) }); }
    finally { this.setData({ deciding: false }); }
  },
  async payInitial() {
    const before = this.data.contract;
    if (this.data.loading || this.data.deciding || !before || !this.data.payable ||
        !['PENDING_CONFIRMATION', 'PENDING_PAYMENT'].includes(before.status)) return;
    const key = this.data.paymentKey || requestKey();
    this.setData({ deciding: true, paymentKey: key, error: '', paymentMessage: '' });
    let message = '';
    try {
      const api = configuredRentalApi();
      const confirmed = before.status === 'PENDING_CONFIRMATION' ? await api.decide(before.contractId, true) : before;
      if (confirmed.status === 'PENDING_ISSUE' && Number(before.amountDue) === 0) {
        message = '合同已确认，应付金额为零，可到门店领取装备。';
      } else {
        const invoice = await api.invoice(before.contractId);
        const charge = invoice.charges.find(row => row.type === 'INITIAL');
        if (charge?.canSimulatePayment && Number(charge.outstandingAmount) === Number(this.data.payable)) {
          const payment = await api.simulate(charge.chargeId, key, 'SUCCEEDED');
          message = payment.status === 'SUCCEEDED' ? '模拟付款成功，本次不扣款。可到门店领取装备。' : '模拟付款未成功，请核对账单。';
        } else {
          message = '账单状态或余额已更新，请核对最新金额。';
        }
      }
      this.setData({ paymentKey: '' });
    } catch (error) {
      message = '请核对最新合同和账单后重试。' + rentalProblem(error);
    } finally {
      this.setData({ deciding: false, paymentMessage: message });
      await this.onShow();
    }
  },
  confirm() { wx.navigateTo({ url: `/pages/rental/confirm?id=${this.data.id}` }); },
  viewInvoice() { wx.navigateTo({ url: `/pages/rental/invoice?id=${this.data.id}` }); },
  requestChange() { wx.navigateTo({ url: `/pages/rental/change?id=${this.data.id}` }); },
});

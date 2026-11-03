import { rentalProblem, rentalAccessLost, type RentalContract, type RentalChange, type RentalAdjustment, type ExternalResult } from './rentalApi';
import { configuredRentalApi } from './configuredApi';
Page({
  data: { id: '', contract: null as RentalContract | null, changes: [] as ReadonlyArray<RentalChange>, transactions: [] as ReadonlyArray<ExternalResult>, proposal: null as RentalAdjustment | null, deciding: false, loading: false, error: '' },
  onLoad(options: Record<string, string | undefined>) { this.setData({ id: options['id'] ?? '' }); },
  async onShow() {
    if (this.data.loading || this.data.deciding) return;
    this.setData({ loading: true, contract: null, changes: [], transactions: [], proposal: null, error: '' });
    try {
      const api = configuredRentalApi();
      const contract = await api.read(this.data.id);
      const changes = await api.changes(this.data.id);
      const histories = await Promise.all(contract.charges.map(c => api.transactions(c.chargeId)));
      this.setData({ contract, changes, transactions: histories.flat() });
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
  confirm() { wx.navigateTo({ url: `/pages/rental/confirm?id=${this.data.id}` }); },
  viewInvoice() { wx.navigateTo({ url: `/pages/rental/invoice?id=${this.data.id}` }); },
  requestChange() { wx.navigateTo({ url: `/pages/rental/change?id=${this.data.id}` }); },
});

import { rentalProblem, rentalAccessLost, type RentalContract } from './rentalApi';
import { configuredRentalApi } from './configuredApi';
Page({
  data: { id: '', contract: null as RentalContract | null, loading: false, submitting: false, error: '' },
  async onLoad(options: Record<string, string | undefined>) {
    this.setData({ id: options['id'] ?? '', loading: true });
    try { this.setData({ contract: await configuredRentalApi().read(this.data.id) }); }
    catch (error) { this.setData({ error: rentalProblem(error), ...(rentalAccessLost(error) ? { contract: null } : {}) }); }
    finally { this.setData({ loading: false }); }
  },
  async onShow() {
    if (this.data.loading || this.data.submitting || !this.data.id) return;
    this.setData({ contract: null, loading: true, error: '' });
    try { this.setData({ contract: await configuredRentalApi().read(this.data.id) }); }
    catch (error) { this.setData({ error: rentalProblem(error) }); }
    finally { this.setData({ loading: false }); }
  },
  accept() { return this.decide(true); },
  reject() { return this.decide(false); },
  async decide(confirm: boolean) {
    if (this.data.submitting || this.data.contract?.status !== 'PENDING_CONFIRMATION') return;
    this.setData({ submitting: true, error: '' });
    try { this.setData({ contract: await configuredRentalApi().decide(this.data.id, confirm) }); }
    catch (error) { this.setData({ error: rentalProblem(error), ...(rentalAccessLost(error) ? { contract: null } : {}) }); }
    finally { this.setData({ submitting: false }); }
  },
});

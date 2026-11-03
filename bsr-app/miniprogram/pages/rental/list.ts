import { rentalProblem, type RentalContract } from './rentalApi';
import { configuredRentalApi } from './configuredApi';
Page({
  data: { current: [] as ReadonlyArray<RentalContract>, history: [] as ReadonlyArray<RentalContract>, loading: false, error: '' },
  async onShow() {
    this.setData({ loading: true, current: [], history: [], error: '' });
    try {
      const contracts = await configuredRentalApi().list();
      this.setData({ current: contracts.filter(c => !['ENDED', 'CANCELLED'].includes(c.status)), history: contracts.filter(c => ['ENDED', 'CANCELLED'].includes(c.status)) });
    } catch (error) { this.setData({ error: rentalProblem(error) }); }
    finally { this.setData({ loading: false }); }
  },
  open(event: WechatMiniprogram.TouchEvent) {
    const id = String(event.currentTarget.dataset['id'] ?? '');
    if (/^[1-9][0-9]{0,18}$/.test(id)) wx.navigateTo({ url: `/pages/rental/detail?id=${id}` });
  },
});

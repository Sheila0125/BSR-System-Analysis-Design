import { rentalStoreName } from '../../shared/rentalChoices';
import type { RentalRequestView } from './customerRequestApi';
import { customerRequestApiForConfiguredOrigin } from './configuredApi';

Page({
  data: {
    requests: [] as ReadonlyArray<RentalRequestView & { serviceStoreName: string }>,
    loading: false,
    errorMessage: '',
  },

  async onShow() {
    this.setData({ loading: true, requests: [], errorMessage: '' });
    try {
      const requests = await customerRequestApiForConfiguredOrigin().listRequests();
      this.setData({
        requests: requests.map(request => ({
          ...request,
          serviceStoreName: rentalStoreName(request.serviceStoreId),
        })),
      });
    } catch (error) {
      this.setData({ errorMessage: problemMessage(error, 'Could not load your rental requests.') });
    } finally {
      this.setData({ loading: false });
    }
  },

  openCreate() {
    wx.navigateTo({ url: '/pages/customerrequest/create' });
  },

  openDetail(event: WechatMiniprogram.TouchEvent) {
    const requestId = String(event.currentTarget.dataset['requestId'] ?? '');
    if (/^[1-9][0-9]{0,18}$/.test(requestId)) {
      wx.navigateTo({ url: `/pages/customerrequest/detail?id=${requestId}` });
    }
  },
});

function problemMessage(error: unknown, fallback: string): string {
  const detail = (error as { detail?: unknown }).detail;
  return typeof detail === 'string' && detail.length > 0 ? detail : fallback;
}

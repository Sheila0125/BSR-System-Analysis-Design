import { customerAuthForOrigin } from '../../shared/auth/customerAuth';
import type { MiniApiProblem } from '../../shared/session-request/sessionRequest';

Page({
  data: {
    loginName: '',
    password: '',
    errorMessage: '',
    submitting: false,
  },

  onLoginName(event: WechatMiniprogram.Input) {
    this.setData({ loginName: event.detail.value });
  },

  onPassword(event: WechatMiniprogram.Input) {
    this.setData({ password: event.detail.value });
  },

  async submit() {
    const origin = getApp<{ globalData?: { apiOrigin?: string } }>().globalData?.apiOrigin;
    if (origin === undefined) {
      this.setData({ errorMessage: 'The secure BSR API origin is not configured.' });
      return;
    }
    this.setData({ submitting: true, errorMessage: '' });
    try {
      await customerAuthForOrigin(origin).login(this.data.loginName, this.data.password);
      this.setData({ password: '' });
      wx.reLaunch({ url: '/pages/customerrequest/home' });
    } catch (error) {
      const problem = error as (Partial<MiniApiProblem> & { errMsg?: string }) | undefined;
      const domainRejected = typeof problem?.errMsg === 'string'
        && /not in domain list|合法域名/i.test(problem.errMsg);
      this.setData({
        password: '',
        errorMessage: domainRejected
          ? 'WeChat rejected the BSR request domain.'
          : (problem?.detail ?? 'Sign-in failed. Check the connection and try again.'),
      });
    } finally {
      this.setData({ submitting: false });
    }
  },
});

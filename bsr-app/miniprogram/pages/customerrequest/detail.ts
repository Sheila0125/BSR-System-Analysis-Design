import {
  resubmitWithRetainedResponse,
  type RentalRequestView,
} from './customerRequestApi';
import { customerRequestApiForConfiguredOrigin } from './configuredApi';

Page({
  data: {
    request: null as RentalRequestView | null,
    loading: false,
    responseDraft: '',
    submitting: false,
    errorMessage: '',
    successMessage: '',
  },

  async onLoad(options: Record<string, string | undefined>) {
    const requestId = options['id'] ?? '';
    this.setData({ request: null, responseDraft: '', loading: true, errorMessage: '', successMessage: '' });
    try {
      const request = await customerRequestApiForConfiguredOrigin().loadRequest(requestId);
      this.setData({ request });
    } catch (error) {
      const detail = (error as { detail?: unknown }).detail;
      this.setData({
        errorMessage: typeof detail === 'string' ? detail : 'Could not load this rental request.',
      });
    } finally {
      this.setData({ loading: false });
    }
  },

  onResponse(event: WechatMiniprogram.Input) {
    this.setData({ responseDraft: event.detail.value });
  },

  async resubmitSupplement() {
    const request = this.data.request;
    const response = this.data.responseDraft.trim();
    if (request === null || request.status !== 'NEEDS_INFO') {
      this.setData({ errorMessage: 'This request is not waiting for supplemental information.' });
      return;
    }
    if (response.length === 0 || response.length > 500) {
      this.setData({ errorMessage: 'Enter a reply of at most 500 characters.' });
      return;
    }

    this.setData({ submitting: true, errorMessage: '', successMessage: '' });
    let outcome;
    try {
      outcome = await resubmitWithRetainedResponse(
        customerRequestApiForConfiguredOrigin(),
        request.requestId,
        response,
      );
    } catch (error) {
      const detail = (error as { detail?: unknown; message?: unknown }).detail;
      const message = (error as { message?: unknown }).message;
      this.setData({
        submitting: false,
        errorMessage: typeof detail === 'string'
          ? detail
          : typeof message === 'string'
            ? message
            : 'Could not send your reply. Your input is still here.',
      });
      return;
    }
    this.setData({ submitting: false });
    if (!outcome.ok) {
      this.setData({ errorMessage: outcome.problem.detail });
      return;
    }
    this.setData({
      request: outcome.request,
      responseDraft: '',
      successMessage: 'Your information was sent and the request is pending review again.',
    });
  },
});

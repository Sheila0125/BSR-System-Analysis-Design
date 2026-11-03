import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const requestApi = vi.hoisted(() => ({ listRequests: vi.fn(), loadRequest: vi.fn(), loadProfile: vi.fn() }));
const rentalApi = vi.hoisted(() => ({ list: vi.fn() }));
vi.mock('../../pages/customerrequest/configuredApi', () => ({ customerRequestApiForConfiguredOrigin: () => requestApi }));
vi.mock('../../pages/rental/configuredApi', () => ({ configuredRentalApi: () => rentalApi }));

interface PageHarness {
  data: Record<string, unknown>;
  setData(patch: Record<string, unknown>): void;
  onShow(): Promise<void>;
  onLoad(options?: Record<string, string>): Promise<void>;
}
let page: PageHarness;
beforeEach(() => {
  vi.resetModules();
  vi.clearAllMocks();
  vi.stubGlobal('Page', (options: PageHarness) => {
    page = { ...options, data: structuredClone(options.data), setData(patch) { Object.assign(this.data, patch); } };
  });
});
afterEach(() => vi.unstubAllGlobals());

describe('AC-WX06 cross-channel refresh and customer switch', () => {
  it('clears previous customer request data before a switched or expired session can fail', async () => {
    requestApi.listRequests.mockResolvedValueOnce([{ requestId: '91', status: 'SUBMITTED' }])
      .mockRejectedValueOnce({ status: 401, detail: 'Sign in again' });
    await import(`../../pages/customerrequest/${'home'}.ts`);
    await page.onShow();
    expect(page.data['requests']).toEqual([{ requestId: '91', status: 'SUBMITTED' }]);
    await page.onShow();
    expect(page.data['requests']).toEqual([]);
    expect(page.data['errorMessage']).toBe('Sign in again');
  });

  it('reloads changed rental state from the shared backend each time the page appears', async () => {
    rentalApi.list.mockResolvedValueOnce([{ contractId: '91', status: 'ACTIVE' }])
      .mockResolvedValueOnce([{ contractId: '91', status: 'ENDED' }]);
    await import(`../../pages/rental/${'list'}.ts`);
    await page.onShow();
    expect(page.data['current']).toEqual([{ contractId: '91', status: 'ACTIVE' }]);
    await page.onShow();
    expect(page.data['current']).toEqual([]);
    expect(page.data['history']).toEqual([{ contractId: '91', status: 'ENDED' }]);
  });

  it('removes a previous customer request when the next account cannot read it', async () => {
    requestApi.loadRequest.mockRejectedValue({ status: 401, detail: 'Sign in again' });
    await import(`../../pages/customerrequest/${'detail'}.ts`);
    page.setData({ request: { requestId: '91', customerId: 'old-customer' }, responseDraft: 'Retained note' });
    await page.onLoad({ id: '91' });
    expect(page.data['request']).toBeNull();
    expect(page.data['responseDraft']).toBe('');
  });

  it('removes previous customer profile fields before an expired reload', async () => {
    requestApi.loadProfile.mockRejectedValue({ status: 401, detail: 'Sign in again' });
    await import(`../../pages/customerrequest/${'profile'}.ts`);
    page.setData({ fullName: 'Previous Customer', customerNumber: 'OLD-91', heightCm: '180' });
    await page.onLoad();
    expect(page.data['fullName']).toBe('');
    expect(page.data['customerNumber']).toBe('');
    expect(page.data['heightCm']).toBe('');
  });
});

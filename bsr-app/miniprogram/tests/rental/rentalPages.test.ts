import { readFileSync } from 'node:fs';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
const api = vi.hoisted(() => ({ read: vi.fn(), changes: vi.fn(), transactions: vi.fn(), adjustment: vi.fn(), decideAdjustment: vi.fn(), submitChange: vi.fn() }));
vi.mock('../../pages/rental/configuredApi', () => ({ configuredRentalApi: () => api }));
type PageHarness = {
  data: Record<string, unknown>; setData(patch: Record<string, unknown>): void;
  onLoad(options: Record<string, string>): void | Promise<void>; onShow(): Promise<void>;
  submit(): Promise<void>; reviewChange(event: { currentTarget: { dataset: { id: string } } }): Promise<void>;
  confirmAdjustment(): Promise<void>;
  returned(event: { detail: { value: string[] } }): void;
  type(event: { detail: { value: string } }): void;
  equipment(event: { detail: { value: string } }): void;
};
let page: PageHarness;
// Pages are checked by the native tsconfig; load at runtime without mixing native and Node globals.
async function loadPage(name: string) { await import(`../../pages/rental/${name}.ts`); }
beforeEach(() => {
  vi.resetModules(); vi.resetAllMocks();
  vi.stubGlobal('getApp', () => ({ globalData: { rentalStores: [{ id: '3', name: '演示门店' }] } }));
  vi.stubGlobal('Page', (options: PageHarness) => { page = { ...options, data: structuredClone(options.data), setData(patch) { Object.assign(this.data, patch); } }; });
});
afterEach(() => vi.unstubAllGlobals());
it('AC-WX03 pending duplicate intent is suppressed; expired Session clears old contract but preserves entered note/store', async () => {
  api.read.mockResolvedValue({ status: 'ACTIVE', serviceStoreId: '3' });
  let reject: (error: unknown) => void = () => undefined;
  api.submitChange.mockImplementation(() => new Promise((_resolve, failed) => { reject = failed; }));
  await loadPage('change');
  await page.onLoad({ id: '91' }); page.setData({ note: 'My change note', storeId: '4' });
  page.equipment({ detail: { value: '2' } });
  const pending = page.submit(); await page.submit();
  expect(api.submitChange).toHaveBeenCalledTimes(1);
  expect(api.submitChange).toHaveBeenCalledWith('91', expect.objectContaining({ note: '希望换入或新增：雪鞋（BOOTS）；My change note' }));
  reject({ status: 401, code: 'AUTHENTICATION_REQUIRED', detail: 'Log in again' }); await pending;
  expect(page.data['contract']).toBeNull();
  expect(page.data['note']).toBe('My change note'); expect(page.data['storeId']).toBe('4');
  expect(page.data['error']).toBe('Log in again');
});
it('AC-WX03 ended contract offers only a limited formal reduction request for an unrefunded return', async () => {
  api.read.mockResolvedValue({ status: 'ENDED', serviceStoreId: '3' });
  api.submitChange.mockResolvedValue({ changeId: '92' });
  await loadPage('change');
  await page.onLoad({ id: '91' }); page.setData({ note: 'Refund returned ski future days' });
  await page.submit();
  expect(page.data['types']).toEqual(['REMOVE', 'PARTIAL_RETURN']);
  expect(api.submitChange).toHaveBeenCalledWith('91', {
    requestedStoreId: '3', type: 'REMOVE', note: 'Refund returned ski future days',
  });
  expect(page.data['submittedId']).toBe('92');
});
it('AC-WX03 quote shows final item and amount; expired confirmation is not replayed and clears personal details', async () => {
  api.read.mockResolvedValue({ contractId: '91', status: 'ACTIVE', charges: [] });
  api.changes.mockResolvedValue([{ changeId: '9', status: 'PROCESSING', amountDelta: '161.00' }]);
  api.adjustment.mockResolvedValue({ change: { changeId: '9', status: 'PROCESSING', amountDelta: '161.00' }, items: [{ equipmentId: '9007199254740993', effect: 'REPLACE_IN', dailyRateSnapshot: '120.00' }], charges: [] });
  api.decideAdjustment.mockRejectedValue({ status: 401, code: 'AUTHENTICATION_REQUIRED' });
  await loadPage('detail');
  await page.onLoad({ id: '91' }); await page.onShow();
  await page.reviewChange({ currentTarget: { dataset: { id: '9' } } });
  expect((page.data['proposal'] as { change: { amountDelta: string } }).change.amountDelta).toBe('161.00');
  await page.confirmAdjustment();
  expect(api.decideAdjustment).toHaveBeenCalledTimes(1); expect(api.decideAdjustment).toHaveBeenCalledWith('9', true);
  expect(page.data['contract']).toBeNull(); expect(page.data['proposal']).toBeNull();
});

it('records requested return items as intent, retains failed selection and clears it when switching to ADD', async () => {
  api.read.mockResolvedValue({ status: 'ACTIVE', serviceStoreId: '3', items: [
    { itemId: '8', equipmentId: '18', active: true, status: 'RENTED' },
    { itemId: '9', equipmentId: '19', active: false, status: 'RETURNED' },
  ] });
  api.submitChange.mockRejectedValue({ detail: 'Offline' });
  await loadPage('change'); await page.onLoad({ id: '91' });
  page.type({ detail: { value: '1' } });
  page.returned({ detail: { value: ['8'] } });
  await page.submit();
  expect(api.submitChange).toHaveBeenCalledWith('91', { requestedStoreId: '3', type: 'REMOVE', note: '', requestedReturnItemIds: ['8'] });
  expect(page.data['requestedReturnItemIds']).toEqual(['8']);
  page.type({ detail: { value: '0' } });
  expect(page.data['requestedReturnItemIds']).toEqual([]);
});
it('AC-WX02/03 native WXML conditions are real bindings and quote confirmation is visible', () => {
  for (const name of ['list', 'detail', 'confirm', 'change']) {
    const markup = readFileSync(new URL(`../../pages/rental/${name}.wxml`, import.meta.url), 'utf8');
    for (const condition of markup.matchAll(/wx:if="([^"]*)"/g)) expect(condition[1]).toMatch(/^\{\{.+\}\}$/);
  }
  const detail = readFileSync(new URL('../../pages/rental/detail.wxml', import.meta.url), 'utf8');
  expect(detail).toContain('bindtap="confirmAdjustment"'); expect(detail).toContain('proposal.change.amountDelta');
  expect(detail).toContain("contract.status === 'ACTIVE' || contract.status === 'ENDED'");
  const change = readFileSync(new URL('../../pages/rental/change.wxml', import.meta.url), 'utf8');
  expect(change).toContain("contract.status !== 'ACTIVE' && contract.status !== 'ENDED'");
});

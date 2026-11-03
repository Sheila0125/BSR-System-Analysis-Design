import { expect, it, vi } from 'vitest';
import { RentalApi } from '../../pages/rental/rentalApi';
it('AC-WX02/03 owned contract reads preserve IDs and cents as strings; confirmation uses authenticated transport', async () => {
  const readApi = vi.fn().mockResolvedValue({ contractId: '9007199254740993', amountDue: '418.50' });
  const writeApi = vi.fn().mockResolvedValue({ status: 'PENDING_PAYMENT' });
  const api = new RentalApi({ readApi, writeApi });
  expect((await api.read('9007199254740993')).amountDue).toBe('418.50');
  await api.decide('9007199254740993', true);
  expect(writeApi).toHaveBeenCalledWith('/api/contracts/9007199254740993/confirm', {});
});
it('AC-WX03/UC03 submits intent only and keeps exact draft after session failure without replay', async () => {
  const writeApi = vi.fn().mockRejectedValue({ status: 401, code: 'AUTHENTICATION_REQUIRED' });
  const api = new RentalApi({ readApi: vi.fn(), writeApi });
  const draft = { requestedStoreId: '4', type: 'PARTIAL_RETURN', note: 'Keep my input' };
  await expect(api.submitChange('91', draft)).rejects.toEqual({ status: 401, code: 'AUTHENTICATION_REQUIRED' });
  expect(writeApi).toHaveBeenCalledTimes(1);
  expect(writeApi).toHaveBeenCalledWith('/api/contracts/91/change-requests', draft);
  expect(draft.note).toBe('Keep my input');
  expect(Object.keys(draft)).toEqual(['requestedStoreId', 'type', 'note']);
});
it('AC-WX02 rejects malformed identifiers before access and only exposes read-only external history', async () => {
  const readApi = vi.fn().mockResolvedValue([]);
  const writeApi = vi.fn();
  const api = new RentalApi({ readApi, writeApi });
  expect(() => api.read('1e3')).toThrow();
  await api.transactions('9007199254740993');
  expect(readApi).toHaveBeenCalledWith('/api/rental-charges/9007199254740993/transactions');
  expect(writeApi).not.toHaveBeenCalled();
});
it('AC-WX03/UC05 customer reviews authoritative adjustment and decides without submitting a price or transaction', async () => {
  const readApi = vi.fn().mockResolvedValue({ change: { amountDelta: '161.00' }, items: [{ equipmentId: '9007199254740993' }] });
  const writeApi = vi.fn().mockRejectedValue({ status: 401, code: 'AUTHENTICATION_REQUIRED' });
  const api = new RentalApi({ readApi, writeApi });
  expect((await api.adjustment('91')).change.amountDelta).toBe('161.00');
  await expect(api.decideAdjustment('91', true)).rejects.toEqual({ status: 401, code: 'AUTHENTICATION_REQUIRED' });
  expect(writeApi).toHaveBeenCalledTimes(1);
  expect(writeApi).toHaveBeenCalledWith('/api/rental-changes/91/confirm', {});
});

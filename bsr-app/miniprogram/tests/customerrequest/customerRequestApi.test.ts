import { describe, expect, it, vi } from 'vitest';
import {
  CustomerRequestApi,
  resubmitWithRetainedResponse,
  submitWithRetainedInput,
  type AuthenticatedCustomerTransport,
  type RentalRequestInput,
} from '../../pages/customerrequest/customerRequestApi';

describe('AC-WX01 customer request API', () => {
  it('keeps identifiers as strings and routes reads and CSRF-protected writes through the authenticated transport', async () => {
    const readApi = vi.fn().mockResolvedValue([]);
    const writeApi = vi.fn().mockResolvedValue({ requestId: '9007199254740993' });
    const api = new CustomerRequestApi({ readApi, writeApi });
    const input = requestInput();

    await api.listRequests();
    const created = await api.submitRequest(input);

    expect(readApi).toHaveBeenCalledWith('/api/customer/requests');
    expect(writeApi).toHaveBeenCalledWith('/api/customer/requests', expect.objectContaining({
      serviceStoreId: '9007199254740995',
      items: [{ equipmentCategory: 'SKI', quantity: 2, fittingNote: 'Keep my draft' }],
    }));
    expect(created.requestId).toBe('9007199254740993');
  });

  it('returns the exact draft after an offline failure so the page can retry without data loss', async () => {
    const transport: AuthenticatedCustomerTransport = {
      readApi: vi.fn(),
      writeApi: vi.fn().mockRejectedValue({ code: 'NETWORK_FAILURE', detail: 'Offline' }),
    };
    const api = new CustomerRequestApi(transport);
    const input = requestInput();

    const outcome = await submitWithRetainedInput(api, input);

    expect(outcome.ok).toBe(false);
    expect(outcome.input).toBe(input);
    if (!outcome.ok) {
      expect(outcome.problem).toEqual({ code: 'NETWORK_FAILURE', detail: 'Offline' });
    }
  });

  it('resubmits the latest supplement through one CSRF-protected write and retains the reply on failure', async () => {
    const writeApi = vi.fn()
      .mockResolvedValueOnce({ requestId: '91', status: 'PENDING', supplements: [] })
      .mockRejectedValueOnce({ code: 'NETWORK_FAILURE', detail: 'Offline' });
    const api = new CustomerRequestApi({ readApi: vi.fn(), writeApi });

    await api.resubmitSupplement('91', 'Measurements updated');
    const failed = await resubmitWithRetainedResponse(api, '91', 'Keep this reply');

    expect(writeApi).toHaveBeenNthCalledWith(1,
      '/api/customer/requests/91/resubmit-supplement',
      { response: 'Measurements updated' },
    );
    expect(failed.ok).toBe(false);
    expect(failed.response).toBe('Keep this reply');
    if (!failed.ok) {
      expect(failed.problem).toEqual({ code: 'NETWORK_FAILURE', detail: 'Offline' });
    }
  });

  function requestInput(): RentalRequestInput {
    return {
      serviceStoreId: '9007199254740995',
      rentalStartDate: '2026-12-20',
      rentalDays: 3,
      skillLevel: 'INTERMEDIATE',
      items: [{ equipmentCategory: 'SKI', quantity: 2, fittingNote: 'Keep my draft' }],
      confirmDuplicate: false,
    };
  }
});

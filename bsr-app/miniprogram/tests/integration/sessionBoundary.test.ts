import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CustomerAuth } from '../../shared/auth/customerAuth';
import { SessionRequest } from '../../shared/session-request/sessionRequest';
import { RentalApi } from '../../pages/rental/rentalApi';

const request = vi.fn();
const actor = (id: string) => ({ accountId: id, loginName: `customer-${id}`, identityType: 'CUSTOMER',
  identityId: id, grants: [{ role: 'CUSTOMER', storeId: null }] });
const ok = (data: unknown, cookie = '') => ({ statusCode: 200, data,
  header: cookie ? { 'Set-Cookie': cookie } : {} });

beforeEach(() => { request.mockReset(); vi.stubGlobal('wx', { request }); });
afterEach(() => vi.unstubAllGlobals());

describe('AC-WX06 native session transport', () => {
  it('drops the previous customer cookie before a different customer signs in', async () => {
    request
      .mockImplementationOnce(({ success }) => success(ok({ headerName: 'X-XSRF-TOKEN', token: 'a' }, 'XSRF-TOKEN=csrf-a; Path=/')))
      .mockImplementationOnce(({ success }) => success(ok(actor('11'), 'JSESSIONID=customer-a; Path=/')))
      .mockImplementationOnce(({ success }) => success(ok([{ contractId: '91' }])))
      .mockImplementationOnce(({ success }) => success(ok({ headerName: 'X-XSRF-TOKEN', token: 'b' }, 'XSRF-TOKEN=csrf-b; Path=/')))
      .mockImplementationOnce(({ success }) => success(ok(actor('22'), 'JSESSIONID=customer-b; Path=/')))
      .mockImplementationOnce(({ success }) => success(ok([])));
    const auth = new CustomerAuth(new SessionRequest('https://api.example.invalid'));
    const rentals = new RentalApi(auth);
    await auth.login('first', 'secret-a');
    expect(await rentals.list()).toEqual([{ contractId: '91' }]);
    await auth.login('second', 'secret-b');
    expect(request.mock.calls[3]?.[0].header.Cookie).toBeUndefined();
    expect(request.mock.calls[4]?.[0].header.Cookie).not.toContain('customer-a');
    expect(await rentals.list()).toEqual([]);
    expect(request.mock.calls[5]?.[0].header.Cookie).toContain('customer-b');
  });

  it('keeps a change draft after an offline failure and never replays the write', async () => {
    request
      .mockImplementationOnce(({ success }) => success(ok({ headerName: 'X-XSRF-TOKEN', token: 'a' }, 'XSRF-TOKEN=csrf-a; Path=/')))
      .mockImplementationOnce(({ fail }) => fail(new Error('offline')));
    const rentals = new RentalApi(new CustomerAuth(new SessionRequest('https://api.example.invalid')));
    const draft = { requestedStoreId: '4', type: 'PARTIAL_RETURN', note: 'Please return one ski' };
    await expect(rentals.submitChange('91', draft)).rejects.toThrow('offline');
    expect(request).toHaveBeenCalledTimes(2);
    expect(draft.note).toBe('Please return one ski');
  });

  it('clears the session on a server revocation without repeating the read', async () => {
    request.mockImplementationOnce(({ success }) => success({ statusCode: 401,
      data: { code: 'SESSION_REVOKED', detail: 'Sign in again' }, header: {} }));
    const session = new SessionRequest('https://api.example.invalid');
    await expect(session.request('/api/contracts/customer', 'GET')).rejects.toMatchObject({ code: 'SESSION_REVOKED' });
    expect(session.hasSession()).toBe(false);
    expect(request).toHaveBeenCalledTimes(1);
  });
});

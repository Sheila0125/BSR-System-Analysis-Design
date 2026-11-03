import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CustomerAuth } from '../../shared/auth/customerAuth';
import { SessionRequest } from '../../shared/session-request/sessionRequest';

const requestMock = vi.fn();

beforeEach(() => {
  requestMock.mockReset();
  vi.stubGlobal('wx', { request: requestMock });
});

describe('AC-WX00 in-memory customer session', () => {
  it('captures only designated cookies, rotates the session, and sends CSRF explicitly', async () => {
    requestMock
      .mockImplementationOnce(({ success }) => success({
        statusCode: 200,
        data: { headerName: 'X-XSRF-TOKEN', token: 'masked' },
        header: { 'Set-Cookie': 'XSRF-TOKEN=raw; Path=/' },
      }))
      .mockImplementationOnce(({ success }) => success({
        statusCode: 200,
        data: { accountId: '1', loginName: 'customer', identityType: 'CUSTOMER', identityId: '2', grants: [{ role: 'CUSTOMER', storeId: null }] },
        header: { 'Set-Cookie': 'JSESSIONID=rotated; Path=/; HttpOnly, OTHER=ignored' },
      }));

    await new CustomerAuth(new SessionRequest('https://api.example.invalid')).login('customer', 'secret');

    expect(requestMock).toHaveBeenNthCalledWith(2, expect.objectContaining({
      header: expect.objectContaining({ Cookie: 'XSRF-TOKEN=raw', 'X-XSRF-TOKEN': 'masked' }),
    }));
  });

  it('clears session material after a 401 response without replaying the write', async () => {
    const session = new SessionRequest('https://api.example.invalid');
    requestMock.mockImplementationOnce(({ success }) => success({
      statusCode: 401,
      data: { code: 'SESSION_REVOKED', detail: 'revoked' },
      header: {},
    }));

    await expect(session.request('/api/auth/me', 'GET')).rejects.toMatchObject({ code: 'SESSION_REVOKED' });
    expect(session.hasSession()).toBe(false);
    expect(requestMock).toHaveBeenCalledTimes(1);
  });

  it('reuses the authenticated session and fetches CSRF once for each business write', async () => {
    const auth = new CustomerAuth(new SessionRequest('https://api.example.invalid'));
    requestMock
      .mockImplementationOnce(({ success }) => success({
        statusCode: 200,
        data: { headerName: 'X-XSRF-TOKEN', token: 'business-token' },
        header: { 'Set-Cookie': 'XSRF-TOKEN=business-cookie; Path=/' },
      }))
      .mockImplementationOnce(({ success }) => success({
        statusCode: 200,
        data: { requestId: '9007199254740993' },
        header: { 'Set-Cookie': 'JSESSIONID=business-session; Path=/; HttpOnly' },
      }));

    const result = await auth.writeApi<{ requestId: string }>(
      '/api/customer/requests',
      { serviceStoreId: '9007199254740995' },
    );

    expect(result.requestId).toBe('9007199254740993');
    expect(requestMock).toHaveBeenNthCalledWith(2, expect.objectContaining({
      url: 'https://api.example.invalid/api/customer/requests',
      header: expect.objectContaining({
        Cookie: 'XSRF-TOKEN=business-cookie',
        'X-XSRF-TOKEN': 'business-token',
      }),
    }));
    expect(requestMock).toHaveBeenCalledTimes(2);
  });
});

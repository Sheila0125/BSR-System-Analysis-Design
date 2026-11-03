import { afterEach, describe, expect, it, vi } from 'vitest'
import { login } from './authApi'

afterEach(() => vi.unstubAllGlobals())

describe('AC-UC00 Web session authentication', () => {
  it('fetches CSRF before login and sends credentials with the server-selected header', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-XSRF-TOKEN', token: 'csrf-1' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        accountId: '9007199254740993', loginName: 'manager', identityType: 'EMPLOYEE',
        identityId: '2', grants: [{ role: 'MANAGER', storeId: '3' }],
      }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    const actor = await login('manager', 'secret')

    expect(actor.accountId).toBe('9007199254740993')
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/auth/login', expect.objectContaining({
      credentials: 'same-origin',
      headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'csrf-1' }),
    }))
  })
})

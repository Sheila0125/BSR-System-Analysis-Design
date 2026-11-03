import { afterEach, describe, expect, it, vi } from 'vitest'
import { createRoleGrant, revokeRoleGrant } from './roleGrantApi'

afterEach(() => vi.unstubAllGlobals())

const grant = {
  roleGrantId: '71', accountId: '22', role: 'MANAGER', storeId: '3',
  validFrom: '2026-09-29T08:00:00', validTo: null,
}

describe('AC-BR02 Web role-grant administration', () => {
  it('obtains CSRF and sends string identifiers when creating a scoped grant', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-XSRF-TOKEN', token: 'csrf-2' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(grant), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await createRoleGrant({
      accountId: '22', role: 'MANAGER', storeId: '3',
      validFrom: '2026-09-29T08:00', validTo: null,
    })

    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/role-grants', expect.objectContaining({
      method: 'POST',
      credentials: 'same-origin',
      headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'csrf-2' }),
      body: JSON.stringify({
        accountId: '22', role: 'MANAGER', storeId: '3',
        validFrom: '2026-09-29T08:00', validTo: null,
      }),
    }))
  })

  it('uses a CSRF-protected write without a fabricated request body when revoking', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-XSRF-TOKEN', token: 'csrf-3' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...grant, validTo: '2026-09-29T09:00:00' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await revokeRoleGrant('71')

    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/role-grants/71/revoke', expect.objectContaining({
      method: 'POST', body: undefined,
    }))
  })
})

import { afterEach, describe, expect, it, vi } from 'vitest'
import { deactivateStore, operations } from './operationsApi'

const csrf = vi.hoisted(() => ({ fetchCsrf: vi.fn() }))
vi.mock('../../features/identity/authApi', () => ({ fetchCsrf: csrf.fetchCsrf }))
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks() })

describe('AC-UC14/16 workbench transport', () => {
  it('reads scoped operations with an encoded string store ID', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ storeId: '2' }) })
    vi.stubGlobal('fetch', fetch)
    expect(await operations('2')).toEqual({ storeId: '2' })
    expect(fetch.mock.calls[0]?.[0]).toBe('/api/operations?storeId=2')
  })

  it('obtains CSRF and sends a body-free store stop for the chosen ID', async () => {
    csrf.fetchCsrf.mockResolvedValue({ headerName: 'X-XSRF-TOKEN', token: 'test-token' })
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ storeId: '2', businessStatus: 'INACTIVE' }) })
    vi.stubGlobal('fetch', fetch)
    expect(await deactivateStore('2')).toEqual({ storeId: '2', businessStatus: 'INACTIVE' })
    expect(fetch).toHaveBeenCalledWith('/api/stores/2/deactivate', expect.objectContaining({
      method: 'POST', credentials: 'same-origin', headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'test-token' }),
    }))
    expect(fetch.mock.calls[0]?.[1].body).toBeUndefined()
  })
})

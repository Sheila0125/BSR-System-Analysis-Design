import { afterEach, expect, it, vi } from 'vitest'
import { rentalApi } from './rentalApi'
vi.mock('../identity/authApi', () => ({ fetchCsrf: vi.fn().mockResolvedValue({ headerName: 'X-XSRF-TOKEN', token: 'csrf-test' }) }))
afterEach(() => vi.unstubAllGlobals())
it('AC-UC04 preserves large identifier and observed decimal strings, trusted writes use Session/CSRF', async () => {
  const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ contractId: '9007199254740993', amountDue: '418.50' })))
  vi.stubGlobal('fetch', fetch)
  const result = await rentalApi.create('3', '9007199254740993', [{ equipmentId: '9007199254740995', expectedDailyRate: '99.50' }])
  expect(result.amountDue).toBe('418.50')
  expect(fetch).toHaveBeenCalledWith('/api/contracts', expect.objectContaining({ credentials: 'same-origin', headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'csrf-test' }), body: JSON.stringify({ storeId: '3', sourceRequestId: '9007199254740993', selections: [{ equipmentId: '9007199254740995', expectedDailyRate: '99.50' }] }) }))
})
it('AC-UC06 uses receiving-store minimum lookup and return semantics without client refunds', async () => {
  const fetch = vi.fn().mockImplementation(() => Promise.resolve(new Response('{}')))
  vi.stubGlobal('fetch', fetch)
  await rentalApi.returnLookup('91', '4')
  await rentalApi.returns('91', '4', [{ itemId: '92', outcome: 'NORMAL', faultDescription: null }])
  expect(fetch).toHaveBeenNthCalledWith(1, '/api/contracts/91/return-lookup?returnStoreId=4', expect.objectContaining({ credentials: 'same-origin' }))
  expect(JSON.parse(fetch.mock.calls[1]?.[1].body as string)).toEqual({ returnStoreId: '4', items: [{ itemId: '92', outcome: 'NORMAL', faultDescription: null }] })
})
it('AC-UC04/R03 does not replay an expired-session or conflicting external result write', async () => {
  const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 401, code: 'AUTHENTICATION_REQUIRED', detail: 'Login again' }), { status: 401 }))
  vi.stubGlobal('fetch', fetch)
  await expect(rentalApi.record('11', { type: 'PAYMENT', externalId: 'EXT-1', amount: '418.50', status: 'SUCCEEDED', processedAt: '2026-10-10T01:00:00Z' })).rejects.toThrow('Login again')
  expect(fetch).toHaveBeenCalledTimes(1)
})

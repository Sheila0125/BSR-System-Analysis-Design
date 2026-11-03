import { beforeEach, expect, it, vi } from 'vitest'
import { storeTransferApi } from './storeTransferApi'

const csrf = vi.hoisted(() => vi.fn())
vi.mock('../identity/authApi', () => ({ fetchCsrf: csrf }))

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ transferId: '7' }) }))
  csrf.mockResolvedValue({ headerName: 'X-CSRF-TOKEN', token: 'token' })
})

it('AC-UC11/AC-BR31 serializes transfer ids as strings and sends CSRF for each item action', async () => {
  await storeTransferApi.createTransfer('11', '12', 'TEMPORARY', ['21', '22'])
  await storeTransferApi.dispatch('7', '31')
  expect(fetch).toHaveBeenNthCalledWith(1, '/api/transfers', expect.objectContaining({
    method: 'POST', credentials: 'same-origin', body: JSON.stringify({ sourceStoreId: '11', destinationStoreId: '12', transferType: 'TEMPORARY', equipmentIds: ['21', '22'] }),
    headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'token' }),
  }))
  expect(fetch).toHaveBeenNthCalledWith(2, '/api/transfers/7/items/31/dispatch', expect.objectContaining({ body: '{}' }))
})

it('AC-BR29 rejects malformed ids without sending a request', async () => {
  expect(() => storeTransferApi.receive('7/9', '31')).toThrow('identifier')
  expect(fetch).not.toHaveBeenCalled()
})

it('AC-BR29 surfaces problem detail and affected equipment ids from conflict', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, json: async () => ({ code: 'TRANSFER_CONFLICT', detail: 'Equipment unavailable', fieldErrors: { invalidEquipmentIds: '21' } }) }))
  await expect(storeTransferApi.createTransfer('11', '12', 'PERMANENT', ['21'])).rejects.toThrow('Equipment unavailable')
})

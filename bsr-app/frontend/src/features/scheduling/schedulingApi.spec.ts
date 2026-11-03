import { beforeEach, expect, it, vi } from 'vitest'
import { schedulingApi } from './schedulingApi'

const csrf = vi.hoisted(() => vi.fn())
vi.mock('../identity/authApi', () => ({ fetchCsrf: csrf }))

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ shiftId: '7' }) }))
  csrf.mockResolvedValue({ headerName: 'X-CSRF-TOKEN', token: 'token' })
})

it('AC-UC12 serializes manager commands with decimal string IDs and CSRF', async () => {
  const input = { shiftDate: '2030-10-20', storeId: '11', employeeId: '21', startTime: '08:00', endTime: '22:00' }
  await schedulingApi.create(input)
  await schedulingApi.adjust('7', input)
  await schedulingApi.cancel('7', 'Unavailable')
  expect(fetch).toHaveBeenNthCalledWith(1, '/api/shifts', expect.objectContaining({
    method: 'POST', credentials: 'same-origin', body: JSON.stringify(input),
    headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'token' }),
  }))
  expect(fetch).toHaveBeenNthCalledWith(2, '/api/shifts/7/adjust', expect.objectContaining({ body: JSON.stringify(input) }))
  expect(fetch).toHaveBeenNthCalledWith(3, '/api/shifts/7/cancel', expect.objectContaining({ body: JSON.stringify({ reason: 'Unavailable' }) }))
})

it('AC-UC13 requests only the signed-in employee shifts and scoped manager store', async () => {
  await schedulingApi.myShifts()
  await schedulingApi.storeShifts('11')
  expect(fetch).toHaveBeenNthCalledWith(1, '/api/me/shifts', expect.objectContaining({ credentials: 'same-origin' }))
  expect(fetch).toHaveBeenNthCalledWith(2, '/api/shifts?storeId=11', expect.objectContaining({ credentials: 'same-origin' }))
})

it('AC-UC12 malformed identifiers are rejected before network requests', () => {
  expect(() => schedulingApi.storeShifts('11/12')).toThrow('identifier')
  expect(() => schedulingApi.cancel('0', 'Reason')).toThrow('identifier')
  expect(fetch).not.toHaveBeenCalled()
})

it('AC-BR35 conflict surfaces problem detail to preserve editable inputs', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, json: async () => ({ code: 'SAME_DAY_SHIFT_CONFLICT', detail: 'Employee already has a shift.', fieldErrors: {} }) }))
  await expect(schedulingApi.create({ shiftDate: '2030-10-20', storeId: '11', employeeId: '21', startTime: '08:00', endTime: '22:00' })).rejects.toThrow('Employee already has a shift.')
})

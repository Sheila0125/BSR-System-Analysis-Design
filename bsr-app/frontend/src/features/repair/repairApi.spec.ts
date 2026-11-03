import { beforeEach, expect, it, vi } from 'vitest'
import { repairApi } from './repairApi'

const fetchCsrf = vi.hoisted(() => vi.fn())
vi.mock('../identity/authApi', () => ({ fetchCsrf }))

beforeEach(() => {
  vi.clearAllMocks()
  fetchCsrf.mockResolvedValue({ headerName: 'X-XSRF-TOKEN', token: 'test-token' })
})

it('AC-UC07 repair API preserves decimal ID strings and includes same-origin CSRF on mutation', async () => {
  const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ taskId: '9007199254740993', status: 'UNASSIGNED' }) })
  vi.stubGlobal('fetch', fetchMock)
  await repairApi.create('3', '9007199254740993', 'Loose binding', null)
  expect(fetchMock).toHaveBeenCalledWith('/api/repair-tasks', expect.objectContaining({
    method: 'POST', credentials: 'same-origin',
    headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'test-token' }),
    body: JSON.stringify({ storeId: '3', equipmentId: '9007199254740993', faultDescription: 'Loose binding', contractorId: null }),
  }))
  vi.unstubAllGlobals()
})

it('AC-UC08 rejects unsafe task identifiers before issuing a request', async () => {
  const fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  expect(() => repairApi.start('1/../../other')).toThrow('identifier')
  expect(fetchMock).not.toHaveBeenCalled()
  vi.unstubAllGlobals()
})

it('AC-UC09 AC-BR24 missing historical rate returns an absent charge without a JSON parsing failure', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 200 })))
  try {
    await expect(repairApi.employeeCharge('9')).resolves.toBeNull()
  } finally {
    vi.unstubAllGlobals()
  }
})

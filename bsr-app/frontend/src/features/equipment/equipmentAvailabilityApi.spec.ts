import { afterEach, describe, expect, it, vi } from 'vitest'
import { loadAvailableEquipment } from './equipmentAvailabilityApi'

afterEach(() => vi.unstubAllGlobals())

describe('AC-UC04 equipment availability API', () => {
  it('uses a read-only same-origin request and preserves string identifiers and rates', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => [{ equipmentId: '9007199254740993', currentDailyRate: '99.50' }],
    })
    vi.stubGlobal('fetch', fetchMock)

    const result = await loadAvailableEquipment('3', ' SKI ', '')
    expect(fetchMock).toHaveBeenCalledWith('/api/equipment/available?storeId=3&category=SKI', {
      credentials: 'same-origin',
      headers: { Accept: 'application/json, application/problem+json' },
    })
    expect(result[0]?.equipmentId).toBe('9007199254740993')
    expect(result[0]?.currentDailyRate).toBe('99.50')
  })
})

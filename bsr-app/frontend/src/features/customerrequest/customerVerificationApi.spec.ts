import { afterEach, describe, expect, it, vi } from 'vitest'
import { reviewCustomerMeasurements } from './customerVerificationApi'

const authMocks = vi.hoisted(() => ({ fetchCsrf: vi.fn() }))
vi.mock('../identity/authApi', () => authMocks)

afterEach(() => vi.unstubAllGlobals())

describe('AC-UC01 employee measurement review API', () => {
  it('sends current snapshot, changes and CSRF once without a numeric customer ID conversion', async () => {
    authMocks.fetchCsrf.mockResolvedValue({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' })
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ customerId: '9007199254740993' }) })
    vi.stubGlobal('fetch', fetchMock)
    const current = {
      customerId: '9007199254740993', customerNumber: 'C-42', fullName: 'Customer',
      email: 'one@test.invalid', phone: '1', heightCm: '170.00', weightKg: null,
      bootSize: null, heightEstimated: true, weightEstimated: false, bootSizeEstimated: false,
    }
    await reviewCustomerMeasurements('3', current, {
      heightCm: '171.25', weightKg: '', bootSize: null,
      heightEstimated: false, weightEstimated: false, bootSizeEstimated: false,
    })

    const [url, options] = fetchMock.mock.calls[0]!
    expect(url).toBe('/api/employee/customers/9007199254740993/review-measurements?storeId=3')
    expect(options.method).toBe('POST')
    expect(options.headers['X-XSRF-TOKEN']).toBe('csrf-token')
    expect(JSON.parse(options.body)).toEqual({
      customerNumber: 'C-42',
      expected: { heightCm: '170.00', weightKg: null, bootSize: null,
        heightEstimated: true, weightEstimated: false, bootSizeEstimated: false },
      updated: { heightCm: '171.25', weightKg: null, bootSize: null,
        heightEstimated: false, weightEstimated: false, bootSizeEstimated: false },
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

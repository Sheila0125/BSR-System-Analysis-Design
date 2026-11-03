import { afterEach, describe, expect, it, vi } from 'vitest'
import { acceptRentalRequest, loadEmployeeRentalRequests, requestMoreInformation } from './employeeRequestApi'

afterEach(() => vi.unstubAllGlobals())

const request = {
  requestId: '91',
  requestNumber: 'REQ-91',
  customerId: '42',
  serviceStoreId: '3',
  rentalStartDate: '2026-12-20',
  rentalDays: 3,
  skillLevel: 'INTERMEDIATE',
  submittedAt: '2026-10-03T12:00:00',
  status: 'PENDING',
  acceptedByEmployeeId: null,
  items: [{ equipmentCategory: 'SKI', quantity: 2, fittingNote: null }],
  supplements: [],
}

describe('AC-UC04/AC-BR02 employee rental-request transport', () => {
  it('loads exactly the selected store with same-origin Session credentials', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify([request]), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await loadEmployeeRentalRequests('3')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/employee/rental-requests?storeId=3',
      expect.objectContaining({ credentials: 'same-origin' }),
    )
  })

  it('fetches CSRF and sends one semantic action without a fabricated body', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-XSRF-TOKEN', token: 'csrf-4' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        ...request,
        status: 'ACCEPTED',
        acceptedByEmployeeId: '17',
      }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await acceptRentalRequest('91')

    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/employee/rental-requests/91/accept',
      expect.objectContaining({
        method: 'POST',
        credentials: 'same-origin',
        headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'csrf-4' }),
      }),
    )
  })

  it('sends the employee reason with CSRF for a supplement request', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-XSRF-TOKEN', token: 'csrf-4' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(request), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await requestMoreInformation('91', 'Confirm boot size')

    expect(fetchMock).toHaveBeenNthCalledWith(2,
      '/api/employee/rental-requests/91/request-more-information',
      expect.objectContaining({ body: JSON.stringify({ reason: 'Confirm boot size' }) }),
    )
  })
})

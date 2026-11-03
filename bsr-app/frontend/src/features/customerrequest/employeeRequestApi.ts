import { ApiError, type ApiProblem } from '../../shared/http/apiClient'
import { fetchCsrf } from '../identity/authApi'

export type RentalRequestStatus =
  | 'PENDING'
  | 'NEEDS_INFO'
  | 'ACCEPTED'
  | 'UNAVAILABLE'
  | 'CONVERTED'

export interface RentalRequestItem {
  equipmentCategory: string
  quantity: number
  fittingNote: string | null
}

export interface EmployeeRentalRequest {
  requestId: string
  requestNumber: string
  customerId: string
  serviceStoreId: string
  rentalStartDate: string
  rentalDays: number
  skillLevel: string
  submittedAt: string
  status: RentalRequestStatus
  acceptedByEmployeeId: string | null
  items: RentalRequestItem[]
  supplements: Array<{
    cycleNumber: number
    reason: string
    requestedAt: string
    customerResponse: string | null
    respondedAt: string | null
  }>
}

async function decode<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new ApiError((await response.json()) as ApiProblem)
  }
  return (await response.json()) as T
}

export async function loadEmployeeRentalRequests(storeId: string): Promise<EmployeeRentalRequest[]> {
  const query = new URLSearchParams({ storeId })
  return decode<EmployeeRentalRequest[]>(await fetch(`/api/employee/rental-requests?${query.toString()}`, {
    credentials: 'same-origin',
    headers: { Accept: 'application/json, application/problem+json' },
  }))
}

async function postAction(requestId: string, action: string, body?: Record<string, unknown>): Promise<EmployeeRentalRequest> {
  const csrf = await fetchCsrf()
  return decode<EmployeeRentalRequest>(await fetch(
    `/api/employee/rental-requests/${encodeURIComponent(requestId)}/${action}`,
    {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        Accept: 'application/json, application/problem+json',
        ...(body ? { 'Content-Type': 'application/json' } : {}),
        [csrf.headerName]: csrf.token,
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
    },
  ))
}

export function requestMoreInformation(requestId: string, reason: string): Promise<EmployeeRentalRequest> {
  return postAction(requestId, 'request-more-information', { reason })
}

export function acceptRentalRequest(requestId: string): Promise<EmployeeRentalRequest> {
  return postAction(requestId, 'accept')
}

export function markRentalRequestUnavailable(requestId: string): Promise<EmployeeRentalRequest> {
  return postAction(requestId, 'mark-unavailable')
}

import { ApiError, type ApiProblem } from '../../shared/http/apiClient'
import { fetchCsrf } from '../identity/authApi'

export interface Shift {
  shiftId: string
  shiftNumber: string
  shiftDate: string
  storeId: string
  employeeId: string
  startTime: string
  endTime: string
  status: 'ACTIVE' | 'CANCELLED'
  changedByEmployeeId: string
  changedAt: string
  cancellationReason: string | null
}

export interface ShiftInput {
  shiftDate: string
  storeId: string
  employeeId: string
  startTime: string
  endTime: string
}

function id(value: string): string {
  if (!/^[1-9]\d{0,18}$/.test(value)) throw new Error('A positive decimal identifier is required.')
  return value
}

async function decode<T>(response: Response): Promise<T> {
  if (!response.ok) throw new ApiError((await response.json()) as ApiProblem)
  return await response.json() as T
}

async function get<T>(path: string): Promise<T> {
  return decode<T>(await fetch(path, {
    credentials: 'same-origin',
    headers: { Accept: 'application/json, application/problem+json' },
  }))
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const csrf = await fetchCsrf()
  return decode<T>(await fetch(path, {
    method: 'POST', credentials: 'same-origin',
    headers: {
      Accept: 'application/json, application/problem+json',
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify(body),
  }))
}

function input(value: ShiftInput): ShiftInput {
  return { ...value, storeId: id(value.storeId), employeeId: id(value.employeeId) }
}

export const schedulingApi = {
  storeShifts: (storeId: string) => get<Shift[]>(`/api/shifts?storeId=${id(storeId)}`),
  myShifts: () => get<Shift[]>('/api/me/shifts'),
  create: (shift: ShiftInput) => post<Shift>('/api/shifts', input(shift)),
  adjust: (shiftId: string, shift: ShiftInput) => post<Shift>(`/api/shifts/${id(shiftId)}/adjust`, input(shift)),
  cancel: (shiftId: string, reason: string) => post<Shift>(`/api/shifts/${id(shiftId)}/cancel`, { reason }),
}

import { ApiError, type ApiProblem } from '../../shared/http/apiClient'
import { fetchCsrf } from '../identity/authApi'

export type RepairStatus = 'UNASSIGNED' | 'PENDING' | 'IN_PROGRESS' | 'PENDING_ACCEPTANCE' | 'RETURNED' | 'PENDING_CHARGE' | 'COMPLETED'
export interface RepairTask {
  taskId: string; number: string; equipmentId: string; serviceStoreId: string
  currentContractorId: string | null; requestDate: string; faultDescription: string
  status: RepairStatus; acceptanceResult: 'PASSED' | 'FAILED' | null; acceptedAt: string | null
}
export interface RepairLabor {
  laborId: string; taskId: string; contractorId: string; repairDate: string
  durationHours: string; handlingResult: string; note: string | null
  repairRateId: string | null; hourlyRateSnapshot: string | null
  laborAmount: string | null; submittedAt: string | null
}
export interface RepairCharge {
  chargeId: string; taskId: string; totalHours: string; serviceAmount: string
  status: 'PENDING_CONFIRMATION' | 'CONFIRMED'
  confirmedByEmployeeId: string | null; confirmedAt: string | null
}
export interface RepairContractor {
  contractorId: string; number: string; name: string; contactName: string | null
  email: string; phone: string | null; active: boolean; postalAddress?: string | null
}
export interface RepairRate {
  rateId: string; contractorId: string; effectiveFrom: string
  effectiveTo: string | null; hourlyRate: string
}

function id(value: string): string {
  if (!/^[1-9]\d{0,18}$/.test(value)) throw new Error('A positive decimal identifier is required.')
  return value
}
async function decode<T>(response: Response): Promise<T> {
  if (!response.ok) throw new ApiError(await response.json() as ApiProblem)
  return await response.json() as T
}
async function get<T>(path: string): Promise<T> {
  return decode<T>(await fetch(path, {
    credentials: 'same-origin', headers: { Accept: 'application/json, application/problem+json' },
  }))
}
async function optionalCharge(taskId: string): Promise<RepairCharge | null> {
  const response = await fetch(`/api/repair-tasks/${id(taskId)}/charge`, {
    credentials: 'same-origin', headers: { Accept: 'application/json, application/problem+json' },
  })
  if (!response.ok) throw new ApiError(await response.json() as ApiProblem)
  const body = await response.text()
  return body.trim() ? JSON.parse(body) as RepairCharge : null
}
async function post<T>(path: string, body?: unknown): Promise<T> {
  const csrf = await fetchCsrf()
  return decode<T>(await fetch(path, {
    method: 'POST', credentials: 'same-origin',
    headers: { Accept: 'application/json, application/problem+json', 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: JSON.stringify(body ?? {}),
  }))
}

export const repairApi = {
  employeeTasks: (storeId: string) => get<RepairTask[]>(`/api/repair-tasks?storeId=${id(storeId)}`),
  employeeTask: (taskId: string) => get<RepairTask>(`/api/repair-tasks/${id(taskId)}`),
  employeeLabors: (taskId: string) => get<RepairLabor[]>(`/api/repair-tasks/${id(taskId)}/labors`),
  employeeCharge: optionalCharge,
  create: (storeId: string, equipmentId: string, faultDescription: string, contractorId: string | null) =>
    post<RepairTask>('/api/repair-tasks', { storeId: id(storeId), equipmentId: id(equipmentId), faultDescription, contractorId: contractorId ? id(contractorId) : null }),
  assign: (taskId: string, contractorId: string) => post<RepairTask>(`/api/repair-tasks/${id(taskId)}/assign`, { contractorId: id(contractorId) }),
  accept: (taskId: string) => post<RepairTask>(`/api/repair-tasks/${id(taskId)}/accept`),
  reject: (taskId: string, reason: string) => post<RepairTask>(`/api/repair-tasks/${id(taskId)}/reject`, { reason }),
  confirmCharge: (taskId: string) => post<RepairTask>(`/api/repair-tasks/${id(taskId)}/confirm-charge`),
  mine: (view: 'unstarted' | 'active' | 'submitted') => get<RepairTask[]>(`/api/repair-tasks/mine?view=${view}`),
  myTask: (taskId: string) => get<RepairTask>(`/api/repair-tasks/mine/${id(taskId)}`),
  myLabors: (taskId: string) => get<RepairLabor[]>(`/api/repair-tasks/mine/${id(taskId)}/labors`),
  feeHistory: () => get<RepairLabor[]>('/api/repair-tasks/mine/charges'),
  start: (taskId: string) => post<RepairTask>(`/api/repair-tasks/mine/${id(taskId)}/start`),
  addLabor: (taskId: string, repairDate: string, durationHours: string, handlingResult: string, note: string | null) =>
    post<RepairLabor>(`/api/repair-tasks/mine/${id(taskId)}/labor`, { repairDate, durationHours, handlingResult, note }),
  submit: (taskId: string) => post<RepairTask>(`/api/repair-tasks/mine/${id(taskId)}/submit`),
  contractors: (storeId: string) => get<RepairContractor[]>(`/api/repair-contractors?storeId=${id(storeId)}`),
  createContractor: (storeId: string, name: string, contactName: string | null, email: string, phone: string | null, postalAddress?: string) =>
    post<RepairContractor>('/api/repair-contractors', { storeId: id(storeId), name, contactName, email, phone, postalAddress }),
  updateContractor: (storeId: string, contractorId: string, name: string, contactName: string | null, email: string, phone: string | null, postalAddress?: string) =>
    post<RepairContractor>(`/api/repair-contractors/${id(contractorId)}/update`, { storeId: id(storeId), name, contactName, email, phone, postalAddress }),
  deactivateContractor: (storeId: string, contractorId: string) =>
    post<RepairContractor>(`/api/repair-contractors/${id(contractorId)}/deactivate`, { storeId: id(storeId) }),
  rates: (storeId: string, contractorId: string) => get<RepairRate[]>(`/api/repair-contractors/${id(contractorId)}/rates?storeId=${id(storeId)}`),
  addRate: (storeId: string, contractorId: string, effectiveFrom: string, effectiveTo: string | null, hourlyRate: string) =>
    post<RepairRate>(`/api/repair-contractors/${id(contractorId)}/rates`, { storeId: id(storeId), effectiveFrom, effectiveTo, hourlyRate }),
}

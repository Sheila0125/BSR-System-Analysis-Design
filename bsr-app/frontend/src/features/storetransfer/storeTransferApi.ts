import { ApiError, type ApiProblem } from '../../shared/http/apiClient'
import { fetchCsrf } from '../identity/authApi'

export type TransferType = 'TEMPORARY' | 'PERMANENT'
export type TransferStatus = 'INITIATED' | 'APPROVED' | 'IN_TRANSIT' | 'PARTIALLY_RECEIVED' | 'COMPLETED'
export interface TransferItem {
  itemId: string
  equipmentId: string
  dispatchResult: 'PENDING' | 'DISPATCHED' | 'EXCEPTION'
  dispatchedByEmployeeId: string | null
  dispatchedAt: string | null
  receiptResult: 'PENDING' | 'RECEIVED' | 'EXCEPTION'
  receivedByEmployeeId: string | null
  receivedAt: string | null
  exceptionNote: string | null
}
export interface Transfer {
  transferId: string
  transferNumber: string
  transferType: TransferType
  sourceStoreId: string
  destinationStoreId: string
  initiatedByEmployeeId: string
  initiatedAt: string
  approvedByEmployeeId: string | null
  approvedAt: string | null
  completedAt: string | null
  status: TransferStatus
  items: TransferItem[]
}
export interface Store {
  storeId: string
  storeNumber: string
  storeName: string
  address: string | null
  businessStatus: 'ACTIVE' | 'INACTIVE'
  openingTime: string
  closingTime: string
}

function id(value: string): string {
  if (!/^[1-9]\d{0,18}$/.test(value)) throw new Error('A positive decimal identifier is required.')
  return value
}
async function decode<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const problem = await response.json() as ApiProblem
    const affected = problem.fieldErrors?.invalidEquipmentIds
    throw new ApiError({ ...problem, detail: affected ? `${problem.detail}: ${affected}` : problem.detail })
  }
  return await response.json() as T
}
async function get<T>(path: string): Promise<T> {
  return decode<T>(await fetch(path, { credentials: 'same-origin', headers: { Accept: 'application/json, application/problem+json' } }))
}
async function post<T>(path: string, body: unknown = {}): Promise<T> {
  const csrf = await fetchCsrf()
  return decode<T>(await fetch(path, {
    method: 'POST', credentials: 'same-origin',
    headers: { Accept: 'application/json, application/problem+json', 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: JSON.stringify(body),
  }))
}

export const storeTransferApi = {
  listTransfers: (storeId: string) => get<Transfer[]>(`/api/transfers?storeId=${id(storeId)}`),
  transfer: (transferId: string) => get<Transfer>(`/api/transfers/${id(transferId)}`),
  createTransfer: (sourceStoreId: string, destinationStoreId: string, transferType: TransferType, equipmentIds: string[]) =>
    post<Transfer>('/api/transfers', { sourceStoreId: id(sourceStoreId), destinationStoreId: id(destinationStoreId), transferType, equipmentIds: equipmentIds.map(id) }),
  approve: (transferId: string) => post<Transfer>(`/api/transfers/${id(transferId)}/approve`),
  dispatch: (transferId: string, itemId: string) => post<Transfer>(`/api/transfers/${id(transferId)}/items/${id(itemId)}/dispatch`),
  receive: (transferId: string, itemId: string) => post<Transfer>(`/api/transfers/${id(transferId)}/items/${id(itemId)}/receive`),
  reportException: (transferId: string, itemId: string, phase: 'DISPATCH' | 'RECEIPT', note: string) =>
    post<Transfer>(`/api/transfers/${id(transferId)}/items/${id(itemId)}/exception`, { phase, note }),
  getStore: (storeId: string) => get<Store>(`/api/stores?storeId=${id(storeId)}`),
  createStore: (authorityStoreId: string, storeNumber: string, storeName: string, address: string | null, openingTime: string, closingTime: string) =>
    post<Store>('/api/stores', { authorityStoreId: id(authorityStoreId), storeNumber, storeName, address, openingTime, closingTime }),
  updateStore: (storeId: string, storeName: string, address: string | null, openingTime: string, closingTime: string) =>
    post<Store>(`/api/stores/${id(storeId)}/update`, { storeName, address, openingTime, closingTime }),
}

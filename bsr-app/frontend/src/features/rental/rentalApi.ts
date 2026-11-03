import { ApiError, type ApiProblem } from '../../shared/http/apiClient'
import { fetchCsrf } from '../identity/authApi'

export interface RentalItem {
  itemId: string
  equipmentId: string
  dailyRateSnapshot: string
  status: 'PENDING_ISSUE' | 'RENTED' | 'RETURNED'
  checkedOutAt: string | null
  returnedAt: string | null
  returnStoreId: string | null
  active: boolean
}
export interface Charge { chargeId: string; type: 'INITIAL' | 'SUPPLEMENT' | 'REFUND'; amount: string; status: string }
export interface Contract {
  contractId: string; contractNumber: string; customerId: string; serviceStoreId: string
  sourceRequestId: string; rentalStartDate: string; rentalDays: number; amountDue: string
  status: 'DRAFT' | 'PENDING_CONFIRMATION' | 'PENDING_PAYMENT' | 'PENDING_ISSUE' | 'ACTIVE' | 'PENDING_SETTLEMENT' | 'ENDED' | 'CANCELLED'
  items: RentalItem[]; charges: Charge[]
}
export interface ChangeRequest {
  changeId: string; contractId: string; changeNumber: string; type: string; status: string
  requestedStoreId: string; requestedAt: string; note: string | null
  handledByEmployeeId: string | null; effectiveDate: string | null; amountDelta: string | null
  requestedReturnItemIds?: string[]
}
export interface ReturnReceipt { contractId: string; status: string; items: Pick<RentalItem, 'itemId' | 'equipmentId' | 'status' | 'returnedAt' | 'returnStoreId'>[] }
export interface TransactionInput { type: 'PAYMENT' | 'SUPPLEMENT' | 'REFUND'; externalId: string; amount: string; status: 'PENDING_CONFIRMATION' | 'SUCCEEDED' | 'FAILED'; processedAt: string | null }
export interface RecordedTransaction extends TransactionInput { transactionId: string; chargeId: string; recordedByEmployeeId: string | null; recordedAt: string; source?: 'EXTERNAL' | 'SIMULATED'; simulatedByAccountId?: string | null }
export interface InvoiceCharge extends Charge { outstandingAmount: string; canSimulatePayment: boolean; transactions: RecordedTransaction[] }
export interface RentalInvoice {
  invoiceNumber: string; documentType: string; currency: string; contractId: string; contractNumber: string;
  customerId: string; customerName: string; customerNumber: string; serviceStoreId: string; storeName: string;
  contractDate: string; rentalStartDate: string; rentalDays: number; contractStatus: string; amountDue: string;
  totalPaid: string; totalRefunded: string; outstandingPayment: string; outstandingRefund: string;
  items: RentalItem[]; charges: InvoiceCharge[];
}
export interface AdjustmentItem extends Pick<RentalItem, 'itemId' | 'equipmentId' | 'status' | 'returnedAt' | 'returnStoreId' | 'active'> { effect: string | null; dailyRateSnapshot: string | null }
export interface Adjustment { change: ChangeRequest; items: AdjustmentItem[]; charges: Charge[] }

async function get<T>(path: string): Promise<T> {
  return decode<T>(await fetch(path, { credentials: 'same-origin', headers: { Accept: 'application/json, application/problem+json' } }))
}
async function post<T>(path: string, body?: unknown): Promise<T> {
  const csrf = await fetchCsrf()
  return decode<T>(await fetch(path, { method: 'POST', credentials: 'same-origin', headers: {
    Accept: 'application/json, application/problem+json', 'Content-Type': 'application/json', [csrf.headerName]: csrf.token,
  }, body: JSON.stringify(body ?? {}) }))
}
async function decode<T>(response: Response): Promise<T> {
  if (!response.ok) throw new ApiError(await response.json() as ApiProblem)
  return await response.json() as T
}
function id(value: string): string {
  if (!/^[1-9]\d{0,18}$/.test(value)) throw new Error('A positive identifier string is required.')
  return value
}
export const rentalApi = {
  list: (storeId: string) => get<Contract[]>(`/api/contracts?storeId=${id(storeId)}`),
  read: (contractId: string) => get<Contract>(`/api/contracts/${id(contractId)}`),
  invoice: (contractId: string) => get<RentalInvoice>(`/api/contracts/${id(contractId)}/invoice`),
  create: (storeId: string, sourceRequestId: string, selections: Array<{ equipmentId: string; expectedDailyRate: string }>) =>
    post<Contract>('/api/contracts', { storeId: id(storeId), sourceRequestId: id(sourceRequestId), selections }),
  submit: (contractId: string) => post<Contract>(`/api/contracts/${id(contractId)}/submit`),
  cancel: (contractId: string) => post<Contract>(`/api/contracts/${id(contractId)}/cancel`),
  revise: (contractId: string, replacedItemIds: string[], selections: Array<{ equipmentId: string; expectedDailyRate: string }>) => post<Contract>(`/api/contracts/${id(contractId)}/revise`, { replacedItemIds, selections }),
  issue: (contractId: string, itemIds: string[]) => post<Contract>(`/api/contracts/${id(contractId)}/issue`, { itemIds }),
  transactions: (chargeId: string) => get<RecordedTransaction[]>(`/api/rental-charges/${id(chargeId)}/transactions`),
  record: (chargeId: string, result: TransactionInput) => post<RecordedTransaction>(`/api/rental-charges/${id(chargeId)}/transactions`, result),
  changes: (storeId: string) => get<ChangeRequest[]>(`/api/rental-change-requests?storeId=${id(storeId)}`),
  adjustment: (changeId: string) => get<Adjustment>(`/api/rental-changes/${id(changeId)}`),
  startAdjustment: (contractId: string, storeId: string, type: string) => post<Adjustment>(`/api/contracts/${id(contractId)}/changes`, { storeId: id(storeId), type }),
  receiveAdjustment: (changeId: string, items: Array<{ itemId: string; outcome: string; faultDescription: string | null }>) => post<Adjustment>(`/api/rental-changes/${id(changeId)}/receive`, { items }),
  quoteAdjustment: (changeId: string, selections: Array<{ equipmentId: string; expectedDailyRate: string }>) => post<Adjustment>(`/api/rental-changes/${id(changeId)}/quote`, { selections }),
  stopAdjustment: (changeId: string) => post<Adjustment>(`/api/rental-changes/${id(changeId)}/stop`),
  issueAtStore: (contractId: string, storeId: string, itemIds: string[]) => post<ReturnReceipt>(`/api/contracts/${id(contractId)}/issue-at-store`, { storeId: id(storeId), itemIds }),
  returnLookup: (contractId: string, storeId: string) => get<ReturnReceipt>(`/api/contracts/${id(contractId)}/return-lookup?returnStoreId=${id(storeId)}`),
  returns: (contractId: string, returnStoreId: string, items: Array<{ itemId: string; outcome: string; faultDescription: string | null }>) =>
    post<ReturnReceipt>(`/api/contracts/${id(contractId)}/returns`, { returnStoreId: id(returnStoreId), items }),
}

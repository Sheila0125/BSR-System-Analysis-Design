export interface ContractItem { itemId: string; equipmentId: string; dailyRateSnapshot: string; status: string; checkedOutAt: string | null; returnedAt: string | null; returnStoreId: string | null; active: boolean }
export interface RentalContract {
  contractId: string; contractNumber: string; serviceStoreId: string; rentalStartDate: string;
  rentalDays: number; amountDue: string; status: string; items: ReadonlyArray<ContractItem>;
  charges: ReadonlyArray<{ chargeId: string; type: string; amount: string; status: string }>;
}
export interface RentalChange { changeId: string; type: string; status: string; requestedAt: string; note: string | null; effectiveDate: string | null; amountDelta: string | null; requestedReturnItemIds?: ReadonlyArray<string> }
export interface RentalAdjustment { change: RentalChange; items: ReadonlyArray<{ itemId: string; equipmentId: string; status: string; effect: string | null; dailyRateSnapshot: string | null; active: boolean }>; charges: RentalContract['charges'] }
export interface ExternalResult { transactionId: string; externalId: string; amount: string; status: string; processedAt: string | null; recordedAt?: string; source?: 'EXTERNAL' | 'SIMULATED'; simulatedByAccountId?: string | null }
export interface InvoiceCharge { chargeId: string; type: string; amount: string; status: string; outstandingAmount: string; canSimulatePayment: boolean; createdAt: string; transactions: ReadonlyArray<ExternalResult> }
export interface RentalInvoice {
  invoiceNumber: string; documentType: string; currency: string; contractId: string; contractNumber: string;
  customerId: string; customerName: string; customerNumber: string; serviceStoreId: string; storeName: string;
  contractDate: string; rentalStartDate: string; rentalDays: number; contractStatus: string; amountDue: string;
  totalPaid: string; totalRefunded: string; outstandingPayment: string; outstandingRefund: string;
  items: ReadonlyArray<ContractItem>; charges: ReadonlyArray<InvoiceCharge>;
}
export interface CustomerRentalTransport { readApi<T>(path: string): Promise<T>; writeApi<T>(path: string, data: Record<string, unknown>): Promise<T> }
export interface ChangeDraft { requestedStoreId: string; type: string; note: string; requestedReturnItemIds?: ReadonlyArray<string> }
export class RentalApi {
  constructor(private readonly transport: CustomerRentalTransport) {}
  list(): Promise<ReadonlyArray<RentalContract>> { return this.transport.readApi('/api/contracts/customer'); }
  read(id: string): Promise<RentalContract> { return this.transport.readApi(`/api/contracts/${identifier(id)}`); }
  invoice(id: string): Promise<RentalInvoice> { return this.transport.readApi(`/api/contracts/${identifier(id)}/invoice`); }
  simulate(chargeId: string, idempotencyKey: string, outcome: 'SUCCEEDED' | 'FAILED'): Promise<ExternalResult> {
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(idempotencyKey) || !['SUCCEEDED', 'FAILED'].includes(outcome)) throw new Error('请选择有效的模拟付款结果。');
    return this.transport.writeApi(`/api/rental-charges/${identifier(chargeId)}/simulated-payments`, { idempotencyKey, outcome });
  }
  decide(id: string, confirm: boolean): Promise<RentalContract> { return this.transport.writeApi(`/api/contracts/${identifier(id)}/${confirm ? 'confirm' : 'reject'}`, {}); }
  changes(id: string): Promise<ReadonlyArray<RentalChange>> { return this.transport.readApi(`/api/contracts/${identifier(id)}/changes`); }
  transactions(id: string): Promise<ReadonlyArray<ExternalResult>> { return this.transport.readApi(`/api/rental-charges/${identifier(id)}/transactions`); }
  adjustment(id: string): Promise<RentalAdjustment> { return this.transport.readApi(`/api/rental-changes/${identifier(id)}`); }
  decideAdjustment(id: string, confirm: boolean): Promise<RentalAdjustment> { return this.transport.writeApi(`/api/rental-changes/${identifier(id)}/${confirm ? 'confirm' : 'reject'}`, {}); }
  submitChange(id: string, input: ChangeDraft): Promise<RentalChange> {
    identifier(input.requestedStoreId);
    if (!['ADD', 'REMOVE', 'REPLACE', 'PARTIAL_RETURN'].includes(input.type) || input.note.length > 500) throw new Error('Select a change type and enter a note of at most 500 characters.');
    return this.transport.writeApi(`/api/contracts/${identifier(id)}/change-requests`, { ...input });
  }
}
function identifier(value: string): string {
  if (!/^[1-9][0-9]{0,18}$/.test(value)) throw new Error('A valid identifier string is required.');
  return value;
}
export function rentalProblem(error: unknown): string {
  const detail = (error as { detail?: unknown })?.detail;
  return typeof detail === 'string' ? detail : error instanceof Error ? error.message : 'Could not complete this request. Your input is retained.';
}
export function rentalAccessLost(error: unknown): boolean {
  const problem = error as { status?: number; code?: string } | null;
  return problem?.status === 401 || problem?.status === 403 || problem?.code === 'AUTHENTICATION_REQUIRED';
}

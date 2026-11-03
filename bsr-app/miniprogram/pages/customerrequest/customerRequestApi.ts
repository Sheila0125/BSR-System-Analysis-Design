import type { MiniApiProblem } from '../../shared/session-request/sessionRequest';

export interface CustomerProfile {
  readonly customerId: string;
  readonly customerNumber: string;
  readonly fullName: string;
  readonly email: string;
  readonly phone: string;
  readonly address: string | null;
  readonly birthYear: number | null;
  readonly heightCm: string | null;
  readonly weightKg: string | null;
  readonly bootSize: string | null;
  readonly heightEstimated: boolean;
  readonly weightEstimated: boolean;
  readonly bootSizeEstimated: boolean;
}

export interface MeasurementInput {
  readonly heightCm: string | null;
  readonly heightEstimated: boolean;
  readonly weightKg: string | null;
  readonly weightEstimated: boolean;
  readonly bootSize: string | null;
  readonly bootSizeEstimated: boolean;
}

export interface ContactInput {
  readonly fullName: string;
  readonly email: string;
  readonly phone: string;
  readonly address?: string;
  readonly birthYear?: string;
}

export interface RentalRequestItemInput {
  readonly equipmentCategory: string;
  readonly quantity: number;
  readonly fittingNote: string | null;
}

export interface RentalRequestInput {
  readonly serviceStoreId: string;
  readonly rentalStartDate: string;
  readonly rentalDays: number;
  readonly skillLevel: string;
  readonly items: ReadonlyArray<RentalRequestItemInput>;
  readonly confirmDuplicate: boolean;
}

export interface RentalRequestView {
  readonly requestId: string;
  readonly requestNumber: string;
  readonly customerId: string;
  readonly serviceStoreId: string;
  readonly rentalStartDate: string;
  readonly rentalDays: number;
  readonly skillLevel: string;
  readonly submittedAt: string;
  readonly status: 'PENDING' | 'NEEDS_INFO' | 'ACCEPTED' | 'UNAVAILABLE' | 'CONVERTED';
  readonly acceptedByEmployeeId: string | null;
  readonly items: ReadonlyArray<RentalRequestItemInput>;
  readonly supplements: ReadonlyArray<{
    readonly cycleNumber: number;
    readonly reason: string;
    readonly requestedAt: string;
    readonly customerResponse: string | null;
    readonly respondedAt: string | null;
  }>;
}

export interface AuthenticatedCustomerTransport {
  readApi<T>(path: string): Promise<T>;
  writeApi<T>(path: string, data: Record<string, unknown>): Promise<T>;
}

export class CustomerRequestApi {
  constructor(private readonly transport: AuthenticatedCustomerTransport) {}

  loadProfile(): Promise<CustomerProfile> {
    return this.transport.readApi<CustomerProfile>('/api/customer/profile');
  }

  updateMeasurements(input: MeasurementInput): Promise<CustomerProfile> {
    return this.transport.writeApi<CustomerProfile>(
      '/api/customer/profile/measurements',
      { ...input },
    );
  }

  updateContactDetails(input: ContactInput): Promise<CustomerProfile> {
    return this.transport.writeApi<CustomerProfile>('/api/customer/profile/contact', { ...input });
  }

  listRequests(): Promise<ReadonlyArray<RentalRequestView>> {
    return this.transport.readApi<ReadonlyArray<RentalRequestView>>('/api/customer/requests');
  }

  loadRequest(requestId: string): Promise<RentalRequestView> {
    requireIdentifier(requestId, 'requestId');
    return this.transport.readApi<RentalRequestView>(`/api/customer/requests/${requestId}`);
  }

  submitRequest(input: RentalRequestInput): Promise<RentalRequestView> {
    return this.transport.writeApi<RentalRequestView>('/api/customer/requests', {
      ...input,
      items: input.items.map((item) => ({ ...item })),
    });
  }

  resubmitSupplement(requestId: string, response: string): Promise<RentalRequestView> {
    requireIdentifier(requestId, 'requestId');
    return this.transport.writeApi<RentalRequestView>(
      `/api/customer/requests/${requestId}/resubmit-supplement`,
      { response },
    );
  }
}

export type RetainedSubmission =
  | { readonly ok: true; readonly input: RentalRequestInput; readonly request: RentalRequestView }
  | { readonly ok: false; readonly input: RentalRequestInput; readonly problem: MiniApiProblem };

export async function submitWithRetainedInput(
  api: CustomerRequestApi,
  input: RentalRequestInput,
): Promise<RetainedSubmission> {
  try {
    return { ok: true, input, request: await api.submitRequest(input) };
  } catch (error) {
    const problem = error as Partial<MiniApiProblem>;
    return {
      ok: false,
      input,
      problem: {
        code: problem.code ?? 'NETWORK_FAILURE',
        detail: problem.detail ?? 'Request failed. Check the connection and try again.',
      },
    };
  }
}

export type RetainedSupplementResponse =
  | { readonly ok: true; readonly response: string; readonly request: RentalRequestView }
  | { readonly ok: false; readonly response: string; readonly problem: MiniApiProblem };

export async function resubmitWithRetainedResponse(
  api: CustomerRequestApi,
  requestId: string,
  response: string,
): Promise<RetainedSupplementResponse> {
  try {
    return { ok: true, response, request: await api.resubmitSupplement(requestId, response) };
  } catch (error) {
    const problem = error as Partial<MiniApiProblem>;
    return {
      ok: false,
      response,
      problem: {
        code: problem.code ?? 'NETWORK_FAILURE',
        detail: problem.detail ?? 'Reply failed. Check the connection and try again.',
      },
    };
  }
}

function requireIdentifier(value: string, field: string): void {
  if (!/^[1-9][0-9]{0,18}$/.test(value)) {
    throw new Error(`${field} must be a positive decimal identifier.`);
  }
}

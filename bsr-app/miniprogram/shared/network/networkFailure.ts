export type NetworkFailureKind = 'offline' | 'timeout' | 'http' | 'unknown';

export interface NetworkFailure<TInput> {
  readonly kind: NetworkFailureKind;
  readonly message: string;
  readonly status?: number;
  readonly input: TInput;
}

export interface FailureDetails {
  readonly kind?: NetworkFailureKind;
  readonly message?: string;
  readonly status?: number;
}

export function toNetworkFailure<TInput>(
  input: TInput,
  details: FailureDetails = {},
): NetworkFailure<TInput> {
  return {
    kind: details.kind ?? 'unknown',
    message: details.message ?? 'Request failed. Please check the connection and try again.',
    ...(details.status === undefined ? {} : { status: details.status }),
    input,
  };
}

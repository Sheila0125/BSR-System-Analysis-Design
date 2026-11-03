import { ApiError, type ApiProblem } from '../../shared/http/apiClient'
import { fetchCsrf } from '../identity/authApi'

export interface VerifiedCustomer {
  customerId: string
  customerNumber: string
  fullName: string
  email: string
  phone: string
  heightCm: string | null
  weightKg: string | null
  bootSize: string | null
  heightEstimated: boolean
  weightEstimated: boolean
  bootSizeEstimated: boolean
}

export type MeasurementReview = Pick<VerifiedCustomer,
  'heightCm' | 'weightKg' | 'bootSize' |
  'heightEstimated' | 'weightEstimated' | 'bootSizeEstimated'>

export async function findCustomer(
  storeId: string,
  field: 'customerNumber' | 'email',
  value: string,
): Promise<VerifiedCustomer> {
  const query = new URLSearchParams({ storeId, [field]: value })
  const response = await fetch(`/api/employee/customers/verify?${query.toString()}`, {
    credentials: 'same-origin',
    headers: { Accept: 'application/json, application/problem+json' },
  })
  if (!response.ok) throw new ApiError((await response.json()) as ApiProblem)
  return (await response.json()) as VerifiedCustomer
}

export async function reviewCustomerMeasurements(
  storeId: string,
  customer: VerifiedCustomer,
  measurements: MeasurementReview,
): Promise<VerifiedCustomer> {
  const csrf = await fetchCsrf()
  const query = new URLSearchParams({ storeId })
  const response = await fetch(
    `/api/employee/customers/${encodeURIComponent(customer.customerId)}/review-measurements?${query.toString()}`,
    {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        Accept: 'application/json, application/problem+json',
        'Content-Type': 'application/json',
        [csrf.headerName]: csrf.token,
      },
      body: JSON.stringify({
        customerNumber: customer.customerNumber,
        expected: measurementValues(customer),
        updated: measurementValues(measurements),
      }),
    },
  )
  if (!response.ok) throw new ApiError((await response.json()) as ApiProblem)
  return (await response.json()) as VerifiedCustomer
}

function measurementValues(values: MeasurementReview): MeasurementReview {
  return {
    heightCm: values.heightCm?.trim() || null,
    weightKg: values.weightKg?.trim() || null,
    bootSize: values.bootSize?.trim() || null,
    heightEstimated: values.heightEstimated,
    weightEstimated: values.weightEstimated,
    bootSizeEstimated: values.bootSizeEstimated,
  }
}

import { ApiError, type ApiProblem } from '../../shared/http/apiClient'
import { fetchCsrf } from './authApi'

export type RoleCode = 'CUSTOMER' | 'EMPLOYEE' | 'MANAGER' | 'CONTRACTOR'

export interface RoleGrantInput {
  accountId: string
  role: RoleCode
  storeId: string | null
  validFrom: string
  validTo: string | null
}

export interface RoleGrant {
  roleGrantId: string
  accountId: string
  role: RoleCode
  storeId: string | null
  validFrom: string
  validTo: string | null
}

async function decode<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new ApiError((await response.json()) as ApiProblem)
  }
  return (await response.json()) as T
}

async function post<T>(path: string, body?: unknown): Promise<T> {
  const csrf = await fetchCsrf()
  return decode<T>(await fetch(path, {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json, application/problem+json',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      [csrf.headerName]: csrf.token,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  }))
}

export function createRoleGrant(input: RoleGrantInput): Promise<RoleGrant> {
  return post<RoleGrant>('/api/role-grants', input)
}

export function revokeRoleGrant(roleGrantId: string): Promise<RoleGrant> {
  return post<RoleGrant>(`/api/role-grants/${encodeURIComponent(roleGrantId)}/revoke`)
}

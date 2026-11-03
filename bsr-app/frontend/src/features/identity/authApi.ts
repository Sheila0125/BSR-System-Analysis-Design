import { ApiError, type ApiProblem } from '../../shared/http/apiClient'

export interface ActiveGrant {
  role: 'CUSTOMER' | 'EMPLOYEE' | 'MANAGER' | 'CONTRACTOR'
  storeId: string | null
}

export interface CurrentActor {
  accountId: string
  loginName: string
  identityType: 'CUSTOMER' | 'EMPLOYEE' | 'CONTRACTOR'
  identityId: string
  grants: ActiveGrant[]
}

export interface CsrfToken {
  headerName: string
  token: string
}

async function decode<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new ApiError((await response.json()) as ApiProblem)
  }
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
}

export async function fetchCsrf(): Promise<CsrfToken> {
  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' })
  return decode<CsrfToken>(response)
}

export async function login(loginName: string, password: string): Promise<CurrentActor> {
  const csrf = await fetchCsrf()
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify({ loginName, password }),
  })
  return decode<CurrentActor>(response)
}

export async function currentActor(): Promise<CurrentActor> {
  return decode<CurrentActor>(await fetch('/api/auth/me', { credentials: 'same-origin' }))
}

export async function logout(): Promise<void> {
  const csrf = await fetchCsrf()
  await decode<void>(await fetch('/api/auth/logout', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { [csrf.headerName]: csrf.token },
  }))
}

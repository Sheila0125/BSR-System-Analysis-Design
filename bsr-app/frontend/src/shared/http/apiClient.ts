export type EntityId = string
export type MoneyString = string

export interface ApiProblem {
  type: string
  title: string
  status: number
  detail: string
  instance?: string
  code: string
  fieldErrors: Record<string, string>
  traceId: string
}

export class ApiError extends Error {
  readonly problem: ApiProblem

  constructor(problem: ApiProblem) {
    super(problem.detail)
    this.name = 'ApiError'
    this.problem = problem
  }
}

export interface JsonRequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
}

export async function requestJson<T>(
  path: string,
  options: JsonRequestOptions = {},
): Promise<T> {
  const response = await fetch(path, {
    method: options.method ?? 'GET',
    credentials: 'same-origin',
    signal: options.signal,
    headers: {
      Accept: 'application/json, application/problem+json',
      ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' }),
    },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  if (!response.ok) {
    const problem = (await response.json()) as ApiProblem
    throw new ApiError(problem)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

export function isEntityId(value: string): value is EntityId {
  return /^[1-9]\d*$/.test(value)
}

export function isMoneyString(value: string): value is MoneyString {
  return /^(0|[1-9]\d{0,9})\.\d{2}$/.test(value)
}

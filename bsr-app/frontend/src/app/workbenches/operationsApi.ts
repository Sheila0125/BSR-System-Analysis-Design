import { ApiError, requestJson, type ApiProblem } from '../../shared/http/apiClient'
import { fetchCsrf } from '../../features/identity/authApi'

export interface OperationsState {
  storeId: string
  rentalStatuses: Record<string, number>
  equipmentStatuses: Record<string, number>
  repairStatuses: Record<string, number>
  transferStatuses: Record<string, number>
  shiftStatuses: Record<string, number>
}

export function operations(storeId: string): Promise<OperationsState> {
  return requestJson<OperationsState>(`/api/operations?storeId=${encodeURIComponent(storeId)}`)
}

export async function deactivateStore(storeId: string): Promise<{ storeId: string; businessStatus: string }> {
  const csrf = await fetchCsrf()
  const response = await fetch(`/api/stores/${encodeURIComponent(storeId)}/deactivate`, {
    method: 'POST', credentials: 'same-origin',
    headers: { Accept: 'application/json, application/problem+json', [csrf.headerName]: csrf.token },
  })
  if (!response.ok) throw new ApiError((await response.json()) as ApiProblem)
  return (await response.json()) as { storeId: string; businessStatus: string }
}

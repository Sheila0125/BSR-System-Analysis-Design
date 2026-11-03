import { ApiError, type ApiProblem, requestJson } from '../../shared/http/apiClient'
import { fetchCsrf } from '../identity/authApi'

export interface EquipmentArchive {
  equipmentId: string
  inventoryNumber: string
  equipmentName: string
  equipmentCategory: string
  skillLevel: string
  description: string | null
  purchaseDate: string | null
  purchasePrice: string | null
  retailPrice: string | null
  currentDailyRate: string
  homeStoreId: string
  currentStoreId: string
  status: string
  revision: string
}
export interface ArchiveInput {
  storeId: string
  inventoryNumber: string
  equipmentName: string
  equipmentCategory: string
  skillLevel: string
  description: string | null
  purchaseDate: string | null
  purchasePrice: string | null
  retailPrice: string | null
  currentDailyRate: string | null
  expectedRevision?: string
}
export const loadPricing = () => requestJson<{ currency: string; dailyRates: Record<string, string> }>('/api/pricing')
export const loadCatalog = (storeId: string, status: string, inventoryNumber: string) => {
  const query = new URLSearchParams({ storeId })
  if (status) query.set('status', status)
  if (inventoryNumber) query.set('inventoryNumber', inventoryNumber)
  return requestJson<EquipmentArchive[]>(`/api/equipment/catalog?${query.toString()}`)
}
export async function saveArchive(input: ArchiveInput, id?: string): Promise<EquipmentArchive> {
  const csrf = await fetchCsrf()
  const response = await fetch(`/api/equipment/catalog${id ? `/${encodeURIComponent(id)}/update` : ''}`, {
    method: 'POST', credentials: 'same-origin', headers: {
      Accept: 'application/json, application/problem+json', 'Content-Type': 'application/json', [csrf.headerName]: csrf.token,
    }, body: JSON.stringify(input),
  })
  if (!response.ok) throw new ApiError(await response.json() as ApiProblem)
  return await response.json() as EquipmentArchive
}

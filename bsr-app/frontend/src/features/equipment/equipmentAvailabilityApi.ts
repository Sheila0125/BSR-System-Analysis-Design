import { ApiError, type ApiProblem } from '../../shared/http/apiClient'

export interface AvailableEquipment {
  equipmentId: string
  inventoryNumber: string
  equipmentName: string
  equipmentCategory: string
  skillLevel: string
  homeStoreId: string
  currentStoreId: string
  status: 'AVAILABLE'
  currentDailyRate: string
}

export async function loadAvailableEquipment(
  storeId: string,
  category: string,
  skillLevel: string,
): Promise<AvailableEquipment[]> {
  const query = new URLSearchParams({ storeId })
  if (category.trim()) query.set('category', category.trim())
  if (skillLevel.trim()) query.set('skillLevel', skillLevel.trim())
  const response = await fetch(`/api/equipment/available?${query.toString()}`, {
    credentials: 'same-origin',
    headers: { Accept: 'application/json, application/problem+json' },
  })
  if (!response.ok) throw new ApiError((await response.json()) as ApiProblem)
  return (await response.json()) as AvailableEquipment[]
}

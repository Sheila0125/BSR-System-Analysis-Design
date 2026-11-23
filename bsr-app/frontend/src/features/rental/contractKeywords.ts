import { uiText } from '../../shared/presentation/zh'
import type { Contract } from './rentalApi'

export function contractKeywords(contract: Contract, categories: ReadonlyMap<string, string>): string {
  const items = contract.items.some(item => item.active) ? contract.items.filter(item => item.active) : contract.items
  const names = new Set<string>()
  for (const item of items) {
    const category = categories.get(item.equipmentId)
    if (category) names.add(uiText(category))
  }
  return [...names].join(' · ') || '雪具租赁'
}

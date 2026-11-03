import type { CurrentActor } from '../../features/identity/authApi'
import type { FeatureMenuItem } from './featureRegistry'

export type NavigationActor = Pick<CurrentActor, 'identityType' | 'grants'>

export function authorizedMenu(actor: NavigationActor | null, items: ReadonlyArray<FeatureMenuItem>): FeatureMenuItem[] {
  const granted = (role: 'EMPLOYEE' | 'MANAGER' | 'CONTRACTOR') =>
    actor?.grants.some(grant => grant.role === role && (role === 'CONTRACTOR' || grant.storeId !== null)) ?? false
  return items.filter(item => {
    switch (item.id) {
      case 'miniprogram-entry': return true
      case 'identity-session': return actor !== null
      case 'employee-workbench': return actor?.identityType === 'EMPLOYEE' && granted('EMPLOYEE')
      case 'manager-workbench': return actor?.identityType === 'EMPLOYEE' && granted('MANAGER')
      case 'repair-contractor': return actor?.identityType === 'CONTRACTOR' && granted('CONTRACTOR')
      case 'equipment-catalog':
      case 'equipment-available': return actor?.identityType === 'EMPLOYEE' && (granted('EMPLOYEE') || granted('MANAGER'))
      case 'identity-role-grants':
      case 'audit-events':
      case 'repair-management':
      case 'scheduling-management':
      case 'storetransfer-management': return actor?.identityType === 'EMPLOYEE' && granted('MANAGER')
      case 'customer-request-employee-queue':
      case 'customer-request-verify-customer':
      case 'rental-desk':
      case 'rental-returns':
      case 'rental-adjustments':
      case 'repair-employee':
      case 'storetransfer-handoff':
      case 'scheduling-my-shifts': return actor?.identityType === 'EMPLOYEE' && granted('EMPLOYEE')
      default: return false
    }
  })
}

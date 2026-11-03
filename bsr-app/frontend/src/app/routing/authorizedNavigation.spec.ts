import { describe, expect, it } from 'vitest'
import { authorizedMenu, type NavigationActor } from './authorizedNavigation'

const menu = [
  { id: 'identity-session', label: '账号信息', to: '/identity/session', order: 1 },
  { id: 'employee-workbench', label: '员工', to: '/workbenches/employee', order: 2 },
  { id: 'manager-workbench', label: '门店经理', to: '/workbenches/manager', order: 3 },
  { id: 'repair-contractor', label: 'Repairs', to: '/repair/contractor', order: 4 },
  { id: 'miniprogram-entry', label: 'Mini', to: '/entry/miniprogram', order: 5 },
  { id: 'rental-desk', label: 'Desk', to: '/rental/desk', order: 6 },
]
const actor = (identityType: NavigationActor['identityType'], roles: Array<'EMPLOYEE' | 'MANAGER' | 'CONTRACTOR'>): NavigationActor => ({
  identityType, grants: roles.map(role => ({ role, storeId: role === 'CONTRACTOR' ? null : '2' })),
})

describe('AC-BR02 role scoped primary navigation', () => {
  it('exposes the archive menu to authorized store staff, never customers or anonymous users', () => {
    const archive = [{ id: 'equipment-catalog', label: '雪具档案', to: '/equipment/catalog', order: 31 }]
    expect(authorizedMenu(actor('EMPLOYEE', ['MANAGER']), archive)).toHaveLength(1)
    expect(authorizedMenu(actor('EMPLOYEE', ['EMPLOYEE']), archive)).toHaveLength(1)
    expect(authorizedMenu({ identityType: 'CUSTOMER', grants: [{ role: 'CUSTOMER', storeId: null }] }, archive)).toHaveLength(0)
    expect(authorizedMenu(null, archive)).toHaveLength(0)
  })
  it('shows only the public mini-program entry before authentication', () => {
    expect(authorizedMenu(null, menu).map(item => item.id)).toEqual(['miniprogram-entry'])
  })
  it('keeps manager and employee workbenches separate', () => {
    expect(authorizedMenu(actor('EMPLOYEE', ['MANAGER']), menu).map(item => item.id))
      .toEqual(['identity-session', 'manager-workbench', 'miniprogram-entry'])
    expect(authorizedMenu(actor('EMPLOYEE', ['EMPLOYEE']), menu).map(item => item.id))
      .toEqual(['identity-session', 'employee-workbench', 'miniprogram-entry', 'rental-desk'])
  })
  it('shows contractor work only to the contractor identity', () => {
    expect(authorizedMenu(actor('CONTRACTOR', ['CONTRACTOR']), menu).map(item => item.id))
      .toEqual(['identity-session', 'repair-contractor', 'miniprogram-entry'])
  })
})

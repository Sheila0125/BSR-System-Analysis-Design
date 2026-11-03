import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App.vue'

const mocks = vi.hoisted(() => ({ currentActor: vi.fn() }))
vi.mock('../features/identity/authApi', () => ({ currentActor: mocks.currentActor }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ fullPath: '/' }),
  RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
  RouterView: { template: '<main />' },
}))
vi.mock('./routing/featureRegistry', () => ({ featureMenuItems: [
  { id: 'identity-session', label: '账号信息', to: '/identity/session', order: 1 },
  { id: 'employee-workbench', label: '员工工作台', to: '/workbenches/employee', order: 2 },
  { id: 'manager-workbench', label: '雪场工作台', to: '/workbenches/manager', order: 3 },
  { id: 'miniprogram-entry', label: '顾客微信小程序', to: '/entry/miniprogram', order: 4 },
  { id: 'rental-desk', label: '租赁办理', to: '/rental/desk', order: 5 },
] }))

beforeEach(() => vi.clearAllMocks())

describe('AC-BR02 primary menu loads live grants', () => {
  it('shows login and the public mini-program entry to an anonymous visitor', async () => {
    mocks.currentActor.mockRejectedValue(new Error('No session'))
    const wrapper = mount(App)
    await flushPromises()
    expect(wrapper.find('nav').text()).toContain('登录')
    expect(wrapper.find('nav').text()).toContain('顾客微信小程序')
    expect(wrapper.find('nav').text()).not.toContain('租赁办理')
  })

  it('does not show employee actions to a manager with only a manager grant', async () => {
    mocks.currentActor.mockResolvedValue({ identityType: 'EMPLOYEE', grants: [{ role: 'MANAGER', storeId: '2' }] })
    const wrapper = mount(App)
    await flushPromises()
    expect(wrapper.find('nav').text()).toContain('雪场工作台')
    expect(wrapper.find('nav').text()).not.toContain('员工工作台')
    expect(wrapper.find('nav').text()).not.toContain('租赁办理')
  })
})

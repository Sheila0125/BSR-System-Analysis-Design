import { sessionOptions } from '../../../tests/unit/sessionOptions'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RoleGrantView from './RoleGrantView.vue'

const roleGrantMocks = vi.hoisted(() => ({
  createRoleGrant: vi.fn(),
  revokeRoleGrant: vi.fn(),
}))

vi.mock('./roleGrantApi', () => roleGrantMocks)

beforeEach(() => vi.clearAllMocks())

describe('AC-BR02 RoleGrantView', () => {
  it('preserves manager input and renders the server-confirmed grant', async () => {
    roleGrantMocks.createRoleGrant.mockResolvedValue({
      roleGrantId: '71', accountId: '22', role: 'MANAGER', storeId: '3',
      validFrom: '2026-09-29T08:00:00', validTo: null,
    })
    const wrapper = mount(RoleGrantView, sessionOptions(['3', '17']))
    const form = wrapper.find('.grant-form')
    const account = form.find('input[inputmode="numeric"]')
    await account.setValue('22')
    await form.find('select').setValue('MANAGER')
    await form.get('select[aria-label="门店编号"]').setValue('3')
    const dates = form.findAll('input[type="datetime-local"]')
    await dates[0]?.setValue('2026-09-29T08:00')
    await form.trigger('submit')
    await flushPromises()

    expect(roleGrantMocks.createRoleGrant).toHaveBeenCalledWith({
      accountId: '22', role: 'MANAGER', storeId: '3',
      validFrom: '2026-09-29T08:00', validTo: null,
    })
    expect(wrapper.text()).toContain('已创建授权 71。')
    expect((account.element as HTMLInputElement).value).toBe('22')
  })

  it('rejects a missing scoped store before sending a request', async () => {
    const wrapper = mount(RoleGrantView, sessionOptions(['3', '17']))
    const form = wrapper.find('.grant-form')
    await form.find('input[inputmode="numeric"]').setValue('22')
    await form.find('input[type="datetime-local"]').setValue('2026-09-29T08:00')
    await form.trigger('submit')

    expect(roleGrantMocks.createRoleGrant).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('门店编号')
  })
})

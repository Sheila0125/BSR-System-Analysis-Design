import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WorkbenchView from './WorkbenchView.vue'

const mocks = vi.hoisted(() => ({ currentActor: vi.fn(), operations: vi.fn(), deactivateStore: vi.fn() }))
vi.mock('../../features/identity/authApi', () => ({ currentActor: mocks.currentActor }))
vi.mock('./operationsApi', () => ({ operations: mocks.operations, deactivateStore: mocks.deactivateStore }))

beforeEach(() => {
  vi.clearAllMocks()
  mocks.operations.mockResolvedValue({ storeId: '2', rentalStatuses: {}, equipmentStatuses: {}, repairStatuses: {}, transferStatuses: {}, shiftStatuses: {} })
})

describe('AC-UC16 role scoped workbenches', () => {
  it('shows employee actions only with an explicit employee grant', async () => {
    mocks.currentActor.mockResolvedValue({ identityType: 'EMPLOYEE', grants: [
      { role: 'MANAGER', storeId: '2' }, { role: 'EMPLOYEE', storeId: '1' },
    ] })
    const wrapper = mount(WorkbenchView, { props: { mode: 'employee' }, global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
    await flushPromises()
    expect(wrapper.text()).toContain('租赁办理')
    expect(wrapper.text()).toContain('我的班次')
    expect(wrapper.text()).not.toContain('门店运营概览')
    expect(wrapper.text()).not.toContain('门店与调拨')
  })

  it('does not turn a manager grant into employee permission', async () => {
    mocks.currentActor.mockResolvedValue({ identityType: 'EMPLOYEE', grants: [{ role: 'MANAGER', storeId: '2' }] })
    const wrapper = mount(WorkbenchView, { props: { mode: 'employee' }, global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
    await flushPromises()
    expect(wrapper.text()).not.toContain('租赁办理')
    expect(wrapper.get('[role="alert"]').text()).toContain('员工授权')
  })

  it('queries only a granted store, keeps input on failure, and clears old result', async () => {
    mocks.currentActor.mockResolvedValue({ identityType: 'EMPLOYEE', grants: [
      { role: 'MANAGER', storeId: '2' }, { role: 'MANAGER', storeId: '3' },
    ] })
    mocks.operations.mockResolvedValueOnce({ storeId: '2', rentalStatuses: { ACTIVE: 1 },
      equipmentStatuses: {}, repairStatuses: {}, transferStatuses: {}, shiftStatuses: {} })
      .mockRejectedValueOnce(new Error('Server unavailable'))
    const wrapper = mount(WorkbenchView, { props: { mode: 'manager' }, global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
    await flushPromises()
    expect(wrapper.text()).toContain('门店运营概览')
    expect(wrapper.text()).not.toContain('租赁办理')
    const store = wrapper.get('select[aria-label="授权门店"]')
    expect(mocks.operations).toHaveBeenCalledWith('2')
    expect(wrapper.text()).toContain('有效：1')
    await store.setValue('3')
    expect(wrapper.text()).not.toContain('有效：1')
    await wrapper.get('button[data-testid="load-operations"]').trigger('click')
    await flushPromises()
    expect(mocks.operations).toHaveBeenLastCalledWith('3')
    expect((store.element as HTMLSelectElement).value).toBe('3')
    expect(wrapper.get('[role="alert"]').text()).toContain('Server unavailable')
  })

  it('AC-UC14 requires an explicit manager confirmation before stopping the selected store', async () => {
    mocks.currentActor.mockResolvedValue({ identityType: 'EMPLOYEE', grants: [{ role: 'MANAGER', storeId: '2' }] })
    mocks.deactivateStore.mockResolvedValue({ storeId: '2', businessStatus: 'INACTIVE' })
    const wrapper = mount(WorkbenchView, { props: { mode: 'manager' }, global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
    await flushPromises()
    await wrapper.get('select[aria-label="授权门店"]').setValue('2')
    expect(wrapper.get('button[data-testid="deactivate-store"]').attributes('disabled')).toBeDefined()
    expect(mocks.deactivateStore).not.toHaveBeenCalled()
    await wrapper.get('input[aria-label="确认停用门店"]').setValue(true)
    await wrapper.get('button[data-testid="deactivate-store"]').trigger('click')
    await flushPromises()
    expect(mocks.deactivateStore).toHaveBeenCalledWith('2')
    expect(wrapper.text()).toContain('门店 2 已停用')
    expect((wrapper.get('input[aria-label="确认停用门店"]').element as HTMLInputElement).checked).toBe(false)
  })
})

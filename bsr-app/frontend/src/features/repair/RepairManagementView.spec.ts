import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import RepairManagementView from './RepairManagementView.vue'

const api = vi.hoisted(() => ({ contractors: vi.fn(), createContractor: vi.fn(), updateContractor: vi.fn(), deactivateContractor: vi.fn(), rates: vi.fn(), addRate: vi.fn() }))
vi.mock('./repairApi', () => ({ repairApi: api }))
beforeEach(() => { vi.clearAllMocks(); api.contractors.mockResolvedValue([{ contractorId: '5', name: 'Workshop', number: 'RC-5', active: true }]); api.rates.mockResolvedValue([]) })

it('AC-UC15 overlapping rate error retains period and price for correction', async () => {
  api.addRate.mockRejectedValue(new Error('Historical repair rate periods overlap'))
  const wrapper = mount(RepairManagementView)
  await wrapper.get('[aria-label="门店编号"]').setValue('3')
  await wrapper.get('[data-testid="load-contractors"]').trigger('click')
  await flushPromises()
  await wrapper.get('[data-testid="select-contractor-5"]').trigger('click')
  await wrapper.get('[aria-label="生效日期"]').setValue('2026-01-01')
  await wrapper.get('[aria-label="小时费率"]').setValue('100.00')
  await wrapper.get('[data-testid="add-rate"]').trigger('click')
  await flushPromises()
  expect(api.addRate).toHaveBeenCalledWith('3', '5', '2026-01-01', null, '100.00')
  expect(wrapper.get('[role="alert"]').text()).toContain('overlap')
  expect((wrapper.get('[aria-label="小时费率"]').element as HTMLInputElement).value).toBe('100.00')
})

it('loads, edits and retains the contractor postal address after a failed save', async () => {
  api.contractors.mockResolvedValue([{ contractorId: '5', number: 'RC-5', name: 'Workshop', contactName: 'Contact',
    email: 'workshop@test.invalid', phone: '555', postalAddress: 'Old postal road', active: true }])
  api.updateContractor.mockRejectedValue(new Error('Update failed'))
  const wrapper = mount(RepairManagementView)
  await wrapper.get('[aria-label="门店编号"]').setValue('3')
  await wrapper.get('[data-testid="load-contractors"]').trigger('click')
  await flushPromises()
  await wrapper.get('[data-testid="select-contractor-5"]').trigger('click')
  await flushPromises()
  expect((wrapper.get('[aria-label="邮寄地址"]').element as HTMLInputElement).value).toBe('Old postal road')
  await wrapper.get('[aria-label="邮寄地址"]').setValue('New postal road')
  await wrapper.findAll('button').find(button => button.text() === '更新承包商')!.trigger('click')
  await flushPromises()
  expect(api.updateContractor).toHaveBeenCalledWith('3', '5', 'Workshop', 'Contact', 'workshop@test.invalid', '555', 'New postal road')
  expect((wrapper.get('[aria-label="邮寄地址"]').element as HTMLInputElement).value).toBe('New postal road')
  expect(wrapper.get('[role="alert"]').text()).toContain('Update failed')
})

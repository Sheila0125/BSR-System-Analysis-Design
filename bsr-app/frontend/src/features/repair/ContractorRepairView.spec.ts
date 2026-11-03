import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import ContractorRepairView from './ContractorRepairView.vue'

const api = vi.hoisted(() => ({ mine: vi.fn(), myTask: vi.fn(), myLabors: vi.fn(), start: vi.fn(), addLabor: vi.fn(), submit: vi.fn(), feeHistory: vi.fn() }))
vi.mock('./repairApi', () => ({ repairApi: api }))
beforeEach(() => { vi.clearAllMocks(); api.mine.mockResolvedValue([{ taskId: '9', number: 'RT-9', equipmentId: '4', status: 'PENDING', faultDescription: 'Binding' }]); api.myLabors.mockResolvedValue([]); api.feeHistory.mockResolvedValue([]) })

it('AC-UC08 contractor four views and failed labor entry retain input without customer fields', async () => {
  api.start.mockResolvedValue({ taskId: '9', number: 'RT-9', equipmentId: '4', status: 'IN_PROGRESS', faultDescription: 'Binding' })
  api.addLabor.mockRejectedValue(new Error('Historical rate is unavailable'))
  const wrapper = mount(ContractorRepairView)
  await wrapper.get('[data-testid="view-unstarted"]').trigger('click')
  await flushPromises()
  await wrapper.get('[data-testid="select-task-9"]').trigger('click')
  await wrapper.get('[data-testid="start-repair"]').trigger('click')
  await flushPromises()
  await wrapper.get('[aria-label="维修日期"]').setValue('2026-10-01')
  await wrapper.get('[aria-label="工时（小时）"]').setValue('0.25')
  await wrapper.get('[aria-label="处理结果"]').setValue('Replaced binding')
  await wrapper.get('[data-testid="add-labor"]').trigger('click')
  await flushPromises()
  expect(api.addLabor).toHaveBeenCalledWith('9', '2026-10-01', '0.25', 'Replaced binding', null)
  expect((wrapper.get('[aria-label="处理结果"]').element as HTMLInputElement).value).toBe('Replaced binding')
  expect(wrapper.text()).not.toMatch(/customer|顾客/i)
  await wrapper.get('[data-testid="view-fees"]').trigger('click')
  await flushPromises()
  expect(api.feeHistory).toHaveBeenCalled()
})

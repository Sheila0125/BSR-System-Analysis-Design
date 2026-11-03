import { flushPromises, mount } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import MyShiftsView from './MyShiftsView.vue'

const api = vi.hoisted(() => ({ myShifts: vi.fn() }))
vi.mock('./schedulingApi', () => ({ schedulingApi: api }))

it('AC-UC13 employee view loads only self endpoint and displays shift provenance', async () => {
  api.myShifts.mockResolvedValue([{ shiftId: '7', shiftNumber: 'SH-7', shiftDate: '2030-10-20', storeId: '11', employeeId: '21', startTime: '08:00', endTime: '16:00', status: 'CANCELLED', cancellationReason: 'Unavailable' }])
  const wrapper = mount(MyShiftsView)
  await wrapper.get('[data-testid="load-my-shifts"]').trigger('click')
  await flushPromises()
  expect(api.myShifts).toHaveBeenCalledOnce()
  expect(wrapper.text()).toContain('SH-7')
  expect(wrapper.text()).toContain('已取消')
  expect(wrapper.text()).toContain('Unavailable')
  expect(wrapper.find('[aria-label="员工编号"]').exists()).toBe(false)
})

it('filters owned shifts by inclusive dates and rejects an inverted interval without another request', async () => {
  api.myShifts.mockClear()
  api.myShifts.mockResolvedValue([
    { shiftId: '1', shiftNumber: 'BEFORE', shiftDate: '2030-10-19', storeId: '11', startTime: '08:00', endTime: '16:00', status: 'ACTIVE' },
    { shiftId: '2', shiftNumber: 'IN-RANGE', shiftDate: '2030-10-20', storeId: '11', startTime: '08:00', endTime: '16:00', status: 'ACTIVE' },
    { shiftId: '3', shiftNumber: 'AFTER', shiftDate: '2030-10-21', storeId: '11', startTime: '08:00', endTime: '16:00', status: 'ACTIVE' },
  ])
  const wrapper = mount(MyShiftsView)
  await wrapper.get('[aria-label="班次开始日期"]').setValue('2030-10-20')
  await wrapper.get('[aria-label="班次结束日期"]').setValue('2030-10-20')
  await wrapper.get('[data-testid="load-my-shifts"]').trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('IN-RANGE')
  expect(wrapper.text()).not.toContain('BEFORE')
  expect(wrapper.text()).not.toContain('AFTER')
  await wrapper.get('[aria-label="班次结束日期"]').setValue('2030-10-19')
  await wrapper.get('[data-testid="load-my-shifts"]').trigger('click')
  expect(api.myShifts).toHaveBeenCalledTimes(1)
  expect(wrapper.get('[role="alert"]').text()).toContain('日期')
})

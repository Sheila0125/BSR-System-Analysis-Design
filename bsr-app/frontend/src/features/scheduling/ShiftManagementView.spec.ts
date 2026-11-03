import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import ShiftManagementView from './ShiftManagementView.vue'

const api = vi.hoisted(() => ({ storeShifts: vi.fn(), create: vi.fn(), adjust: vi.fn(), cancel: vi.fn(), employeeChoices: vi.fn() }))
vi.mock('./schedulingApi', () => ({ schedulingApi: api }))
vi.mock('./employeeContactApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./employeeContactApi')>()
  return { ...actual, employeeChoices: (storeId: string) => api.employeeChoices(storeId) }
})
beforeEach(() => { vi.clearAllMocks(); api.storeShifts.mockResolvedValue([]); api.employeeChoices.mockResolvedValue([]) })

it('loads current store employee hints with the shift queue without bypassing the final conflict check', async () => {
  api.employeeChoices.mockResolvedValue([{ employeeId: '21', employeeNumber: 'E-21', fullName: 'Course employee' }])
  const wrapper = mount(ShiftManagementView)
  await wrapper.get('[aria-label="门店编号"]').setValue('11')
  await wrapper.get('[data-testid="load-shifts"]').trigger('click')
  await flushPromises()
  expect(api.employeeChoices).toHaveBeenCalledWith('11')
  expect(wrapper.get('datalist option[value="21"]').text()).toContain('Course employee')
})

it('maintains the scoped employee address and note independently from a shift and retains failed input', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce({ ok: true, json: async () => ({ employeeId: '21', employeeNumber: 'E-21', fullName: 'Course employee', email: 'e@test.invalid', phone: null, address: 'Old address', note: 'Old note' }) })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ headerName: 'X-XSRF-TOKEN', token: 'course-csrf' }) })
    .mockRejectedValueOnce(new Error('Offline'))
  vi.stubGlobal('fetch', fetchMock)
  try {
    const wrapper = mount(ShiftManagementView)
    await wrapper.get('[aria-label="门店编号"]').setValue('11')
    await wrapper.get('[aria-label="员工编号"]').setValue('21')
    await wrapper.get('[data-testid="load-employee-contact"]').trigger('click')
    await flushPromises()
    expect((wrapper.get('[aria-label="员工地址"]').element as HTMLInputElement).value).toBe('Old address')
    await wrapper.get('[aria-label="员工地址"]').setValue('New address')
    await wrapper.get('[aria-label="员工备注"]').setValue('New note')
    await wrapper.get('[data-testid="save-employee-contact"]').trigger('click')
    await flushPromises()
    expect(fetchMock.mock.calls[2]?.[0]).toBe('/api/employees/21/contact')
    expect(JSON.parse(fetchMock.mock.calls[2]?.[1].body)).toEqual({ storeId: '11', address: 'New address', note: 'New note' })
    expect((wrapper.get('[aria-label="员工地址"]').element as HTMLInputElement).value).toBe('New address')
    expect(wrapper.get('[role="alert"]').text()).toContain('Offline')
    expect(api.create).not.toHaveBeenCalled()
    await wrapper.get('[aria-label="员工编号"]').setValue('22')
    expect(wrapper.find('[data-testid="save-employee-contact"]').exists()).toBe(false)
  } finally { vi.unstubAllGlobals() }
})

it('AC-UC12 manager creates and adjusts a shift with one date, store, employee and time range', async () => {
  const created = { shiftId: '7', shiftNumber: 'SH-7', shiftDate: '2030-10-20', storeId: '11', employeeId: '21', startTime: '08:00', endTime: '16:00', status: 'ACTIVE' }
  api.create.mockResolvedValue(created)
  api.adjust.mockResolvedValue({ ...created, endTime: '18:00' })
  const wrapper = mount(ShiftManagementView)
  await wrapper.get('[aria-label="门店编号"]').setValue('11')
  await wrapper.get('[aria-label="员工编号"]').setValue('21')
  await wrapper.get('[aria-label="排班日期"]').setValue('2030-10-20')
  await wrapper.get('[aria-label="开始时间"]').setValue('08:00')
  await wrapper.get('[aria-label="结束时间"]').setValue('16:00')
  await wrapper.get('[data-testid="save-shift"]').trigger('click')
  await flushPromises()
  expect(api.create).toHaveBeenCalledWith({ shiftDate: '2030-10-20', storeId: '11', employeeId: '21', startTime: '08:00', endTime: '16:00' })
  await wrapper.get('[data-testid="edit-7"]').trigger('click')
  await wrapper.get('[aria-label="结束时间"]').setValue('18:00')
  await wrapper.get('[data-testid="save-shift"]').trigger('click')
  await flushPromises()
  expect(api.adjust).toHaveBeenCalledWith('7', { shiftDate: '2030-10-20', storeId: '11', employeeId: '21', startTime: '08:00', endTime: '18:00' })
})

it('AC-BR35/AC-BR37 keeps manager input on conflict and requires cancellation reason', async () => {
  const shift = { shiftId: '7', shiftNumber: 'SH-7', shiftDate: '2030-10-20', storeId: '11', employeeId: '21', startTime: '08:00', endTime: '16:00', status: 'ACTIVE' }
  api.storeShifts.mockResolvedValue([shift])
  api.create.mockRejectedValue(new Error('Employee already has a shift.'))
  api.cancel.mockResolvedValue({ ...shift, status: 'CANCELLED', cancellationReason: 'Unavailable' })
  const wrapper = mount(ShiftManagementView)
  await wrapper.get('[aria-label="门店编号"]').setValue('11')
  await wrapper.get('[data-testid="load-shifts"]').trigger('click')
  await flushPromises()
  await wrapper.get('[aria-label="员工编号"]').setValue('21')
  await wrapper.get('[aria-label="排班日期"]').setValue('2030-10-20')
  await wrapper.get('[aria-label="开始时间"]').setValue('08:00')
  await wrapper.get('[aria-label="结束时间"]').setValue('16:00')
  await wrapper.get('[data-testid="save-shift"]').trigger('click')
  await flushPromises()
  expect(wrapper.get('[role="alert"]').text()).toContain('Employee already has a shift.')
  expect((wrapper.get('[aria-label="员工编号"]').element as HTMLInputElement).value).toBe('21')
  expect(wrapper.get('[data-testid="cancel-7"]').attributes('disabled')).toBeDefined()
  await wrapper.get('[aria-label="取消原因：7"]').setValue('Unavailable')
  await wrapper.get('[data-testid="cancel-7"]').trigger('click')
  await flushPromises()
  expect(api.cancel).toHaveBeenCalledWith('7', 'Unavailable')
})

it('AC-UC12 moving a shift to another store removes it from the loaded source-store list', async () => {
  const source = { shiftId: '7', shiftNumber: 'SH-7', shiftDate: '2030-10-20', storeId: '11', employeeId: '21', startTime: '08:00', endTime: '16:00', status: 'ACTIVE' }
  api.storeShifts.mockResolvedValue([source])
  api.adjust.mockResolvedValue({ ...source, storeId: '12' })
  const wrapper = mount(ShiftManagementView)
  await wrapper.get('[aria-label="门店编号"]').setValue('11')
  await wrapper.get('[data-testid="load-shifts"]').trigger('click')
  await flushPromises()
  await wrapper.get('[data-testid="edit-7"]').trigger('click')
  await wrapper.get('[aria-label="门店编号"]').setValue('12')
  await wrapper.get('[data-testid="save-shift"]').trigger('click')
  await flushPromises()
  expect(api.adjust).toHaveBeenCalledWith('7', expect.objectContaining({ storeId: '12' }))
  expect(wrapper.find('[data-testid="edit-7"]').exists()).toBe(false)
})

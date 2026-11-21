import { sessionOptions } from '../../../tests/unit/sessionOptions'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import EmployeeRepairView from './EmployeeRepairView.vue'

const api = vi.hoisted(() => ({ assignmentChoices: vi.fn(async () => []), employeeTasks: vi.fn(), create: vi.fn(), assign: vi.fn(), accept: vi.fn(), reject: vi.fn(), confirmCharge: vi.fn(), employeeLabors: vi.fn(), employeeCharge: vi.fn() }))
vi.mock('./repairApi', () => ({ repairApi: api }))
vi.mock('../equipment/equipmentCatalogApi', () => ({ loadCatalog: vi.fn(async () => [{ equipmentId: '4', inventoryNumber: 'INV-4', equipmentName: 'Snowboard' }]) }))
beforeEach(() => { vi.clearAllMocks(); api.employeeTasks.mockResolvedValue([]) })

it('AC-UC07 failed creation keeps fault and equipment input for correction', async () => {
  api.create.mockRejectedValue(new Error('Equipment is not waiting for repair'))
  const wrapper = mount(EmployeeRepairView, sessionOptions(['3']))
  await wrapper.get('[aria-label="门店编号"]').setValue('3')
  await flushPromises()
  await wrapper.get('select:has(option[value="4"])').setValue('4')
  await wrapper.get('[aria-label="故障描述"]').setValue('Loose binding')
  await wrapper.get('[data-testid="create-repair"]').trigger('click')
  await flushPromises()
  expect(api.create).toHaveBeenCalledWith('3', '4', 'Loose binding', null)
  expect(wrapper.get('[role="alert"]').text()).toContain('not waiting')
  expect((wrapper.get('[aria-label="故障描述"]').element as HTMLInputElement).value).toBe('Loose binding')
})

it('AC-UC09 accepted pending charge stays visible and can be confirmed after rate backfill', async () => {
  const task = { taskId: '9', number: 'RT-9', equipmentId: '4', serviceStoreId: '3', currentContractorId: '5', status: 'PENDING_ACCEPTANCE', faultDescription: 'Loose binding' }
  api.employeeTasks.mockResolvedValue([task])
  api.employeeLabors.mockResolvedValue([]); api.employeeCharge.mockResolvedValue(null)
  api.accept.mockResolvedValue({ ...task, status: 'PENDING_CHARGE' })
  api.confirmCharge.mockResolvedValue({ ...task, status: 'COMPLETED' })
  const wrapper = mount(EmployeeRepairView, sessionOptions(['3']))
  await wrapper.get('[aria-label="门店编号"]').setValue('3')
  await wrapper.get('[data-testid="load-repairs"]').trigger('click')
  await flushPromises()
  await wrapper.get('select:has(option[value="9"])').setValue('9')
  await flushPromises()
  await wrapper.get('[data-testid="accept-repair"]').trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('待确认费用')
  await wrapper.get('[data-testid="confirm-charge"]').trigger('click')
  await flushPromises()
  expect(api.confirmCharge).toHaveBeenCalledWith('9')
  expect(wrapper.text()).toContain('已完成')
})

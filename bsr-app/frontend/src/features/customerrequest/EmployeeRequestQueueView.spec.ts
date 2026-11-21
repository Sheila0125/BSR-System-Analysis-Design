import { sessionOptions } from '../../../tests/unit/sessionOptions'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import EmployeeRequestQueueView from './EmployeeRequestQueueView.vue'

const apiMocks = vi.hoisted(() => ({
  loadEmployeeRentalRequests: vi.fn(),
  requestMoreInformation: vi.fn(),
  acceptRentalRequest: vi.fn(),
  markRentalRequestUnavailable: vi.fn(),
}))

vi.mock('./employeeRequestApi', () => apiMocks)

const pending = {
  requestId: '91',
  requestNumber: 'REQ-91',
  customerId: '42',
  serviceStoreId: '3',
  rentalStartDate: '2026-12-20',
  rentalDays: 3,
  skillLevel: 'INTERMEDIATE',
  submittedAt: '2026-10-03T12:00:00',
  status: 'PENDING',
  acceptedByEmployeeId: null,
  items: [{ equipmentCategory: 'SKI', quantity: 2, fittingNote: 'Shorter pair' }],
  supplements: [],
}

beforeEach(() => vi.clearAllMocks())

describe('AC-UC04/AC-BR05 EmployeeRequestQueueView', () => {
  it('loads one store and applies only the server-confirmed semantic action', async () => {
    apiMocks.loadEmployeeRentalRequests.mockResolvedValue([pending])
    apiMocks.acceptRentalRequest.mockResolvedValue({
      ...pending,
      status: 'ACCEPTED',
      acceptedByEmployeeId: '17',
    })
    const wrapper = mount(EmployeeRequestQueueView, sessionOptions(['3']))
    await wrapper.get('select[aria-label="门店编号"]').setValue('3')
    await wrapper.get('.queue-search').trigger('submit')
    await flushPromises()

    expect(apiMocks.loadEmployeeRentalRequests).toHaveBeenCalledWith('3')
    expect(wrapper.text()).toContain('顾客 42 · 待处理')
    expect(wrapper.text()).toContain('来源需求编号（数字 ID）：91')
    expect(wrapper.text()).toContain('双板 × 2')

    await wrapper.get('.actions button:nth-child(2)').trigger('click')
    await flushPromises()

    expect(apiMocks.acceptRentalRequest).toHaveBeenCalledWith('91')
    expect(wrapper.text()).toContain('顾客 42 · 已受理')
    expect(wrapper.text()).toContain('已受理 REQ-91.')
  })

  it('keeps the loaded queue and store input when a write fails', async () => {
    apiMocks.loadEmployeeRentalRequests.mockResolvedValue([pending])
    apiMocks.requestMoreInformation.mockRejectedValue(new Error('offline'))
    const wrapper = mount(EmployeeRequestQueueView, sessionOptions(['3']))
    const store = wrapper.get('select[aria-label="门店编号"]')
    await store.setValue('3')
    await wrapper.get('.queue-search').trigger('submit')
    await flushPromises()
    await wrapper.get('.reason-field textarea').setValue('Confirm boot size')
    await wrapper.get('.actions button').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('检查连接')
    expect(wrapper.text()).toContain('顾客 42 · 待处理')
    expect((store.element as HTMLInputElement).value).toBe('3')
    expect((wrapper.get('.reason-field textarea').element as HTMLTextAreaElement).value).toBe('Confirm boot size')
    expect(apiMocks.requestMoreInformation).toHaveBeenCalledWith('91', 'Confirm boot size')
  })
})

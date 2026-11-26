import { sessionOptions } from '../../../tests/unit/sessionOptions'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import RentalReturnView from './RentalReturnView.vue'
import type { Contract } from './rentalApi'
const api = vi.hoisted(() => ({ list: vi.fn<(storeId: string) => Promise<Contract[]>>(async () => []), returnLookup: vi.fn(), returns: vi.fn() }))
vi.mock('./rentalApi', () => ({ rentalApi: api }))
beforeEach(() => vi.resetAllMocks())
it('selecting a listed contract loads its returnable items and supports selecting the batch explicitly', async () => {
  api.list.mockResolvedValueOnce([{
    contractId: '20', contractNumber: 'RC-20', customerId: '2', serviceStoreId: '4', sourceRequestId: '1',
    rentalStartDate: '2026-11-23', rentalDays: 1, amountDue: '100.00', status: 'ACTIVE', charges: [],
    items: [{ itemId: '21', equipmentId: '31', active: true, status: 'RENTED', dailyRateSnapshot: '100.00', checkedOutAt: '2026-11-23T08:00:00', returnedAt: null, returnStoreId: null }],
  }])
  api.returnLookup.mockResolvedValueOnce({ contractId: '20', status: 'ACTIVE', items: [
    { itemId: '21', equipmentId: '31', status: 'RENTED', returnedAt: null },
    { itemId: '22', equipmentId: '32', status: 'RETURNED', returnedAt: '2026-11-23T10:00:00' },
  ] })
  const wrapper = mount(RentalReturnView, sessionOptions(['4']))
  await wrapper.get('[aria-label="合同所属门店"]').setValue('4')
  await flushPromises()
  expect(wrapper.get('[data-test="contract-20"]').attributes('aria-pressed')).toBe('true')
  expect(api.returnLookup).toHaveBeenCalledWith('20', '4')
  const checkbox = wrapper.get<HTMLInputElement>('input[type="checkbox"]')
  expect(checkbox.element.checked).toBe(false)
  await wrapper.findAll('button').find(button => button.text() === '选中全部可归还雪具')!.trigger('click')
  expect(wrapper.findAll('input[type="checkbox"]')).toHaveLength(1)
  expect(checkbox.element.checked).toBe(true)
  expect(api.returns).not.toHaveBeenCalled()
})
it('AC-UC06 selects a cross-store contract without entering its identifier and retains failed return input', async () => {
  api.list.mockImplementation(async storeId => storeId === '3' ? [{
    contractId: '91', contractNumber: 'RC-91', customerId: '2', serviceStoreId: '3', sourceRequestId: '1',
    rentalStartDate: '2026-11-23', rentalDays: 1, amountDue: '100.00', status: 'ACTIVE', charges: [],
    items: [{ itemId: '92', equipmentId: '93', active: true, status: 'RENTED', dailyRateSnapshot: '100.00', checkedOutAt: '2026-11-23T08:00:00', returnedAt: null, returnStoreId: null }],
  }] : [])
  api.returnLookup.mockResolvedValue({ contractId: '91', status: 'ACTIVE', items: [{ itemId: '92', equipmentId: '93', status: 'RENTED', returnedAt: null }] })
  api.returns.mockRejectedValue(new Error('offline'))
  const wrapper = mount(RentalReturnView, sessionOptions(['3', '4']))
  await wrapper.get('select[aria-label="合同所属门店"]').setValue('3')
  await flushPromises()
  await wrapper.get('[data-test="contract-91"]').trigger('click')
  await wrapper.get('select[aria-label="实际接收门店"]').setValue('4')
  await flushPromises()
  expect(wrapper.find('input[inputmode="numeric"]').exists()).toBe(false)
  expect(api.returnLookup).toHaveBeenLastCalledWith('91', '4')
  await wrapper.get('input[type="checkbox"]').setValue(true)
  await wrapper.get('article select').setValue('FAULT')
  await wrapper.get('textarea').setValue('Real fault retained')
  await wrapper.findAll('button').at(-1)?.trigger('click')
  await flushPromises()
  expect(api.returns).toHaveBeenCalledWith('91', '4', [{ itemId: '92', outcome: 'FAULT', faultDescription: 'Real fault retained' }])
  expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('Real fault retained')
  expect((wrapper.get('input[type="checkbox"]').element as HTMLInputElement).checked).toBe(true)
  expect(wrapper.get('[role="alert"]').text()).toBe('操作失败：offline')
})

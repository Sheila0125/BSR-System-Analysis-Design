import { flushPromises, mount } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import RentalReturnView from './RentalReturnView.vue'
const api = vi.hoisted(() => ({ returnLookup: vi.fn(), returns: vi.fn() }))
vi.mock('./rentalApi', () => ({ rentalApi: api }))
it('AC-UC06 cross-store return failure retains selected item and fault description', async () => {
  api.returnLookup.mockResolvedValue({ contractId: '91', status: 'ACTIVE', items: [{ itemId: '92', equipmentId: '93', status: 'RENTED', returnedAt: null }] })
  api.returns.mockRejectedValue(new Error('offline'))
  const wrapper = mount(RentalReturnView)
  await wrapper.findAll('input')[0]?.setValue('4')
  await wrapper.findAll('input')[1]?.setValue('91')
  await wrapper.findAll('button')[0]?.trigger('click')
  await flushPromises()
  await wrapper.get('input[type="checkbox"]').setValue(true)
  await wrapper.get('select').setValue('FAULT')
  await wrapper.get('textarea').setValue('Real fault retained')
  await wrapper.findAll('button').at(-1)?.trigger('click')
  await flushPromises()
  expect(api.returns).toHaveBeenCalledWith('91', '4', [{ itemId: '92', outcome: 'FAULT', faultDescription: 'Real fault retained' }])
  expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('Real fault retained')
  expect((wrapper.get('input[type="checkbox"]').element as HTMLInputElement).checked).toBe(true)
  expect(wrapper.get('[role="alert"]').text()).toBe('操作失败：offline')
})

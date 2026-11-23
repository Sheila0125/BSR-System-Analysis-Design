import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import EquipmentPicker from './EquipmentPicker.vue'
const api = vi.hoisted(() => ({ loadAvailableEquipment: vi.fn() }))
vi.mock('./equipmentAvailabilityApi', () => api)
beforeEach(() => vi.clearAllMocks())
const item = { equipmentId: '9007199254740993', inventoryNumber: 'INV-PICK', equipmentName: 'Atomic Redster', equipmentCategory: 'SKI', skillLevel: 'INTERMEDIATE', homeStoreId: '3', currentStoreId: '3', status: 'AVAILABLE', currentDailyRate: '99.50' }
it('shows concise date-free labels and only offers categories still required by the request', async () => {
  api.loadAvailableEquipment.mockResolvedValue([
    { ...item, equipmentName: 'Atomic Redster 2026-10-25', inventoryNumber: 'TEST-20261025-SKI-12' },
    { ...item, equipmentId: '2', equipmentName: 'Smith Helmet', inventoryNumber: 'TEST-20261025-HELMET-03', equipmentCategory: 'HELMET' },
  ])
  const wrapper = mount(EquipmentPicker, {
    props: {
      storeId: '3',
      requirements: [{ equipmentCategory: 'SKI', quantity: 1 }],
      preferredSkillLevel: 'INTERMEDIATE',
    },
  })
  await flushPromises()

  const options = wrapper.findAll('option').map(option => option.text()).join(' ')
  expect(options).toContain('Atomic Redster')
  expect(options).toContain('SKI-12')
  expect(options).not.toContain('2026-10-25')
  expect(options).not.toContain('20261025')
  expect(wrapper.find('option[value="2"]').exists()).toBe(false)
  expect(wrapper.get('[aria-label="配货进度"]').text()).toContain('双板 0 / 1')
})
it('AC-UC04 emits observed string ID/rate without reserving; store change clears stale selection', async () => {
  api.loadAvailableEquipment.mockResolvedValue([item])
  const wrapper = mount(EquipmentPicker, { props: { storeId: '3' } })
  await flushPromises()
  await wrapper.get('select').setValue(item.equipmentId)
  await wrapper.findAll('button')[0]!.trigger('click')
  expect(wrapper.emitted('selection')?.at(-1)).toEqual([[item]])
  expect(api.loadAvailableEquipment).toHaveBeenCalledTimes(1)
  await wrapper.setProps({ storeId: '4' })
  expect(wrapper.find('option[value="9007199254740993"]').exists()).toBe(false)
  expect(wrapper.emitted('selection')?.at(-1)).toEqual([[]])
})
it('AC-UC04 failed refresh clears obsolete availability and selection', async () => {
  api.loadAvailableEquipment.mockResolvedValueOnce([item]).mockRejectedValueOnce(new Error('offline'))
  const wrapper = mount(EquipmentPicker, { props: { storeId: '3' } })
  await flushPromises()
  await wrapper.get('select').setValue(item.equipmentId)
  await wrapper.findAll('button')[0]!.trigger('click')
  await wrapper.findAll('button')[1]!.trigger('click')
  await flushPromises()
  expect(wrapper.get('[role="alert"]').text()).toBe('操作失败：offline')
  expect(wrapper.find('option[value="9007199254740993"]').exists()).toBe(false)
  expect(wrapper.emitted('selection')?.at(-1)).toEqual([[]])
})

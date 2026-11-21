import { sessionOptions } from '../../../tests/unit/sessionOptions'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import EquipmentAvailabilityView from './EquipmentAvailabilityView.vue'

const apiMocks = vi.hoisted(() => ({ loadAvailableEquipment: vi.fn() }))
vi.mock('./equipmentAvailabilityApi', () => apiMocks)

beforeEach(() => vi.clearAllMocks())

describe('AC-UC04/AC-BR13 authorized availability view', () => {
  it('queries one store and keeps selection local without a reservation request', async () => {
    apiMocks.loadAvailableEquipment.mockResolvedValue([{
      equipmentId: '9007199254740993', inventoryNumber: 'INV-42', equipmentName: 'Ski',
      equipmentCategory: 'SKI', skillLevel: 'INTERMEDIATE', homeStoreId: '3',
      currentStoreId: '3', status: 'AVAILABLE', currentDailyRate: '99.50',
    }])
    const wrapper = mount(EquipmentAvailabilityView, sessionOptions(['3']))
    const inputs = wrapper.findAll('select')
    await inputs[0]?.setValue('3')
    await inputs[1]?.setValue('SKI')
    await inputs[2]?.setValue('INTERMEDIATE')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(apiMocks.loadAvailableEquipment).toHaveBeenCalledWith('3', 'SKI', 'INTERMEDIATE')
    expect(wrapper.text()).toContain('99.50')
    await wrapper.get('input[type="checkbox"]').setValue(true)
    expect(wrapper.get('[aria-label="已选雪具"]').text()).toContain('INV-42')
    expect(wrapper.text()).toContain('不会锁定或预留')
    expect(apiMocks.loadAvailableEquipment).toHaveBeenCalledTimes(1)
  })

  it('clears stale results and selection on failed refresh while preserving filters', async () => {
    apiMocks.loadAvailableEquipment.mockResolvedValueOnce([{
      equipmentId: '1', inventoryNumber: 'INV-1', equipmentName: 'Ski',
      equipmentCategory: 'SKI', skillLevel: 'INTERMEDIATE', homeStoreId: '3',
      currentStoreId: '3', status: 'AVAILABLE', currentDailyRate: '90.00',
    }]).mockRejectedValueOnce(new Error('offline'))
    const wrapper = mount(EquipmentAvailabilityView, sessionOptions(['3']))
    await wrapper.findAll('select')[0]?.setValue('3')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    await wrapper.get('input[type="checkbox"]').setValue(true)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('检查连接')
    expect(wrapper.find('table').exists()).toBe(false)
    expect(wrapper.find('[aria-label="已选雪具"]').exists()).toBe(false)
    expect(wrapper.get('select[aria-label="门店编号"]').element).toHaveProperty('value', '3')
  })
})

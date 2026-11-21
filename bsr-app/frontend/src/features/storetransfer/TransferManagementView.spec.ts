import { sessionOptions } from '../../../tests/unit/sessionOptions'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import EquipmentPicker from '../equipment/EquipmentPicker.vue'
import TransferManagementView from './TransferManagementView.vue'

const api = vi.hoisted(() => ({ listTransfers: vi.fn(), createTransfer: vi.fn(), approve: vi.fn(), getStore: vi.fn(), createStore: vi.fn(), updateStore: vi.fn() }))
vi.mock('./storeTransferApi', () => ({ storeTransferApi: api }))
vi.mock('../equipment/equipmentAvailabilityApi', () => ({ loadAvailableEquipment: vi.fn(async () => []) }))
beforeEach(() => { vi.clearAllMocks(); api.listTransfers.mockResolvedValue([]); api.getStore.mockResolvedValue({ storeId: '11', storeName: 'North', address: null, openingTime: '09:00', closingTime: '18:00' }) })

it('AC-UC11/AC-BR27/AC-BR28 manager can create and separately approve a transfer', async () => {
  api.createTransfer.mockResolvedValue({ transferId: '7', transferNumber: 'TR-7', status: 'INITIATED' })
  api.approve.mockResolvedValue({ transferId: '7', transferNumber: 'TR-7', status: 'APPROVED' })
  const wrapper = mount(TransferManagementView, sessionOptions(['11', '12']))
  await wrapper.get('[aria-label="调出门店编号"]').setValue('11')
  await wrapper.get('[aria-label="调入门店编号"]').setValue('12')
  await flushPromises()
  wrapper.getComponent(EquipmentPicker).vm.$emit('selection', [{ equipmentId: '21' }, { equipmentId: '22' }])
  await wrapper.vm.$nextTick()
  await wrapper.get('[data-testid="create-transfer"]').trigger('click')
  await flushPromises()
  expect(api.createTransfer).toHaveBeenCalledWith('11', '12', 'TEMPORARY', ['21', '22'])
  expect(api.approve).not.toHaveBeenCalled()
  await wrapper.get('[data-testid="approve-7"]').trigger('click')
  await flushPromises()
  expect(api.approve).toHaveBeenCalledWith('7')
})

it('AC-BR29 retains equipment input and displays conflict for correction', async () => {
  api.createTransfer.mockRejectedValue(new Error('Equipment unavailable: 22'))
  const wrapper = mount(TransferManagementView, sessionOptions(['11', '12']))
  await wrapper.get('[aria-label="调出门店编号"]').setValue('11')
  await wrapper.get('[aria-label="调入门店编号"]').setValue('12')
  await flushPromises()
  wrapper.getComponent(EquipmentPicker).vm.$emit('selection', [{ equipmentId: '21' }, { equipmentId: '22' }])
  await wrapper.vm.$nextTick()
  await wrapper.get('[data-testid="create-transfer"]').trigger('click')
  await flushPromises()
  expect(wrapper.get('[role="alert"]').text()).toContain('22')
  expect(wrapper.get('[data-testid="create-transfer"]').attributes('disabled')).toBeUndefined()
})

it('AC-UC14 creates and updates a store without exposing status control', async () => {
  api.createStore.mockResolvedValue({ storeId: '13', storeName: 'West', address: null, openingTime: '09:00', closingTime: '18:00' })
  api.updateStore.mockResolvedValue({ storeId: '11', storeName: 'North 2', address: null, openingTime: '09:00', closingTime: '18:00' })
  const wrapper = mount(TransferManagementView, sessionOptions(['11', '12']))
  await wrapper.get('[aria-label="授权门店编号"]').setValue('11')
  await wrapper.get('[aria-label="门店编号"]').setValue('S-13')
  await wrapper.get('[aria-label="门店名称"]').setValue('West')
  await wrapper.get('[aria-label="营业开始时间"]').setValue('09:00')
  await wrapper.get('[aria-label="营业结束时间"]').setValue('18:00')
  await wrapper.get('[data-testid="create-store"]').trigger('click')
  await flushPromises()
  expect(api.createStore).toHaveBeenCalledWith('11', 'S-13', 'West', null, '09:00', '18:00')
  await wrapper.get('[aria-label="管理门店编号"]').setValue('11')
  await wrapper.get('[data-testid="load-store"]').trigger('click')
  await flushPromises()
  await wrapper.get('[aria-label="门店名称"]').setValue('North 2')
  await wrapper.get('[data-testid="update-store"]').trigger('click')
  await flushPromises()
  expect(api.updateStore).toHaveBeenCalledWith('11', 'North 2', null, '09:00', '18:00')
  expect(wrapper.find('[aria-label="Business status"]').exists()).toBe(false)
})

import { sessionOptions } from '../../../tests/unit/sessionOptions'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import RentalDeskView from './RentalDeskView.vue'
import EquipmentPicker from '../equipment/EquipmentPicker.vue'
const api = vi.hoisted(() => ({ list: vi.fn(), changes: vi.fn(), create: vi.fn(), revise: vi.fn(), read: vi.fn(), submit: vi.fn(), cancel: vi.fn(), issue: vi.fn(), transactions: vi.fn(), record: vi.fn() }))
vi.mock('./rentalApi', () => ({ rentalApi: api }))
const requestApi = vi.hoisted(() => ({ loadEmployeeRentalRequests: vi.fn() }))
vi.mock('../customerrequest/employeeRequestApi', () => requestApi)
vi.mock('../equipment/equipmentAvailabilityApi', () => ({ loadAvailableEquipment: vi.fn(async () => []) }))
vi.mock('../equipment/equipmentCatalogApi', () => ({ loadCatalog: vi.fn(async () => [
  { equipmentId: '4', inventoryNumber: 'INV-SKI-04', equipmentName: 'Atomic Redster 2026-10-25', equipmentCategory: 'SKI' },
]) }))
beforeEach(() => {
  vi.clearAllMocks()
  requestApi.loadEmployeeRentalRequests.mockResolvedValue([
    {
      requestId: '9007199254740993', requestNumber: 'RQ-99', customerId: '51', serviceStoreId: '3',
      rentalStartDate: '2026-12-20', rentalDays: 2, skillLevel: 'INTERMEDIATE', submittedAt: '2026-11-23T10:00:00',
      status: 'ACCEPTED', acceptedByEmployeeId: '9', supplements: [],
      items: [
        { equipmentCategory: 'SKI', quantity: 1, fittingNote: '板长 160 cm' },
        { equipmentCategory: 'SKI_BOOTS', quantity: 1, fittingNote: '鞋码 42' },
      ],
    },
    { requestId: '100', requestNumber: 'RQ-100', status: 'PENDING' },
  ])
})
it('shows accepted requests as scannable demand cards and passes the selected demand to equipment picking', async () => {
  const wrapper = mount(RentalDeskView, sessionOptions(['1', '3']))
  await wrapper.get('[aria-label="门店编号"]').setValue('3')
  await flushPromises()

  const card = wrapper.get('[aria-label="选择需求 RQ-99"]')
  expect(card.text()).toContain('2026-12-20 · 2 天')
  expect(card.text()).toContain('双板 × 1')
  expect(card.text()).toContain('双板雪鞋 × 1')
  await card.trigger('click')

  const detail = wrapper.get('[aria-label="已选需求详情"]')
  expect(detail.text()).toContain('顾客 51')
  expect(detail.text()).toContain('中级')
  expect(detail.text()).toContain('板长 160 cm')
  expect(detail.text()).toContain('鞋码 42')
  expect(wrapper.getComponent(EquipmentPicker).props('requirements')).toEqual([
    { equipmentCategory: 'SKI', quantity: 1 },
    { equipmentCategory: 'SKI_BOOTS', quantity: 1 },
  ])
  expect(wrapper.getComponent(EquipmentPicker).props('preferredSkillLevel')).toBe('INTERMEDIATE')
})
it('only enables contract creation after every requested category and quantity is matched', async () => {
  const wrapper = mount(RentalDeskView, sessionOptions(['1', '3']))
  await wrapper.get('[aria-label="门店编号"]').setValue('3')
  await flushPromises()
  await wrapper.get('[aria-label="选择需求 RQ-99"]').trigger('click')

  const create = () => wrapper.findAll('button').find(button => button.text() === '创建合同草稿')!
  wrapper.getComponent(EquipmentPicker).vm.$emit('selection', [
    { equipmentId: '1', equipmentCategory: 'SKI', skillLevel: 'INTERMEDIATE', currentDailyRate: '350.00' },
  ])
  await wrapper.vm.$nextTick()
  expect(create().attributes('disabled')).toBeDefined()

  wrapper.getComponent(EquipmentPicker).vm.$emit('selection', [
    { equipmentId: '1', equipmentCategory: 'SKI', skillLevel: 'INTERMEDIATE', currentDailyRate: '350.00' },
    { equipmentId: '2', equipmentCategory: 'SKI_BOOTS', skillLevel: 'INTERMEDIATE', currentDailyRate: '150.00' },
  ])
  await wrapper.vm.$nextTick()
  expect(create().attributes('disabled')).toBeUndefined()
})
it('only offers accepted requests and prevents creation with an unselected request', async () => {
  const wrapper = mount(RentalDeskView, sessionOptions(['1', '3']))
  await wrapper.get('[aria-label="门店编号"]').setValue('1')
  await flushPromises()
  expect(wrapper.findComponent(EquipmentPicker).exists()).toBe(false)
  await wrapper.findAll('button').find(b => b.text() === '创建合同草稿')?.trigger('click')
  await flushPromises()
  expect(api.create).not.toHaveBeenCalled()
  expect(wrapper.get('[aria-label="选择需求 RQ-99"]').text()).toContain('RQ-99')
  expect(wrapper.find('[aria-label="选择需求 RQ-100"]').exists()).toBe(false)
})
it('AC-UC04 shows the pricing basis and exact server amounts without repricing historical items', async () => {
  api.list.mockResolvedValue([{ contractId: '7', contractNumber: 'RC-7', status: 'ACTIVE', customerId: '2', rentalStartDate: '2026-12-20', rentalDays: 3, amountDue: '418.50', items: [{ itemId: '8', equipmentId: '9007199254740993', dailyRateSnapshot: '99.50', status: 'RETURNED', active: false, checkedOutAt: '2026-12-20', returnedAt: '2026-12-20' }], charges: [] }])
  api.changes.mockResolvedValue([])
  const wrapper = mount(RentalDeskView, sessionOptions(['1', '3']))
  await wrapper.get('[aria-label="门店编号"]').setValue('3')
  await wrapper.findAll('button').find(b => b.text() === '查询合同与变更需求')?.trigger('click')
  await flushPromises()
  await wrapper.get('select:has(option[value="7"])').setValue('7')
  const article = wrapper.get('article')
  expect(article.text()).toContain('合同应付合计 418.50')
  expect(article.text()).toContain('日租金快照 99.50 / 天')
  expect(article.text()).toContain('9007199254740993')
  expect(article.get('[aria-label="计费依据"]').text()).toContain('逐件日租金快照 × 约定整日天数')
  expect(article.get('[aria-label="计费依据"]').text()).toContain('普通提前归还不自动退款')
  expect(article.get('[aria-label="计费依据"]').text()).toContain('调整补缴或退款另列费用')
  expect(article.get('[aria-label="计费依据"]').text()).toContain('不是当前未付余额')
})
it('AC-UC04 create failure retains request and observed selection for explicit reconfirmation', async () => {
  api.create.mockRejectedValue(new Error('Price changed; reconfirm'))
  const wrapper = mount(RentalDeskView, sessionOptions(['1', '3']))
  await wrapper.get('[aria-label="门店编号"]').setValue('3')
  await flushPromises()
  await wrapper.get('[aria-label="选择需求 RQ-99"]').trigger('click')
  wrapper.getComponent(EquipmentPicker).vm.$emit('selection', [
    { equipmentId: '9', equipmentCategory: 'SKI', skillLevel: 'INTERMEDIATE', currentDailyRate: '99.50' },
    { equipmentId: '10', equipmentCategory: 'SKI_BOOTS', skillLevel: 'INTERMEDIATE', currentDailyRate: '59.50' },
  ])
  await wrapper.vm.$nextTick()
  await wrapper.findAll('button').find(b => b.text() === '创建合同草稿')?.trigger('click')
  await flushPromises()
  expect(api.create).toHaveBeenCalledWith('3', '9007199254740993', [
    { equipmentId: '9', expectedDailyRate: '99.50' },
    { equipmentId: '10', expectedDailyRate: '59.50' },
  ])
  expect(wrapper.get('[role="alert"]').text()).toContain('reconfirm')
  expect(wrapper.get('[aria-label="选择需求 RQ-99"]').attributes('aria-pressed')).toBe('true')
  expect(wrapper.findAll('button').find(b => b.text() === '创建合同草稿')?.attributes('disabled')).toBeUndefined()
})

it('AC-UC04 failed draft revision retains old item and replacement selection', async () => {
  const draft = { contractId: '7', contractNumber: 'RC-7', status: 'DRAFT', customerId: '2', rentalStartDate: '2026-12-20', rentalDays: 3, amountDue: '418.50', items: [{ itemId: '8', equipmentId: '4', dailyRateSnapshot: '99.50', status: 'PENDING_ISSUE', active: true, checkedOutAt: null, returnedAt: null }], charges: [] }
  api.list.mockResolvedValue([draft]); api.changes.mockResolvedValue([])
  api.revise.mockRejectedValue(new Error('Observed price changed'))
  const wrapper = mount(RentalDeskView, sessionOptions(['1', '3']))
  await wrapper.get('[aria-label="门店编号"]').setValue('3')
  await wrapper.findAll('button').find(b => b.text() === '查询合同与变更需求')?.trigger('click')
  await flushPromises()
  await wrapper.get('select:has(option[value="7"])').setValue('7')
  await flushPromises()
  expect(wrapper.get('article ul').text()).toContain('Atomic Redster · 双板 · INV-SKI-04')
  await wrapper.get('input[type="checkbox"]').setValue(true)
  wrapper.getComponent(EquipmentPicker).vm.$emit('selection', [{ equipmentId: '9', currentDailyRate: '120.00' }])
  await wrapper.vm.$nextTick()
  await wrapper.findAll('button').find(b => b.text().startsWith('替换所选草稿雪具'))?.trigger('click')
  await flushPromises()
  expect(api.revise).toHaveBeenCalledWith('7', ['8'], [{ equipmentId: '9', expectedDailyRate: '120.00' }])
  expect(wrapper.get('[role="alert"]').text()).toContain('Observed price changed')
  expect((wrapper.get('input[type="checkbox"]').element as HTMLInputElement).checked).toBe(true)
})

it('AC-UC04 switching contracts clears an observed replacement from the previous draft', async () => {
  const draft = (id: string) => ({ contractId: id, contractNumber: `RC-${id}`, status: 'DRAFT', customerId: '2', rentalStartDate: '2026-12-20', rentalDays: 3, amountDue: '418.50', items: [{ itemId: `item-${id}`, equipmentId: '4', dailyRateSnapshot: '99.50', status: 'PENDING_ISSUE', active: true, checkedOutAt: null, returnedAt: null }], charges: [] })
  api.list.mockResolvedValue([draft('7'), draft('8')]); api.changes.mockResolvedValue([])
  const wrapper = mount(RentalDeskView, sessionOptions(['1', '3']))
  await wrapper.get('[aria-label="门店编号"]').setValue('3')
  await wrapper.findAll('button').find(b => b.text() === '查询合同与变更需求')?.trigger('click')
  await flushPromises()
  await wrapper.get('select:has(option[value="7"])').setValue('7')
  wrapper.getComponent(EquipmentPicker).vm.$emit('selection', [{ equipmentId: '9', currentDailyRate: '120.00' }])
  await wrapper.vm.$nextTick()
  await wrapper.get('select:has(option[value="8"])').setValue('8')
  await wrapper.get('input[type="checkbox"]').setValue(true)
  expect(wrapper.findAll('button').find(b => b.text().startsWith('替换所选草稿雪具'))?.attributes('disabled')).toBeDefined()
})

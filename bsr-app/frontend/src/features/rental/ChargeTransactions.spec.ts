import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import ChargeTransactions from './ChargeTransactions.vue'
import type { Charge } from './rentalApi'

const api = vi.hoisted(() => ({ transactions: vi.fn(), record: vi.fn() }))
vi.mock('./rentalApi', () => ({ rentalApi: api }))
const charges: Charge[] = [
  { chargeId: '15', type: 'SUPPLEMENT', amount: '14.25', status: 'PENDING' },
  { chargeId: '16', type: 'REFUND', amount: '12.99', status: 'PENDING_REFUND' },
]
beforeEach(() => {
  vi.resetAllMocks()
  api.transactions.mockResolvedValue([])
})

it('does not pick between different outstanding fees and clears receipt details on switching', async () => {
  const wrapper = mount(ChargeTransactions, { props: { charges, disabled: false, canRecord: true } })
  expect(wrapper.find('form').exists()).toBe(false)
  expect(api.transactions).not.toHaveBeenCalled()
  await wrapper.get('select[aria-label="费用"]').setValue('15')
  await flushPromises()
  await wrapper.get('input[aria-label="外部交易编号"]').setValue('RECEIPT-A')
  await wrapper.get('input[aria-label="金额"]').setValue('5.12')
  await wrapper.get('select[aria-label="费用"]').setValue('16')
  await flushPromises()
  expect(wrapper.get<HTMLInputElement>('input[aria-label="外部交易编号"]').element.value).toBe('')
  expect(wrapper.get<HTMLInputElement>('input[aria-label="金额"]').element.value).toBe('12.99')
  expect(wrapper.get('legend').text()).toContain('退款')
})

it('uses the selected fee to record an actual refund and preserves edited inputs if recording fails', async () => {
  api.record.mockRejectedValueOnce(new Error('连接已断开'))
  const wrapper = mount(ChargeTransactions, { props: { charges: [charges[1]!], disabled: false, canRecord: true } })
  await flushPromises()
  await wrapper.get('input[aria-label="外部交易编号"]').setValue('REFUND-48')
  await wrapper.get('input[aria-label="金额"]').setValue('8.20')
  await wrapper.get('form select').setValue('SUCCEEDED')
  await wrapper.get('input[type="datetime-local"]').setValue('2026-11-23T08:00:00')
  await wrapper.get('form').trigger('submit')
  await flushPromises()

  expect(api.record).toHaveBeenCalledWith('16', {
    type: 'REFUND', externalId: 'REFUND-48', amount: '8.20', status: 'SUCCEEDED',
    processedAt: new Date(2026, 10, 23, 8).toISOString().replace('.000Z', 'Z'),
  })
  expect(wrapper.get('[role="alert"]').text()).toContain('连接已断开')
  expect(wrapper.get<HTMLInputElement>('input[aria-label="外部交易编号"]').element.value).toBe('REFUND-48')
  expect(wrapper.get<HTMLInputElement>('input[aria-label="金额"]').element.value).toBe('8.20')
  expect(wrapper.emitted('recorded')).toBeUndefined()
})

it('shows the system-generated simulated identifier for a settled fee without asking for another receipt', async () => {
  api.transactions.mockResolvedValueOnce([{ transactionId: '4', externalId: 'SIM-PAID-4', status: 'SUCCEEDED', source: 'SIMULATED', amount: '14.25' }])
  const wrapper = mount(ChargeTransactions, { props: { charges: [{ ...charges[0]!, status: 'SETTLED' }], disabled: false, canRecord: true } })
  await flushPromises()
  expect(wrapper.get('[aria-label="交易记录"]').text()).toContain('SIM-PAID-4')
  expect(wrapper.find('form').exists()).toBe(false)
})

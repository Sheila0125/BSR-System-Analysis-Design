import { sessionOptions } from '../../../tests/unit/sessionOptions'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import TransferHandoffView from './TransferHandoffView.vue'

const api = vi.hoisted(() => ({ listTransfers: vi.fn(), dispatch: vi.fn(), receive: vi.fn(), reportException: vi.fn() }))
vi.mock('./storeTransferApi', () => ({ storeTransferApi: api }))
const transfer = { transferId: '7', transferNumber: 'TR-7', status: 'APPROVED', sourceStoreId: '11', destinationStoreId: '12', items: [{ itemId: '31', equipmentId: '21', dispatchResult: 'PENDING', receiptResult: 'PENDING', exceptionNote: null }] }
beforeEach(() => { vi.clearAllMocks(); api.listTransfers.mockResolvedValue([transfer]) })

it('AC-BR29/AC-BR31 submits one source-store item and displays only that result', async () => {
  api.dispatch.mockResolvedValue({ ...transfer, status: 'IN_TRANSIT', items: [{ ...transfer.items[0], dispatchResult: 'DISPATCHED' }] })
  const wrapper = mount(TransferHandoffView, sessionOptions(['11', '12']))
  await wrapper.get('[aria-label="交接门店编号"]').setValue('11')
  await wrapper.get('[data-testid="load-handoffs"]').trigger('click')
  await flushPromises()
  await wrapper.get('[data-testid="dispatch-31"]').trigger('click')
  await flushPromises()
  expect(api.dispatch).toHaveBeenCalledWith('7', '31')
  expect(wrapper.text()).toContain('已发出')
})

it('AC-BR30/AC-BR31 receives at destination and keeps exception note after a failed action', async () => {
  api.listTransfers.mockResolvedValue([{ ...transfer, items: [{ ...transfer.items[0], dispatchResult: 'DISPATCHED' }] }])
  api.reportException.mockRejectedValue(new Error('Receipt conflict'))
  api.receive.mockResolvedValue({ ...transfer, status: 'COMPLETED', items: [{ ...transfer.items[0], dispatchResult: 'DISPATCHED', receiptResult: 'RECEIVED' }] })
  const wrapper = mount(TransferHandoffView, sessionOptions(['11', '12']))
  await wrapper.get('[aria-label="交接门店编号"]').setValue('12')
  await wrapper.get('[data-testid="load-handoffs"]').trigger('click')
  await flushPromises()
  await wrapper.get('[aria-label="异常说明：31"]').setValue('Serial not visible')
  await wrapper.get('[data-testid="exception-31"]').trigger('click')
  await flushPromises()
  expect(api.reportException).toHaveBeenCalledWith('7', '31', 'RECEIPT', 'Serial not visible')
  expect(wrapper.get('[role="alert"]').text()).toContain('Receipt conflict')
  expect((wrapper.get('[aria-label="异常说明：31"]').element as HTMLInputElement).value).toBe('Serial not visible')
  await wrapper.get('[data-testid="receive-31"]').trigger('click')
  await flushPromises()
  expect(api.receive).toHaveBeenCalledWith('7', '31')
})

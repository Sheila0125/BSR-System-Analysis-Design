import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import EquipmentCatalogView from './EquipmentCatalogView.vue'
import { uiText } from '../../shared/presentation/zh'

afterEach(() => { vi.unstubAllGlobals() })
it('labels the tariff goggles category in Chinese', () => {
  expect(uiText('GOGGLES')).toBe('雪镜')
})
it('queries non-available archives and lets only managers edit prices without lifecycle controls', async () => {
  const calls: { url: string; body?: string }[] = []
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    calls.push({ url, body: init?.body as string | undefined })
    if (url === '/api/auth/me') return { ok: true, json: async () => ({ identityType: 'EMPLOYEE', grants: [{ role: 'MANAGER', storeId: '1' }] }) }
    if (url === '/api/pricing') return { ok: true, json: async () => ({ currency: 'NOK', dailyRates: { SKI: '350.00' } }) }
    return { ok: true, json: async () => [{ equipmentId: '7', inventoryNumber: 'SK-7', equipmentName: '测试双板', equipmentCategory: 'SKI', skillLevel: 'BEGINNER', status: 'WAITING_REPAIR', currentDailyRate: '99.50', currentStoreId: '1', homeStoreId: '1', revision: '0' }] }
  }))
  const wrapper = mount(EquipmentCatalogView)
  await flushPromises()
  await wrapper.get('[aria-label="装备状态"]').setValue('WAITING_REPAIR')
  await wrapper.get('[data-testid="catalog-search"]').trigger('submit')
  await flushPromises()
  expect(calls.some(call => call.url.includes('status=WAITING_REPAIR'))).toBe(true)
  expect(wrapper.text()).toContain('SK-7')
  expect(wrapper.text()).toContain('99.50')
  await wrapper.get('[data-testid="edit-7"]').trigger('click')
  expect(wrapper.get('[aria-label="当前日租金"]').element).toHaveProperty('value', '99.50')
  expect(wrapper.find('[aria-label="修改装备状态"]').exists()).toBe(false)
})

it('does not offer archive writes to an employee', async () => {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => ({ ok: true, json: async () => url === '/api/auth/me'
    ? { identityType: 'EMPLOYEE', grants: [{ role: 'EMPLOYEE', storeId: '1' }] }
    : url === '/api/pricing' ? { currency: 'NOK', dailyRates: {} } : [] })))
  const wrapper = mount(EquipmentCatalogView)
  await flushPromises()
  expect(wrapper.find('[data-testid="new-archive"]').exists()).toBe(false)
})

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CustomerVerificationView from './CustomerVerificationView.vue'

const apiMocks = vi.hoisted(() => ({ findCustomer: vi.fn(), reviewCustomerMeasurements: vi.fn() }))
vi.mock('./customerVerificationApi', () => apiMocks)

beforeEach(() => vi.clearAllMocks())

describe('AC-BR06 employee customer verification', () => {
  it('AC-UC01 saves a reviewed measurement and keeps entered values on failure', async () => {
    const profile = {
      customerId: '9007199254740993', customerNumber: 'C-42', fullName: '顾客',
      email: 'one@test.invalid', phone: '+47000000001', heightCm: '170.00',
      weightKg: '70.00', bootSize: '42.0', heightEstimated: true,
      weightEstimated: false, bootSizeEstimated: false,
    }
    apiMocks.findCustomer.mockResolvedValue(profile)
    apiMocks.reviewCustomerMeasurements.mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ ...profile, heightCm: '171.25', heightEstimated: false })
    const wrapper = mount(CustomerVerificationView)
    await wrapper.findAll('input')[0]?.setValue('3')
    await wrapper.findAll('input')[1]?.setValue('C-42')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const review = wrapper.findAll('form')[1]!
    await review.findAll('input')[0]?.setValue('171.25')
    await review.findAll('input')[1]?.setValue(false)
    await review.trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('已保留您的输入')
    expect((review.findAll('input')[0]?.element as HTMLInputElement).value).toBe('171.25')

    await review.trigger('submit')
    await flushPromises()
    expect(apiMocks.reviewCustomerMeasurements).toHaveBeenCalledWith('3', profile, expect.objectContaining({
      heightCm: '171.25', heightEstimated: false,
    }))
    expect(wrapper.get('[role="status"]').text()).toContain('已保存')
    expect(wrapper.text()).toContain('171.25')
  })
  it('searches by one unique identifier and shows estimated measurements for review', async () => {
    apiMocks.findCustomer.mockResolvedValue({
      customerId: '9007199254740993', customerNumber: 'C-42', fullName: 'Same Name',
      email: 'one@test.invalid', phone: '+47000000001',
      heightCm: '170.25', weightKg: null, bootSize: '42.5',
      heightEstimated: true, weightEstimated: false, bootSizeEstimated: false,
    })
    const wrapper = mount(CustomerVerificationView)
    const inputs = wrapper.findAll('input')
    await inputs[0]?.setValue('3')
    await inputs[1]?.setValue('C-42')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(apiMocks.findCustomer).toHaveBeenCalledWith('3', 'customerNumber', 'C-42')
    expect(wrapper.text()).toContain('9007199254740993')
    expect(wrapper.text()).toContain('估计值，请与顾客核对')
  })

  it('retains the identifier and clears stale results after a failed lookup', async () => {
    apiMocks.findCustomer.mockResolvedValueOnce({
      customerId: '1', customerNumber: 'C-1', fullName: 'Same Name',
      email: 'one@test.invalid', phone: '1', heightCm: null, weightKg: null,
      bootSize: null, heightEstimated: false, weightEstimated: false,
      bootSizeEstimated: false,
    }).mockRejectedValueOnce(new Error('offline'))
    const wrapper = mount(CustomerVerificationView)
    const inputs = wrapper.findAll('input')
    await inputs[0]?.setValue('3')
    await inputs[1]?.setValue('C-1')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('检查连接')
    expect(wrapper.find('.verification-result').exists()).toBe(false)
    expect((inputs[1]?.element as HTMLInputElement).value).toBe('C-1')
  })
})

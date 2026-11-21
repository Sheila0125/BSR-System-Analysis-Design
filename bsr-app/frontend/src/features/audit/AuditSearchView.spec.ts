import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AuditSearchView from './AuditSearchView.vue'

const auditMocks = vi.hoisted(() => ({ findAuditEvents: vi.fn() }))

vi.mock('./auditApi', () => auditMocks)

beforeEach(() => vi.clearAllMocks())

describe('AC-BR40 AuditSearchView', () => {
  it('renders authorized object history returned newest first by the server', async () => {
    auditMocks.findAuditEvents.mockResolvedValue([{
      auditEventId: '91', actorAccountId: '8', objectType: 'ROLE_GRANT', objectId: '71',
      actionCode: 'ROLE_GRANT_CREATED', occurredAt: '2026-09-29T08:00:00',
      resultCode: 'SUCCESS', detailSummary: 'role=MANAGER;storeId=3',
    }])
    const wrapper = mount(AuditSearchView)
    const inputs = wrapper.findAll('input')
    await inputs[0]?.setValue('71')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(auditMocks.findAuditEvents).toHaveBeenCalledWith('ROLE_GRANT', '71')
    expect(wrapper.text()).toContain('ROLE_GRANT_CREATED')
    expect(wrapper.text()).toContain('role=MANAGER;storeId=3')
  })

  it('rejects malformed object IDs before sending a request', async () => {
    const wrapper = mount(AuditSearchView)
    const inputs = wrapper.findAll('input')
    await inputs[0]?.setValue('0')
    await wrapper.find('form').trigger('submit')

    expect(auditMocks.findAuditEvents).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('正整数')
  })
})

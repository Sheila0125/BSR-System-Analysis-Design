import { afterEach, describe, expect, it, vi } from 'vitest'
import { findAuditEvents } from './auditApi'

afterEach(() => vi.unstubAllGlobals())

describe('AC-BR40 Web object-scoped audit query', () => {
  it('queries one encoded object and preserves decimal identifiers as strings', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify([{
      auditEventId: '9007199254740993', actorAccountId: '8', objectType: 'ROLE_GRANT',
      objectId: '71', actionCode: 'ROLE_GRANT_CREATED', occurredAt: '2026-09-29T08:00:00',
      resultCode: 'SUCCESS', detailSummary: 'role=MANAGER;storeId=3',
    }]), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    const events = await findAuditEvents('ROLE_GRANT', '71')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/audit-events?objectType=ROLE_GRANT&objectId=71',
      expect.objectContaining({ credentials: 'same-origin' }),
    )
    expect(events[0]?.auditEventId).toBe('9007199254740993')
  })
})

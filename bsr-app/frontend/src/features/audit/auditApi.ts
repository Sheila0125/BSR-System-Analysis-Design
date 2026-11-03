import { ApiError, type ApiProblem } from '../../shared/http/apiClient'

export interface AuditEvent {
  auditEventId: string
  actorAccountId: string
  objectType: string
  objectId: string
  actionCode: string
  occurredAt: string
  resultCode: string
  detailSummary: string | null
}

export async function findAuditEvents(objectType: string, objectId: string): Promise<AuditEvent[]> {
  const query = new URLSearchParams({ objectType, objectId })
  const response = await fetch(`/api/audit-events?${query.toString()}`, {
    credentials: 'same-origin',
    headers: { Accept: 'application/json, application/problem+json' },
  })
  if (!response.ok) {
    throw new ApiError((await response.json()) as ApiProblem)
  }
  return (await response.json()) as AuditEvent[]
}

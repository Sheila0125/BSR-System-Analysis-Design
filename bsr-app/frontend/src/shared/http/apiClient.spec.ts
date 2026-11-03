import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, isEntityId, isMoneyString, requestJson } from './apiClient'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('shared HTTP contract', () => {
  it('S01 keeps valid form input when the server reports field errors', async () => {
    const form = Object.freeze({ customerNumber: 'C-2048', rentalDays: '3' })
    const before = { ...form }
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            type: 'urn:bsr:problem:validation_failed',
            title: 'Bad Request',
            status: 400,
            detail: 'Request validation failed',
            code: 'VALIDATION_FAILED',
            fieldErrors: { rentalDays: 'must be at least 4' },
            traceId: 'trace-1',
          }),
          {
            status: 400,
            headers: { 'Content-Type': 'application/problem+json' },
          },
        ),
      ),
    )

    const error = await requestJson('/api/example', { method: 'POST', body: form }).catch(
      (reason: unknown) => reason,
    )

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).problem.fieldErrors).toEqual({
      rentalDays: 'must be at least 4',
    })
    expect(form).toEqual(before)
  })

  it('S01 treats large IDs and money as decimal strings', () => {
    expect(isEntityId('9007199254740993')).toBe(true)
    expect(isEntityId('9007199254740993.0')).toBe(false)
    expect(isMoneyString('9999999999.99')).toBe(true)
    expect(isMoneyString('99.5')).toBe(false)
  })
})

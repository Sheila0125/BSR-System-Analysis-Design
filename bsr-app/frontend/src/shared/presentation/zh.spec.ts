import { describe, expect, it } from 'vitest'
import { problemText, uiText } from './zh'

describe('Chinese presentation preserves business codes', () => {
  it('shows Chinese labels and keeps original code values untouched', () => {
    const status = 'PENDING_CHARGE'
    expect(uiText(status)).toBe('待确认费用')
    expect(status).toBe('PENDING_CHARGE')
    expect(uiText('42001')).toBe('42001')
  })
  it('gives a Chinese authentication error and preserves original server evidence', () => {
    const problem = { code: 'BAD_CREDENTIALS', status: 401, detail: 'The login name or password is incorrect.' }
    expect(problemText(problem)).toBe('账号或密码不正确，请重新输入。')
    expect(problem.detail).toBe('The login name or password is incorrect.')
    expect(problemText({ code: 'NEW_ERROR', status: 409, detail: 'Unsupported state' })).toContain('冲突')
  })
})

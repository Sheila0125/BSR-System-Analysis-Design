import { describe, expect, it } from 'vitest';
import { normalizeApiOrigin } from '../shared/network/apiOrigin';
import { toNetworkFailure } from '../shared/network/networkFailure';
import { amountString, identifierString } from '../shared/network/transportValues';

describe('mini program network foundation', () => {
  it('keeps identifiers and money as exact strings beyond JavaScript safe integers', () => {
    expect(identifierString('9007199254740993')).toBe('9007199254740993');
    expect(amountString('9007199254740993.25')).toBe('9007199254740993.25');
    expect(() => identifierString('9007199254740993.0')).toThrow();
    expect(() => amountString('12.5')).toThrow();
  });

  it('accepts only a credential-free HTTPS origin without paths', () => {
    expect(normalizeApiOrigin('https://api.example.invalid')).toBe('https://api.example.invalid');
    expect(() => normalizeApiOrigin('http://api.example.invalid')).toThrow(/HTTPS/);
    expect(() => normalizeApiOrigin('https://user:pass@example.invalid')).toThrow(/credentials/);
    expect(() => normalizeApiOrigin('https://api.example.invalid/v1')).toThrow(/path/);
    expect(() => normalizeApiOrigin('https://api.example.invalid:99999')).toThrow(/65535/);
  });

  it('returns the original form input when adapting a transport failure', () => {
    const input = { customerNumber: 'C-10001', note: 'retain this draft' };
    const failure = toNetworkFailure(input, { kind: 'timeout', message: 'Timed out' });

    expect(failure.input).toBe(input);
    expect(failure.kind).toBe('timeout');
  });

  it('allows only the explicitly selected HTTPS IPv4 origin for a classroom demo', () => {
    const demo = 'https://49.233.93.180';
    expect(() => normalizeApiOrigin(demo)).toThrow();
    expect(normalizeApiOrigin(demo, demo)).toBe(demo);
    expect(() => normalizeApiOrigin('https://49.233.93.181', demo)).toThrow();
    expect(() => normalizeApiOrigin('https://256.1.2.3', 'https://256.1.2.3')).toThrow();
    expect(() => normalizeApiOrigin('http://49.233.93.180', 'http://49.233.93.180')).toThrow(/HTTPS/);
    expect(() => normalizeApiOrigin(`${demo}/api`, `${demo}/api`)).toThrow(/path/);
  });
});

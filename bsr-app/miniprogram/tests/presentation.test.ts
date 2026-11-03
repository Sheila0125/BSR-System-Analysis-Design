import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { describe, expect, it } from 'vitest';

interface Presentation { label(value: unknown): string; message(value: string): string }
function presentation(): Presentation {
  const module = { exports: {} };
  runInNewContext(readFileSync(new URL('../shared/ui/presentation.wxs', import.meta.url), 'utf8'), { module });
  return module.exports as Presentation;
}

describe('native customer presentation', () => {
  it('translates display codes without modifying source business values', () => {
    const data = { status: 'PENDING_CONFIRMATION', type: 'PARTIAL_RETURN' };
    const ui = presentation();
    expect(ui.label(data.status)).toBe('待确认');
    expect(ui.label(data.type)).toBe('部分归还');
    expect(ui.label('NEEDS_INFO')).toBe('待补充资料');
    expect(ui.label('PENDING_SETTLEMENT')).toBe('待结算');
    expect(ui.label('BEGINNER')).toBe('初级');
    expect(data).toEqual({ status: 'PENDING_CONFIRMATION', type: 'PARTIAL_RETURN' });
  });
  it('preserves unknown codes, exact large identifiers and amount strings', () => {
    const ui = presentation();
    expect(ui.label('FUTURE_STATUS')).toBe('FUTURE_STATUS');
    expect(ui.label('toString')).toBe('toString');
    expect(ui.label('9007199254740993')).toBe('9007199254740993');
    expect(ui.label('9007199254740993.25')).toBe('9007199254740993.25');
    expect(ui.label(null)).toBe('—');
    expect(ui.label('')).toBe('—');
  });
  it('localizes familiar setup errors but retains unknown server detail', () => {
    const ui = presentation();
    expect(ui.message('The secure BSR API origin is not configured.')).toBe('服务连接尚未配置，请联系管理员。');
    expect(ui.message('请刷新后重试')).toBe('请刷新后重试');
    expect(ui.message('Inventory conflict #91')).toBe('Inventory conflict #91');
    expect(ui.message('constructor')).toBe('constructor');
  });
});

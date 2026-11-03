import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { runInNewContext } from 'node:vm';
import { afterAll, expect, it } from 'vitest';

const root = resolve(import.meta.dirname, '..');
function build(origin = '', demoOrigin = '') {
  return spawnSync(process.execPath, ['scripts/build.mjs'], {
    cwd: root,
    env: { ...process.env, BSR_API_ORIGIN: origin, BSR_DEMO_IP_ORIGIN: demoOrigin },
    encoding: 'utf8',
    timeout: 30_000,
  });
}
function readApp() {
  const source = readFileSync(resolve(root, 'dist/app.js'), 'utf8');
  let app: { globalData?: { apiOrigin?: string } } | undefined;
  runInNewContext(source, { App: (value: typeof app) => { app = value; } });
  return app;
}

afterAll(() => { expect(build().status).toBe(0); }, 30_000);

it('injects the configured HTTPS origin into the actual built App registration', () => {
  expect(build('  https://api.example.invalid/  ').status).toBe(0);
  expect(readApp()?.globalData?.apiOrigin).toBe('https://api.example.invalid');
}, 30_000);

it('rejects invalid deployment origins before replacing the existing compiled output', () => {
  const previous = readFileSync(resolve(root, 'dist/app.js'), 'utf8');
  for (const origin of ['http://api.example.invalid', 'https://user:secret@api.example.invalid',
    'https://api.example.invalid/api', 'https://api.example.invalid?q=1']) {
    expect(build(origin).status).not.toBe(0);
    expect(readFileSync(resolve(root, 'dist/app.js'), 'utf8')).toBe(previous);
  }
}, 30_000);

it('keeps unconfigured local preview free of a fabricated cloud address', () => {
  expect(build().status).toBe(0);
  expect(readApp()?.globalData?.apiOrigin).toBeUndefined();
}, 30_000);

it('builds an explicit IP demo whose actual runtime accepts only that IP', () => {
  const demo = 'https://49.233.93.180';
  expect(build('', demo).status).toBe(0);
  expect(readApp()?.globalData?.apiOrigin).toBe(demo);
  const runtimeExports: { normalizeApiOrigin?: (origin: string) => string } = {};
  runInNewContext(readFileSync(resolve(root, 'dist/shared/network/apiOrigin.js'), 'utf8'),
    { exports: runtimeExports });
  expect(runtimeExports.normalizeApiOrigin?.(demo)).toBe(demo);
  expect(() => runtimeExports.normalizeApiOrigin?.('https://49.233.93.181')).toThrow();
  // A subsequent ordinary build must not retain the demo exception.
  expect(build().status).toBe(0);
  const strictExports: typeof runtimeExports = {};
  runInNewContext(readFileSync(resolve(root, 'dist/shared/network/apiOrigin.js'), 'utf8'),
    { exports: strictExports });
  expect(() => strictExports.normalizeApiOrigin?.(demo)).toThrow();
}, 30_000);

it('rejects ambiguous or invalid demo builds before replacing usable output', () => {
  const previous = readFileSync(resolve(root, 'dist/app.js'), 'utf8');
  for (const [origin, demo] of [
    ['https://api.example.invalid', 'https://49.233.93.180'],
    ['', 'https://api.example.invalid'],
    ['', 'https://256.1.2.3'],
    ['', 'http://49.233.93.180'],
  ]) {
    expect(build(origin, demo).status).not.toBe(0);
    expect(readFileSync(resolve(root, 'dist/app.js'), 'utf8')).toBe(previous);
  }
}, 30_000);

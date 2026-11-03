import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { missingPageFiles, validatePageFiles } from '../scripts/project-layout.mjs';

const root = resolve(import.meta.dirname, '..');
const manifest = JSON.parse(readFileSync(resolve(root, 'app.json'), 'utf8')) as {
  pages: string[];
};

const expectedPages = [
  'pages/identity/login',
  'pages/customerrequest/home',
  'pages/customerrequest/profile',
  'pages/customerrequest/create',
  'pages/customerrequest/detail',
  'pages/rental/list',
  'pages/rental/detail',
  'pages/rental/confirm',
  'pages/rental/change',
  'pages/rental/invoice',
];

describe('mini program project layout', () => {
  it('registers the original pages and approved invoice page exactly once', () => {
    expect(manifest.pages).toEqual(expectedPages);
    expect(new Set(manifest.pages).size).toBe(10);
    expect(() => validatePageFiles(root, manifest.pages)).not.toThrow();
  });

  it('reports every missing companion file instead of silently building', () => {
    const missing = missingPageFiles(
      root,
      ['pages/identity/login'],
      (path) => !path.endsWith('.wxml'),
    );
    expect(missing).toEqual(['pages/identity/login.wxml']);
  });

  it('accepts an absent AppID or a real WeChat AppID configured by the owner', () => {
    const project = JSON.parse(readFileSync(resolve(root, 'project.config.json'), 'utf8')) as {
      appid?: string;
    };
    if (project.appid !== undefined) {
      expect(project.appid).toMatch(/^wx[0-9a-f]{16}$/);
    }
  });
});

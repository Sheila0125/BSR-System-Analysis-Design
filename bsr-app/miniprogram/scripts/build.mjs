import { cpSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { validatePageFiles } from './project-layout.mjs';
import { normalizeApiOrigin } from '../shared/network/apiOrigin.ts';

const root = resolve(import.meta.dirname, '..');
const output = resolve(root, 'dist');
const manifest = JSON.parse(readFileSync(resolve(root, 'app.json'), 'utf8'));
const typescriptCompiler = resolve(root, 'node_modules/typescript/bin/tsc');
const configuredOrigin = process.env.BSR_API_ORIGIN?.trim();
const demoIpOrigin = process.env.BSR_DEMO_IP_ORIGIN?.trim();
if (configuredOrigin && demoIpOrigin) {
  throw new Error('Configure BSR_API_ORIGIN or BSR_DEMO_IP_ORIGIN, not both.');
}
if (demoIpOrigin && !/^https:\/\/(?:\d{1,3}\.){3}\d{1,3}(?::[1-9]\d{0,4})?\/?$/.test(demoIpOrigin)) {
  throw new Error('BSR_DEMO_IP_ORIGIN must be an explicit HTTPS IPv4 origin.');
}
// Validate before replacing an existing usable build. No default cloud address.
const selectedOrigin = demoIpOrigin || configuredOrigin;
const apiOrigin = selectedOrigin ? normalizeApiOrigin(selectedOrigin, demoIpOrigin) : undefined;

validatePageFiles(root, manifest.pages);
rmSync(output, { recursive: true, force: true });

const compiler = spawnSync(
  process.execPath,
  [typescriptCompiler, '-p', 'tsconfig.build.json'],
  { cwd: root, stdio: 'inherit' },
);
if (compiler.error) {
  throw compiler.error;
}
if (compiler.status !== 0) {
  process.exit(compiler.status ?? 1);
}

const applicationFile = resolve(output, 'app.js');
writeFileSync(applicationFile,
  `const BSR_API_ORIGIN = ${JSON.stringify(apiOrigin) ?? 'undefined'};\n${readFileSync(applicationFile, 'utf8')}`);

const originValidatorFile = resolve(output, 'shared/network/apiOrigin.js');
writeFileSync(originValidatorFile,
  `const BSR_DEMO_IP_ORIGIN = ${JSON.stringify(demoIpOrigin ? apiOrigin : undefined) ?? 'undefined'};\n` +
  readFileSync(originValidatorFile, 'utf8'));

for (const relative of ['app.json', 'app.wxss', 'sitemap.json']) {
  cpSync(resolve(root, relative), resolve(output, relative));
}
mkdirSync(resolve(output, 'shared/ui'), { recursive: true });
cpSync(resolve(root, 'shared/ui/presentation.wxs'), resolve(output, 'shared/ui/presentation.wxs'));
for (const page of manifest.pages) {
  for (const extension of ['.json', '.wxml', '.wxss']) {
    const destination = resolve(output, `${page}${extension}`);
    mkdirSync(dirname(destination), { recursive: true });
    cpSync(resolve(root, `${page}${extension}`), destination);
  }
}

validatePageFiles(output, manifest.pages, ['.js', '.json', '.wxml', '.wxss']);
console.log(`Built ${manifest.pages.length} registered mini program pages.`);

import { existsSync } from 'node:fs';
import { resolve } from 'node:path';

export const REQUIRED_PAGE_EXTENSIONS = ['.ts', '.json', '.wxml', '.wxss'];

export function missingPageFiles(
  root,
  pages,
  exists = existsSync,
  extensions = REQUIRED_PAGE_EXTENSIONS,
) {
  return pages.flatMap((page) =>
    extensions.map((extension) => `${page}${extension}`)
      .filter((relative) => !exists(resolve(root, relative))),
  );
}

export function validatePageFiles(root, pages, extensions = REQUIRED_PAGE_EXTENSIONS) {
  const missing = missingPageFiles(root, pages, existsSync, extensions);
  if (missing.length > 0) {
    throw new Error(`Missing registered page files: ${missing.join(', ')}`);
  }
}

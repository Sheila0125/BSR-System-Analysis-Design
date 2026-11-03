export const REQUIRED_PAGE_EXTENSIONS: readonly string[];
export function missingPageFiles(
  root: string,
  pages: readonly string[],
  exists?: (path: string) => boolean,
  extensions?: readonly string[],
): string[];
export function validatePageFiles(
  root: string,
  pages: readonly string[],
  extensions?: readonly string[],
): void;

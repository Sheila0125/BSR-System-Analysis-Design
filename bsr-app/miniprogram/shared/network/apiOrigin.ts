declare const BSR_DEMO_IP_ORIGIN: string | undefined;

export function normalizeApiOrigin(configured: string, demoIpOrigin?: string): string {
  const trimmed = configured.trim();
  if (trimmed.length === 0) {
    throw new Error('The mini program API origin is not configured.');
  }

  if (!trimmed.startsWith('https://')) {
    throw new Error('The mini program API origin must use HTTPS.');
  }
  const authority = trimmed.slice('https://'.length).replace(/\/$/, '');
  if (authority.includes('@') || authority.includes('?') || authority.includes('#')) {
    throw new Error('The mini program API origin cannot contain credentials, query, or fragment data.');
  }
  if (authority.includes('/')) {
    throw new Error('The mini program API origin cannot contain an API path.');
  }
  const match = authority.match(
    /^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}(?::([1-9]\d{0,4}))?$/i,
  );
  // The classroom build permits exactly its selected HTTPS IPv4 origin.
  // Ordinary builds have no injected exception and retain DNS validation.
  const selectedDemoOrigin = demoIpOrigin ??
    (typeof BSR_DEMO_IP_ORIGIN === 'undefined' ? undefined : BSR_DEMO_IP_ORIGIN);
  const ipv4 = authority.match(/^((?:\d{1,3}\.){3}\d{1,3})(?::([1-9]\d{0,4}))?$/);
  const isSelectedDemo = selectedDemoOrigin?.trim().replace(/\/$/, '') === `https://${authority}` &&
    ipv4 !== null && ipv4[1]!.split('.').every((part) =>
      Number(part) <= 255 && String(Number(part)) === part);
  if (!match && !isSelectedDemo) {
    throw new Error('The mini program API origin must contain a valid DNS host and optional port.');
  }
  const port = match?.[1] ?? ipv4?.[2];
  if (port !== undefined && Number(port) > 65_535) {
    throw new Error('The mini program API origin port must be between 1 and 65535.');
  }
  return `https://${authority}`;
}

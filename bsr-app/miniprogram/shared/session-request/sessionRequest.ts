import { normalizeApiOrigin } from '../network/apiOrigin';

interface MiniRequestResult {
  readonly statusCode: number;
  readonly data: unknown;
  readonly header: Record<string, string>;
}

interface MiniRequestOptions {
  readonly url: string;
  readonly method: 'GET' | 'POST';
  readonly data?: Record<string, unknown>;
  readonly header: Record<string, string>;
  readonly timeout: number;
  readonly success: (result: MiniRequestResult) => void;
  readonly fail: (reason: unknown) => void;
}

declare const wx: { request(options: MiniRequestOptions): void };

export interface MiniApiProblem {
  readonly code: string;
  readonly detail: string;
}

export interface MiniResponse<T> {
  readonly statusCode: number;
  readonly data: T;
}

export class SessionRequest {
  private readonly origin: string;
  private readonly cookies = new Map<string, string>();

  constructor(origin: string) {
    this.origin = normalizeApiOrigin(origin);
  }

  clear(): void {
    this.cookies.clear();
  }

  hasSession(): boolean {
    return this.cookies.has('JSESSIONID');
  }

  async request<T>(
    path: string,
    method: 'GET' | 'POST',
    data?: Record<string, unknown>,
    headers: Record<string, string> = {},
  ): Promise<MiniResponse<T>> {
    if (!path.startsWith('/api/')) {
      throw new Error('Session requests are restricted to absolute BSR API paths.');
    }
    const cookie = [...this.cookies.entries()].map(([name, value]) => `${name}=${value}`).join('; ');
    const response = await new Promise<MiniRequestResult>((resolve, reject) => {
      wx.request({
        url: `${this.origin}${path}`,
        method,
        data,
        header: {
          Accept: 'application/json, application/problem+json',
          ...(data === undefined ? {} : { 'Content-Type': 'application/json' }),
          ...(cookie === '' ? {} : { Cookie: cookie }),
          ...headers,
        },
        timeout: 15_000,
        success: resolve,
        fail: reject,
      });
    });
    this.captureCookies(response.header);
    if (response.statusCode === 401) {
      this.clear();
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw response.data as MiniApiProblem;
    }
    return { statusCode: response.statusCode, data: response.data as T };
  }

  private captureCookies(headers: Record<string, string>): void {
    const setCookie = Object.entries(headers)
      .filter(([name]) => name.toLowerCase() === 'set-cookie')
      .map(([, value]) => value)
      .join(',');
    for (const name of ['JSESSIONID', 'XSRF-TOKEN']) {
      const match = setCookie.match(new RegExp(`(?:^|[,;]\\s*)${name}=([^;,\\s]+)`));
      if (match?.[1] !== undefined) {
        this.cookies.set(name, match[1]);
      }
    }
  }
}

import { SessionRequest, type MiniApiProblem } from '../session-request/sessionRequest';

interface CsrfToken {
  readonly headerName: string;
  readonly token: string;
}

export interface CustomerActor {
  readonly accountId: string;
  readonly loginName: string;
  readonly identityType: 'CUSTOMER' | 'EMPLOYEE' | 'CONTRACTOR';
  readonly identityId: string;
  readonly grants: ReadonlyArray<{ readonly role: string; readonly storeId: string | null }>;
}

export class CustomerAuth {
  constructor(private readonly session: SessionRequest) {}

  async readApi<T>(path: string): Promise<T> {
    return (await this.session.request<T>(path, 'GET')).data;
  }

  async writeApi<T>(path: string, data: Record<string, unknown>): Promise<T> {
    const csrf = await this.csrf();
    return (await this.session.request<T>(path, 'POST', data, {
      [csrf.headerName]: csrf.token,
    })).data;
  }

  async login(loginName: string, password: string): Promise<CustomerActor> {
    this.session.clear();
    try {
      const csrf = await this.csrf();
      const actor = (await this.session.request<CustomerActor>(
        '/api/auth/login',
        'POST',
        { loginName, password },
        { [csrf.headerName]: csrf.token },
      )).data;
      if (actor.identityType !== 'CUSTOMER' || !actor.grants.some((grant) => grant.role === 'CUSTOMER')) {
        await this.logout();
        throw { code: 'CUSTOMER_ACCOUNT_REQUIRED', detail: 'Use a customer account in the mini program.' } satisfies MiniApiProblem;
      }
      return actor;
    } catch (error) {
      this.session.clear();
      throw error;
    }
  }

  async me(): Promise<CustomerActor> {
    return this.readApi<CustomerActor>('/api/auth/me');
  }

  async logout(): Promise<void> {
    try {
      const csrf = await this.csrf();
      await this.session.request<void>('/api/auth/logout', 'POST', undefined, {
        [csrf.headerName]: csrf.token,
      });
    } finally {
      this.session.clear();
    }
  }

  private async csrf(): Promise<CsrfToken> {
    return (await this.session.request<CsrfToken>('/api/auth/csrf', 'GET')).data;
  }
}

let configuredOrigin: string | undefined;
let inMemoryAuth: CustomerAuth | undefined;

export function customerAuthForOrigin(origin: string): CustomerAuth {
  if (inMemoryAuth === undefined || configuredOrigin !== origin) {
    configuredOrigin = origin;
    inMemoryAuth = new CustomerAuth(new SessionRequest(origin));
  }
  return inMemoryAuth;
}

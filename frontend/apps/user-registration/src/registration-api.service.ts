import {Injectable} from '@angular/core';
import {
  Configuration,
  Folder,
  Session,
  RevocationResult,
} from './registration.models';

@Injectable({providedIn: 'root'})
export class RegistrationApiService {
  session(): Promise<Session> {
    return this.request<Session>('/api/session');
  }
  async configuration(): Promise<Configuration | null> {
    const response = await fetch('/api/v1/me');
    if (response.status === 404) return null;
    if (!response.ok)
      throw Error('Account unavailable. Reload or contact the operator.');
    return response.json();
  }
  connect(): Promise<{url: string}> {
    return this.request<{url: string}>('/api/v1/me/gmail/connect', 'POST', {});
  }
  save(
    configuration: Configuration | null,
    folders: Folder[],
  ): Promise<Configuration> {
    return this.request<Configuration>(
      configuration ? '/api/v1/me/configuration' : '/api/v1/registration',
      configuration ? 'PUT' : 'POST',
      configuration ? {version: configuration.version, folders} : {folders},
    );
  }
  remove(deleting: boolean, version: number): Promise<RevocationResult> {
    return this.request<RevocationResult>(
      deleting ? '/api/v1/registration' : '/api/v1/me/gmail/disconnect',
      deleting ? 'DELETE' : 'POST',
      {version},
    );
  }
  logout(): Promise<void> {
    return this.request<void>('/api/logout', 'POST');
  }
  private async request<T>(
    url: string,
    method = 'GET',
    body?: unknown,
  ): Promise<T> {
    const headers: Record<string, string> = {};
    if (method !== 'GET') {
      const csrf = await fetch('/api/csrf').then(r => r.json());
      headers[csrf.headerName] = csrf.token;
      headers['Content-Type'] = 'application/json';
    }
    const res = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!res.ok) {
      if (res.status === 409) {
        throw Error('Your configuration changed. Reload before trying again.');
      }
      if (res.status === 401) {
        throw Error('Your session ended. Reload and sign in again.');
      }
      throw Error('Unable to save. Check your folders and try again.');
    }
    return res.status === 204 ? (undefined as T) : res.json();
  }
}

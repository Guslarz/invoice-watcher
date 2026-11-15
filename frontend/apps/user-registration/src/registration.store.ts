import {Injectable, computed, inject, signal} from '@angular/core';
import {Configuration} from './registration.models';
import {RegistrationApiService} from './registration-api.service';
import {FolderEditorStore} from './folder-editor/folder-editor.store';

@Injectable()
export class RegistrationStore {
  private readonly api = inject(RegistrationApiService);
  private readonly loadingState = signal(true);
  readonly loading = this.loadingState.asReadonly();
  private readonly busyState = signal(false);
  readonly busy = this.busyState.asReadonly();
  private readonly emailState = signal('');
  readonly email = this.emailState.asReadonly();
  private readonly configState = signal<Configuration | null>(null);
  readonly config = this.configState.asReadonly();
  private readonly folderEditor = inject(FolderEditorStore);
  private readonly messageState = signal('');
  readonly message = this.messageState.asReadonly();
  private readonly hasErrorState = signal(false);
  readonly hasError = this.hasErrorState.asReadonly();
  private readonly confirmingState = signal<'' | 'delete' | 'disconnect'>('');
  readonly confirming = this.confirmingState.asReadonly();
  private readonly pendingState = signal(false);
  readonly pending = this.pendingState.asReadonly();
  readonly confirmationMessage = computed(() =>
    this.confirming() === 'delete'
      ? 'Delete your registration and scan progress?'
      : 'Disconnect Gmail and remove scan progress?',
  );

  requestConfirmation(action: 'delete' | 'disconnect'): void {
    this.confirmingState.set(action);
  }
  cancelConfirmation(): void {
    this.confirmingState.set('');
  }
  async load(): Promise<void> {
    try {
      const session = await this.api.session();
      this.emailState.set(session.email ?? '');
      if (session.authenticated) {
        const configuration = await this.api.configuration();
        if (configuration) this.apply(configuration);
      }
      const status = new URLSearchParams(location.search).get('connection');
      this.pendingState.set(
        status === 'connected' && !this.config()?.connected,
      );
      if (
        status === 'denied' ||
        status === 'failed' ||
        status === 'temporary-error'
      ) {
        this.show(
          'Gmail connection was not completed. Your existing configuration is unchanged.',
          true,
        );
      } else if (status === 'connected') {
        this.show('Gmail authorization completed.');
      }
    } catch (error) {
      this.show((error as Error).message, true);
    } finally {
      this.loadingState.set(false);
    }
  }
  private apply(value: Configuration): void {
    this.configState.set(value);
    this.folderEditor.reset(value.folders);
  }
  private show(message: string, error = false): void {
    this.messageState.set(message);
    this.hasErrorState.set(error);
  }
  signin(): void {
    location.assign('/oauth2/authorization/google');
  }
  async connect(): Promise<void> {
    this.busyState.set(true);
    try {
      const result = await this.api.connect();
      location.assign(result.url);
    } catch (error) {
      this.show((error as Error).message, true);
      this.busyState.set(false);
    }
  }
  async save(): Promise<void> {
    this.busyState.set(true);
    try {
      const folders = this.folderEditor.toPayload();
      this.apply(await this.api.save(this.config(), folders));
      this.pendingState.set(false);
      this.show(
        'Your folders are saved. The next scheduled scan uses these settings.',
      );
    } catch (error) {
      this.show((error as Error).message, true);
    } finally {
      this.busyState.set(false);
    }
  }
  async confirm(): Promise<void> {
    this.busyState.set(true);
    try {
      const deleting = this.confirming() === 'delete';
      const result = await this.api.remove(deleting, this.config()!.version);
      this.confirmingState.set('');
      await this.api.logout();
      this.emailState.set('');
      this.configState.set(null);
      this.show(
        result.revoked
          ? 'Removed. You are signed out.'
          : 'Removed locally. Google revocation failed; remove InvoiceWatcher access in your Google Account permissions.',
        !result.revoked,
      );
    } catch (error) {
      this.show((error as Error).message, true);
    } finally {
      this.busyState.set(false);
    }
  }
  async signout(): Promise<void> {
    try {
      await this.api.logout();
      location.assign('/');
    } catch (error) {
      this.show((error as Error).message, true);
    }
  }
}

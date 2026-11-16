import {Injectable, computed, signal} from '@angular/core';
import {Folder} from '../registration.models';
import {folderError, hasInvalidFolders} from '../folder-validation';

@Injectable()
export class FolderEditorStore {
  private readonly folderState = signal<Folder[]>([
    {name: 'INBOX', startDate: ''},
  ]);
  readonly folders = this.folderState.asReadonly();
  readonly invalid = computed(() => hasInvalidFolders(this.folders()));
  readonly rows = computed(() =>
    this.folders().map((folder, index) => ({
      folder,
      nameError: folderError(this.folders(), folder, index),
      dateType: folder.id ? 'text' : 'datetime-local',
      dateReadOnly: !!folder.id,
      dateError:
        folder.dateValid === false
          ? 'Enter a complete date and time, or leave it empty.'
          : '',
      dateHint: folder.id
        ? 'Set when this folder was added.'
        : 'Optional. Uses your local time; leave empty for all history.',
    })),
  );

  reset(folders: Folder[]): void {
    this.folderState.set(folders.map(folder => ({...folder})));
  }
  toPayload(): Folder[] {
    return this.folders().map(folder => ({
      ...(folder.id ? {id: folder.id} : {}),
      name: folder.name,
      startDate: folder.id
        ? folder.startDate
        : folder.startDate
          ? new Date(folder.startDate).toISOString()
          : '',
    }));
  }
  add(): void {
    this.folderState.update(v => [...v, {name: '', startDate: ''}]);
  }
  remove(i: number): void {
    this.folderState.update(v => v.filter((_, index) => index !== i));
  }
  change(i: number, field: 'name' | 'startDate', value: string): void {
    this.folderState.update(v =>
      v.map((f, index) => (index === i ? {...f, [field]: value} : f)),
    );
  }
  setDateValidity(index: number, valid: boolean): void {
    this.folderState.update(values =>
      values.map((folder, i) =>
        i === index ? {...folder, dateValid: valid} : folder,
      ),
    );
  }
}

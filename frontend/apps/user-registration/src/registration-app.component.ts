import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
} from '@angular/core';
import {ActionComponent, NoticeComponent} from '@invoice-watcher/ui';
import {FolderEditorComponent} from './folder-editor/folder-editor.component';
import {FolderEditorStore} from './folder-editor/folder-editor.store';
import {RegistrationStore} from './registration.store';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'iw-root',
  providers: [RegistrationStore, FolderEditorStore],
  imports: [ActionComponent, NoticeComponent, FolderEditorComponent],
  templateUrl: './registration-app.component.html',
  styleUrl: './registration-app.component.scss',
})
export class RegistrationAppComponent implements OnInit {
  readonly store = inject(RegistrationStore);

  ngOnInit(): void {
    void this.store.load();
  }
}

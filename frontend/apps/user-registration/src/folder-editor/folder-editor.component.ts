import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  output,
} from '@angular/core';
import {ActionComponent, FieldComponent} from '@invoice-watcher/ui';
import {FolderEditorStore} from './folder-editor.store';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'iw-folder-editor',
  imports: [ActionComponent, FieldComponent],
  templateUrl: './folder-editor.component.html',
  styleUrl: './folder-editor.component.scss',
})
export class FolderEditorComponent {
  readonly store = inject(FolderEditorStore);
  readonly busy = input(false);
  readonly registered = input(false);
  readonly saved = output<void>();
}

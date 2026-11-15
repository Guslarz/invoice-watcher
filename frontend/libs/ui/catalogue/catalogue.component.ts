import {ChangeDetectionStrategy, Component} from '@angular/core';
import {
  ActionComponent,
  FieldComponent,
  NoticeComponent,
} from '@invoice-watcher/ui';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'iw-root',
  standalone: true,
  imports: [ActionComponent, FieldComponent, NoticeComponent],
  templateUrl: './catalogue.component.html',
  styleUrl: './catalogue.component.scss',
})
export class CatalogueComponent {}

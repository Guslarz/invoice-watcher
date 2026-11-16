import {
  ChangeDetectionStrategy,
  Component,
  ViewEncapsulation,
  input,
  output,
} from '@angular/core';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'iw-field',
  standalone: true,
  encapsulation: ViewEncapsulation.ShadowDom,
  templateUrl: './field.component.html',
  styleUrl: './field.component.scss',
})
export class FieldComponent {
  label = input.required<string>();
  value = input('');
  type = input('text');
  hint = input('');
  error = input('');
  readOnly = input(false);
  required = input(false);
  valueChange = output<string>();
  validityChange = output<boolean>();
  changed(event: Event): void {
    const control = event.target as HTMLInputElement;
    this.valueChange.emit(control.value);
    this.validityChange.emit(control.validity.valid);
  }
}

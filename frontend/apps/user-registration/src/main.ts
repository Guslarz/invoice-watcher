import {bootstrapApplication} from '@angular/platform-browser';
import {RegistrationAppComponent} from './registration-app.component';

bootstrapApplication(RegistrationAppComponent).catch(() => {
  document.body.textContent = 'Unable to load InvoiceWatcher. Please reload.';
});

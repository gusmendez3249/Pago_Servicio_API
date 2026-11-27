import { InjectionToken } from '@angular/core';
import { environment } from '../../../environments/environment';

/** URL base del backend propio (sin barra final). Sale de `environment`. */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => environment.apiBaseUrl.replace(/\/+$/, ''),
});

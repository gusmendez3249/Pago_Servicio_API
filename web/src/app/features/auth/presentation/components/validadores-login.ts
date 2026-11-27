import { ValidatorFn, Validators } from '@angular/forms';

export const PATRON_USUARIO = /^[A-Za-z0-9._-]+$/;

/** Usuario: obligatorio, 3–50 caracteres `[A-Za-z0-9._-]`. */
export const validadoresUsuario: ValidatorFn[] = [
  Validators.required,
  Validators.minLength(3),
  Validators.maxLength(50),
  Validators.pattern(PATRON_USUARIO),
];

/** Contraseña: obligatoria, 8–72 caracteres. */
export const validadoresPassword: ValidatorFn[] = [
  Validators.required,
  Validators.minLength(8),
  Validators.maxLength(72),
];

import { Entrada } from '../../../../shared/entrada/entrada.directive';
import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { mensajeGenerico } from '../../../../core/error/mensajes-error';
import { Banner } from '../../../../shared/ui/banner/banner';
import { Icono } from '../../../../shared/ui/icono/icono';
import { Logotipo } from '../../../../shared/ui/logotipo/logotipo';
import { Pasos } from '../../../../shared/ui/pasos/pasos';
import { formatoMoneda } from '../../../catalogo/domain/models/producto-reglas';
import { CuentaCreada, Nacionalidad, RegistroApi, SolicitudRegistro } from '../../data/registro-api';
import {
  PATRON_CORREO,
  PATRON_TEXTO_SEGURO,
  PATRON_USUARIO,
  edad,
  errorCurp,
  errorRfc,
  nombreValido,
  passwordValida,
  pinTrivial,
} from '../../domain/reglas-registro';

const PASOS = ['Tus datos', 'Contacto y domicilio', 'Trabajo', 'Acceso'] as const;

/** Campos de cada paso: al continuar solo se valida el paso actual. */
/** 'yyyy-mm-dd' de hoy menos n años (hora local). */
function fechaHaceAnios(anios: number, hoy = new Date()): string {
  const f = new Date(hoy.getFullYear() - anios, hoy.getMonth(), hoy.getDate());
  const dos = (n: number) => String(n).padStart(2, '0');
  return `${f.getFullYear()}-${dos(f.getMonth() + 1)}-${dos(f.getDate())}`;
}

const CAMPOS_POR_PASO: readonly (readonly string[])[] = [
  ['nombre', 'segundoNombre', 'apellidoPaterno', 'apellidoMaterno', 'fechaNacimiento', 'sexo', 'estadoCivil', 'nacionalidad', 'curp', 'rfc'],
  ['correo', 'telefono', 'calle', 'numeroExterior', 'numeroInterior', 'colonia', 'municipio', 'estado', 'codigoPostal'],
  ['ocupacion', 'empresa', 'ingresoMensual'],
  ['username', 'password', 'confirmacion', 'pin', 'confirmacionPin'],
];

/** Campo del backend (datosPersonales.curp…) → control del formulario. */
const CONTROL_DE_CAMPO: Record<string, string> = {
  'datosPersonales.nombre': 'nombre',
  'datosPersonales.segundoNombre': 'segundoNombre',
  'datosPersonales.apellidoPaterno': 'apellidoPaterno',
  'datosPersonales.apellidoMaterno': 'apellidoMaterno',
  'datosPersonales.fechaNacimiento': 'fechaNacimiento',
  'datosPersonales.sexo': 'sexo',
  'datosPersonales.estadoCivil': 'estadoCivil',
  'datosPersonales.nacionalidad': 'nacionalidad',
  'datosPersonales.curp': 'curp',
  'datosPersonales.rfc': 'rfc',
  'datosContacto.correo': 'correo',
  'datosContacto.telefonoMovil': 'telefono',
  'domicilio.calle': 'calle',
  'domicilio.numeroExterior': 'numeroExterior',
  'domicilio.numeroInterior': 'numeroInterior',
  'domicilio.colonia': 'colonia',
  'domicilio.municipio': 'municipio',
  'domicilio.estado': 'estado',
  'domicilio.codigoPostal': 'codigoPostal',
  'informacionLaboral.ocupacion': 'ocupacion',
  'informacionLaboral.empresa': 'empresa',
  'informacionLaboral.ingresoMensual': 'ingresoMensual',
  'loginCredenciales.username': 'username',
  'loginCredenciales.password': 'password',
  'loginCredenciales.pin': 'pin',
};

const texto = (max: number, requerido = true) => [
  ...(requerido ? [Validators.required] : []),
  Validators.maxLength(max),
  Validators.pattern(PATRON_TEXTO_SEGURO),
];

const nombre = (requerido = true) => (c: AbstractControl): ValidationErrors | null => {
  const v = String(c.value ?? '').trim();
  if (!v) return requerido ? { required: true } : null;
  return nombreValido(v) ? null : { nombre: true };
};

@Component({
  selector: 'app-registro-page',
  imports: [Banner, Icono, Logotipo, Pasos, ReactiveFormsModule, RouterLink, Entrada],
  templateUrl: './registro-page.html',
  styleUrls: ['../../../auth/presentation/pages/login-page.scss', './registro-page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegistroPage {
  private readonly api = inject(RegistroApi);
  private readonly titulo = viewChild<ElementRef<HTMLElement>>('titulo');

  protected readonly pasos = PASOS;
  protected readonly paso = signal(0);
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly creada = signal<CuentaCreada | null>(null);
  protected readonly nacionalidades = signal<Nacionalidad[]>([]);
  protected readonly verPassword = signal(false);
  /** El selector de fecha solo ofrece fechas de personas de 18 a 100 años. */
  protected readonly fechaMaxima = fechaHaceAnios(18);
  protected readonly fechaMinima = fechaHaceAnios(100);
  /** Errores que devolvió el servidor por control (texto amigable fijo). */
  protected readonly rechazados = signal<Record<string, string>>({});

  protected readonly form = new FormGroup({
    nombre: new FormControl('', { nonNullable: true, validators: nombre() }),
    segundoNombre: new FormControl('', { nonNullable: true, validators: nombre(false) }),
    apellidoPaterno: new FormControl('', { nonNullable: true, validators: nombre() }),
    apellidoMaterno: new FormControl('', { nonNullable: true, validators: nombre() }),
    fechaNacimiento: new FormControl('', { nonNullable: true, validators: Validators.required }),
    sexo: new FormControl('', { nonNullable: true, validators: Validators.required }),
    estadoCivil: new FormControl('', { nonNullable: true, validators: Validators.required }),
    nacionalidad: new FormControl('MEXICANA', { nonNullable: true, validators: Validators.required }),
    curp: new FormControl('', { nonNullable: true, validators: Validators.required }),
    rfc: new FormControl('', { nonNullable: true, validators: Validators.required }),
    correo: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100), Validators.pattern(PATRON_CORREO)] }),
    telefono: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^[1-9]\d{9}$/)] }),
    calle: new FormControl('', { nonNullable: true, validators: texto(100) }),
    numeroExterior: new FormControl('', { nonNullable: true, validators: texto(20) }),
    numeroInterior: new FormControl('', { nonNullable: true, validators: texto(20, false) }),
    colonia: new FormControl('', { nonNullable: true, validators: texto(100) }),
    municipio: new FormControl('', { nonNullable: true, validators: texto(100) }),
    estado: new FormControl('', { nonNullable: true, validators: texto(100) }),
    codigoPostal: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^\d{5}$/)] }),
    ocupacion: new FormControl('', { nonNullable: true, validators: texto(100) }),
    empresa: new FormControl('', { nonNullable: true, validators: texto(100) }),
    ingresoMensual: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^\d{1,12}(\.\d{1,2})?$/)] }),
    username: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(PATRON_USUARIO)] }),
    password: new FormControl('', { nonNullable: true, validators: Validators.required }),
    confirmacion: new FormControl('', { nonNullable: true, validators: Validators.required }),
    pin: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^\d{4}$/)] }),
    confirmacionPin: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  constructor() {
    void this.cargarNacionalidades();
  }

  private async cargarNacionalidades(): Promise<void> {
    const r = await this.api.nacionalidades();
    if (r.ok) this.nacionalidades.set(r.data);
  }

  protected control(nombreControl: string): AbstractControl {
    return this.form.get(nombreControl)!;
  }

  /** Mensaje del campo: primero el rechazo del servidor; luego las reglas locales, solo si se tocó. */
  protected mensaje(c: string): string | null {
    const rechazado = this.rechazados()[c];
    if (rechazado) return rechazado;
    const control = this.control(c);
    if (!control.touched) return null;
    const v = String(control.value ?? '').trim();
    const e = control.errors ?? {};
    const valores = this.form.getRawValue();
    switch (c) {
      case 'nombre':
      case 'apellidoPaterno':
      case 'apellidoMaterno':
      case 'segundoNombre':
        if (e['required']) return 'Este dato es obligatorio.';
        return e['nombre'] ? 'Usa de 3 a 50 letras, con un solo espacio entre palabras.' : null;
      case 'fechaNacimiento': {
        if (e['required']) return 'Indica tu fecha de nacimiento.';
        const anios = edad(v);
        if (anios === null) return 'Revisa la fecha.';
        if (anios < 18) return 'Debes ser mayor de edad para abrir una cuenta.';
        return anios > 120 ? 'Revisa el año de nacimiento.' : null;
      }
      case 'sexo':
      case 'estadoCivil':
      case 'nacionalidad':
        return e['required'] ? 'Elige una opción.' : null;
      case 'curp': {
        if (e['required']) return 'Escribe tu CURP.';
        switch (errorCurp(v.toUpperCase(), valores.fechaNacimiento, valores.sexo)) {
          case 'formato':
            return 'La CURP tiene 18 caracteres. Revísala en tu acta o credencial.';
          case 'digito':
            return 'La CURP no es válida. Revisa el último carácter.';
          case 'fecha':
            return 'La CURP no coincide con tu fecha de nacimiento.';
          case 'sexo':
            return 'La CURP no coincide con el sexo que elegiste.';
          default:
            return null;
        }
      }
      case 'rfc': {
        if (e['required']) return 'Escribe tu RFC.';
        switch (errorRfc(v.toUpperCase(), valores.curp.trim().toUpperCase())) {
          case 'formato':
            return 'El RFC tiene 13 caracteres, con homoclave.';
          case 'generico':
            return 'Usa tu RFC personal, no el genérico.';
          case 'curp':
            return 'El RFC no corresponde a tu CURP.';
          default:
            return null;
        }
      }
      case 'correo':
        return e['required'] ? 'Escribe tu correo.' : e['pattern'] || e['maxlength'] ? 'Revisa tu correo (ejemplo: nombre@dominio.com).' : null;
      case 'telefono':
        return e['required'] ? 'Escribe tu celular.' : e['pattern'] ? 'El celular tiene 10 dígitos.' : null;
      case 'codigoPostal':
        return e['required'] ? 'Escribe tu código postal.' : e['pattern'] ? 'El código postal tiene 5 dígitos.' : null;
      case 'ingresoMensual':
        if (e['required']) return 'Indica tu ingreso mensual.';
        if (e['pattern']) return 'Escribe solo números, con hasta 2 decimales.';
        return Number(v) <= 0 ? 'El ingreso debe ser mayor a cero.' : null;
      case 'username':
        return e['required'] ? 'Elige un usuario.' : e['pattern'] ? 'De 3 a 50 caracteres: letras, números, punto, guion o guion bajo.' : null;
      case 'password':
        if (e['required']) return 'Crea una contraseña.';
        return passwordValida(control.value) ? null : 'Usa de 8 a 72 caracteres, con mayúscula, minúscula, número y un símbolo, sin espacios.';
      case 'confirmacion':
        if (e['required']) return 'Escribe otra vez tu contraseña.';
        return control.value !== valores.password ? 'Las contraseñas no coinciden.' : null;
      case 'pin':
        if (e['required']) return 'Crea tu PIN de 4 dígitos.';
        if (e['pattern']) return 'El PIN tiene 4 dígitos.';
        return pinTrivial(v) ? 'Evita PIN como 1111 o 1234.' : null;
      case 'confirmacionPin':
        if (e['required']) return 'Escribe otra vez tu PIN.';
        return control.value !== valores.pin ? 'Los PIN no coinciden.' : null;
      default:
        return e['required'] ? 'Este dato es obligatorio.' : Object.keys(e).length ? 'Revisa este dato.' : null;
    }
  }

  protected limpiar(c: string): void {
    this.error.set(null);
    if (!(c in this.rechazados())) return;
    const { [c]: _omitido, ...resto } = this.rechazados();
    this.rechazados.set(resto);
  }

  private pasoValido(indice: number): boolean {
    const campos = CAMPOS_POR_PASO[indice];
    campos.forEach((c) => this.control(c).markAsTouched());
    return campos.every((c) => this.mensaje(c) === null);
  }

  protected continuar(): void {
    if (!this.pasoValido(this.paso())) return;
    this.irA(this.paso() + 1);
  }

  protected atras(): void {
    this.irA(this.paso() - 1);
  }

  private irA(indice: number): void {
    this.paso.set(indice);
    this.error.set(null);
    queueMicrotask(() => this.titulo()?.nativeElement.focus());
  }

  protected async crear(): Promise<void> {
    if (this.enviando()) return;
    for (let i = 0; i < PASOS.length; i++) {
      if (!this.pasoValido(i)) {
        this.irA(i);
        return;
      }
    }
    this.enviando.set(true);
    this.error.set(null);
    const r = await this.api.registrar(this.solicitud());
    this.enviando.set(false);
    if (r.ok) {
      this.creada.set(r.data);
      this.form.controls.password.reset('');
      this.form.controls.confirmacion.reset('');
      this.form.controls.pin.reset('');
      this.form.controls.confirmacionPin.reset('');
      queueMicrotask(() => this.titulo()?.nativeElement.focus());
      return;
    }
    if (r.tipo === 'validacion' && r.campos?.length) {
      const rechazos: Record<string, string> = {};
      for (const campo of r.campos) {
        const c = CONTROL_DE_CAMPO[campo.campo];
        if (c) rechazos[c] = 'Revisa este dato.';
      }
      this.rechazados.set(rechazos);
      const primero = CAMPOS_POR_PASO.findIndex((campos) => campos.some((c) => c in rechazos));
      this.error.set('Revisa los datos marcados.');
      if (primero >= 0) this.irA(primero);
      this.error.set('Revisa los datos marcados.');
      return;
    }
    this.error.set(
      r.httpStatus === 409
        ? 'Alguno de tus datos (CURP, RFC, correo o usuario) ya está registrado. Si ya tienes cuenta, inicia sesión.'
        : mensajeGenerico(r),
    );
  }

  private solicitud(): SolicitudRegistro {
    const v = this.form.getRawValue();
    const t = (s: string) => s.trim();
    return {
      bandera: 1,
      datosPersonales: {
        nombre: t(v.nombre),
        ...(t(v.segundoNombre) ? { segundoNombre: t(v.segundoNombre) } : {}),
        apellidoPaterno: t(v.apellidoPaterno),
        apellidoMaterno: t(v.apellidoMaterno),
        fechaNacimiento: v.fechaNacimiento,
        curp: t(v.curp).toUpperCase(),
        rfc: t(v.rfc).toUpperCase(),
        sexo: v.sexo,
        nacionalidad: v.nacionalidad,
        estadoCivil: v.estadoCivil,
      },
      datosContacto: { correo: t(v.correo).toLowerCase(), telefonoMovil: Number(v.telefono) },
      domicilio: {
        calle: t(v.calle),
        numeroExterior: t(v.numeroExterior),
        ...(t(v.numeroInterior) ? { numeroInterior: t(v.numeroInterior) } : {}),
        colonia: t(v.colonia),
        municipio: t(v.municipio),
        estado: t(v.estado),
        codigoPostal: v.codigoPostal,
        pais: 'MEXICO',
      },
      informacionLaboral: { ocupacion: t(v.ocupacion), empresa: t(v.empresa), ingresoMensual: Number(v.ingresoMensual) },
      loginCredenciales: { username: t(v.username), password: v.password, pin: v.pin },
    };
  }

  protected moneda(valor: number): string {
    return formatoMoneda(valor);
  }
}

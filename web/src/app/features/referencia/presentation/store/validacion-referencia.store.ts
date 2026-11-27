import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { SessionStore } from '../../../../core/session/session-store';
import { ErrorFormato, validarFormatoReferencia } from '../../domain/models/formato-referencia';
import {
  TEXTO_ERROR,
  TEXTO_NADA_POR_PAGAR,
  TEXTO_SOLO_FORMATO,
  textoTotalAPagar,
  TEXTO_EXPIRADA,
  TEXTO_INVALIDA,
  TEXTO_SOLO_CONSULTA,
  textoErrorFormato,
  textoVerificada,
} from '../../domain/models/mensajes-referencia';
import { DatosPago, ValidacionReferencia } from '../../domain/models/validacion-referencia';
import { ValidarReferenciaUseCase } from '../../domain/use-cases/validar-referencia.use-case';

export type EstadoReferencia =
  | 'capturando'
  | 'formatoInvalido'
  | 'validando'
  | 'valida'
  | 'soloConsulta'
  | 'invalida'
  | 'error'
  | 'expirada';

/** Producto sobre el que se captura la referencia. */
export interface ProductoReferencia {
  idSer: number;
  idProd: number;
  servicio: string;
  producto: string;
  logoUrl: string | null;
  ayudaUrl: string | null;
}

export type TonoResultado = 'ok' | 'error' | 'aviso' | 'info' | 'neutro';

export interface ResultadoVisible {
  tono: TonoResultado;
  texto: string;
  detalle: string | null;
}

const RUTA_FLUJO = /^\/catalogo\/(\d+)\/(\d+)\/(referencia|pago)(?:[?#].*)?$/;

/**
 * Máquina de estados de la validación de referencia. Singleton en memoria: el guard de la ruta de pago y el
 * Sprint 3 leen de aquí. Nada se persiste. Se limpia al cerrar sesión (incluye 401), al cambiar de producto y
 * al salir del flujo.
 */
@Injectable({ providedIn: 'root' })
export class ValidacionReferenciaStore {
  private readonly validarReferencia = inject(ValidarReferenciaUseCase);
  private readonly sesion = inject(SessionStore);
  private readonly router = inject(Router);

  private readonly _estado = signal<EstadoReferencia>('capturando');
  private readonly _producto = signal<ProductoReferencia | null>(null);
  private readonly _texto = signal('');
  private readonly _errorFormato = signal<ErrorFormato | null>(null);
  private readonly _validacion = signal<ValidacionReferencia | null>(null);
  private readonly _expiraEn = signal<number | null>(null);

  private temporizador: ReturnType<typeof setTimeout> | null = null;
  /** Se incrementa en cada invalidación para ignorar respuestas atrasadas. */
  private generacion = 0;

  readonly estado = this._estado.asReadonly();
  readonly producto = this._producto.asReadonly();
  readonly texto = this._texto.asReadonly();
  readonly errorFormato = this._errorFormato.asReadonly();
  readonly validacion = this._validacion.asReadonly();

  readonly mensajeFormato = computed(() => {
    const error = this._errorFormato();
    return this._estado() === 'formatoInvalido' && error !== null ? textoErrorFormato(error) : null;
  });

  /** Mensaje de resultado (siempre texto propio; nunca el de la respuesta). */
  readonly resultado = computed<ResultadoVisible | null>(() => {
    const v = this._validacion();
    switch (this._estado()) {
      case 'valida':
        if (v?.verificacion === 'SOLO_FORMATO') return { tono: 'aviso', texto: TEXTO_SOLO_FORMATO, detalle: null };
        return {
          tono: 'ok',
          texto: textoVerificada(v?.situacion ?? null, v?.saldo ?? null),
          detalle: v?.montoAPagar != null ? textoTotalAPagar(v.montoAPagar) : null,
        };
      case 'soloConsulta': {
        const situacion = v?.situacion ?? null;
        if (situacion === null) return { tono: 'info', texto: TEXTO_SOLO_CONSULTA, detalle: null };
        const texto = textoVerificada(situacion, v?.saldo ?? null);
        return { tono: 'info', texto, detalle: situacion === 'ADEUDO' ? TEXTO_SOLO_CONSULTA : TEXTO_NADA_POR_PAGAR };
      }
      case 'invalida':
        return { tono: 'error', texto: TEXTO_INVALIDA, detalle: null };
      case 'error':
        return { tono: 'aviso', texto: TEXTO_ERROR, detalle: null };
      case 'expirada':
        return { tono: 'neutro', texto: TEXTO_EXPIRADA, detalle: null };
      default:
        return null;
    }
  });

  readonly validando = computed(() => this._estado() === 'validando');

  /** Datos para pagar; solo existen en estado `valida` con comprobante. */
  readonly datosPago = computed<DatosPago | null>(() => {
    const v = this._validacion();
    const expiraEn = this._expiraEn();
    if (this._estado() !== 'valida' || v === null || v.idValidacion === null || expiraEn === null) return null;
    return {
      idSer: v.idSer,
      idProd: v.idProd,
      referencia: v.referencia,
      idValidacion: v.idValidacion,
      saldo: v.saldo,
      situacion: v.situacion,
      verificacion: v.verificacion,
      montoAPagar: v.montoAPagar,
      servicio: v.servicio,
      producto: v.producto,
      expiraEn,
    };
  });

  readonly puedeContinuar = computed(() => this.datosPago() !== null);

  constructor() {
    // Cierre de sesión (voluntario, inactividad o 401): se descarta todo.
    effect(() => {
      if (!this.sesion.haySesion()) this.reiniciar();
    });
    // Salir del flujo (otra pantalla u otro producto) invalida la validación.
    this.router.events.subscribe((evento) => {
      if (!(evento instanceof NavigationEnd)) return;
      const coincidencia = RUTA_FLUJO.exec(evento.urlAfterRedirects);
      const p = this._producto();
      const enFlujo = coincidencia !== null && p !== null && Number(coincidencia[1]) === p.idSer && Number(coincidencia[2]) === p.idProd;
      if (!enFlujo) this.reiniciar();
    });
  }

  /** Activa el producto; si es distinto del anterior descarta lo capturado y validado. */
  iniciar(producto: ProductoReferencia): void {
    const actual = this._producto();
    if (actual !== null && (actual.idSer !== producto.idSer || actual.idProd !== producto.idProd)) {
      this.reiniciar();
    }
    this._producto.set(producto);
  }

  /** Cada edición invalida la validación previa. */
  editar(texto: string): void {
    if (this._estado() === 'validando') return;
    this.descartarValidacion();
    this._texto.set(texto);
    this._errorFormato.set(null);
    this._estado.set('capturando');
  }

  /** Valida el formato y consulta; ignora envíos mientras hay una consulta en curso. */
  async validar(): Promise<void> {
    const producto = this._producto();
    if (producto === null || this._estado() === 'validando') return;

    this.descartarValidacion();
    const formato = validarFormatoReferencia(this._texto());
    if (!formato.valida) {
      this._errorFormato.set(formato.error);
      this._estado.set('formatoInvalido');
      return;
    }

    this._errorFormato.set(null);
    this._estado.set('validando');
    const generacion = this.generacion;
    const resultado = await this.validarReferencia.ejecutar(producto.idSer, producto.idProd, formato.referencia);
    if (generacion !== this.generacion) return;

    if (!resultado.ok) {
      // El 401 ya lo atiende el interceptor (sesión cerrada y envío a login).
      this._estado.set('error');
      return;
    }
    this.aplicar(resultado.data, producto, formato.referencia);
  }

  reintentar(): Promise<void> {
    return this.validar();
  }

  /** Limpia todo (sesión cerrada, cambio de producto o salida del flujo). */
  reiniciar(): void {
    this.descartarValidacion();
    this._producto.set(null);
    this._texto.set('');
    this._errorFormato.set(null);
    this._estado.set('capturando');
  }

  private aplicar(v: ValidacionReferencia, producto: ProductoReferencia, referencia: string): void {
    if (v.idSer !== producto.idSer || v.idProd !== producto.idProd || v.referencia !== referencia) {
      this._estado.set('error');
      return;
    }
    if (v.estado === 'INVALIDA') {
      this._estado.set('invalida');
      return;
    }
    const vigencia = v.vigenciaSegundos ?? 0;
    if (v.puedePagar && v.idValidacion !== null && vigencia > 0) {
      this._validacion.set(v);
      this._expiraEn.set(Date.now() + vigencia * 1000);
      this.temporizador = setTimeout(() => this.expirar(), vigencia * 1000);
      this._estado.set('valida');
    } else if (!v.puedePagar) {
      this._validacion.set(v);
      this._estado.set('soloConsulta');
    } else {
      this._estado.set('error');
    }
  }

  private expirar(): void {
    this.temporizador = null;
    if (this._estado() !== 'valida') return;
    this.descartarValidacion();
    this._estado.set('expirada');
  }

  /** Descarta `idValidacion`, el temporizador y cualquier respuesta en vuelo. */
  private descartarValidacion(): void {
    this.generacion++;
    if (this.temporizador !== null) {
      clearTimeout(this.temporizador);
      this.temporizador = null;
    }
    this._validacion.set(null);
    this._expiraEn.set(null);
  }
}

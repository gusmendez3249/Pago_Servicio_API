import { Injectable, InjectionToken, computed, inject, signal } from '@angular/core';
import { nuevaIdempotencyKey } from '../../../../core/http/idempotency';
import { ValidacionReferenciaStore } from '../../../referencia/presentation/store/validacion-referencia.store';
import { Verificacion } from '../../../referencia/domain/models/validacion-referencia';
import {
  CuentaCliente,
  MotivoRechazo,
  ProductoPago,
  ResultadoPago,
  SalidaEstado,
  SalidaPago,
  SolicitudPago,
} from '../../domain/models/pago';
import {
  ErrorImporte,
  ErrorTelefono,
  INTERVALOS_SONDEO_MS,
  NO_ENCONTRADO_MAXIMO,
  calcularResumen,
  esPinValido,
  motivoRechazoDe,
  soloDigitos,
  validarImporte,
  validarTelefono,
} from '../../domain/models/pago-reglas';
import { textoPinIncorrecto } from '../../domain/models/mensajes-pago';
import {
  ConsultarEstadoPagoUseCase,
  ObtenerCuentasUseCase,
  PagarServicioUseCase,
} from '../../domain/use-cases/pago.use-cases';

export type EstadoPago =
  | 'preparando'
  | 'capturandoDatos'
  | 'resumen'
  | 'confirmando'
  | 'procesando'
  | 'exitoso'
  | 'rechazado'
  | 'pendiente'
  | 'error';

export type MotivoError = 'SIN_CUENTA' | 'PIN_BLOQUEADO' | 'VALIDACION_EXPIRADA' | 'GENERICO';

/** Intervalos de sondeo (ms desde que se entra a `pendiente`); inyectables para pruebas. */
export const INTERVALOS_SONDEO = new InjectionToken<readonly number[]>('INTERVALOS_SONDEO', {
  providedIn: 'root',
  factory: () => INTERVALOS_SONDEO_MS,
});

/** Datos de la validación de referencia que el pago necesita (solo memoria). */
export interface ReferenciaPago {
  referencia: string;
  idValidacion: string;
  verificacion: Verificacion;
  montoAPagar: number | null;
}

export interface ContextoPago {
  producto: ProductoPago;
  /** Presente solo en pagos con referencia (tipoFront 2). */
  referencia?: ReferenciaPago;
}

export type DestinoReintento = 'referencia' | 'captura';

/**
 * Máquina de estados del pago. Singleton en memoria. El PIN nunca se guarda aquí: llega como argumento de
 * `confirmar` y solo vive en la solicitud en vuelo. Nada se persiste en ningún storage.
 */
@Injectable({ providedIn: 'root' })
export class PagoStore {
  private readonly obtenerCuentas = inject(ObtenerCuentasUseCase);
  private readonly pagarServicio = inject(PagarServicioUseCase);
  private readonly consultarEstado = inject(ConsultarEstadoPagoUseCase);
  private readonly validacion = inject(ValidacionReferenciaStore);
  private readonly intervalos = inject(INTERVALOS_SONDEO);

  private readonly _estado = signal<EstadoPago>('preparando');
  private readonly _producto = signal<ProductoPago | null>(null);
  private readonly _referencia = signal<ReferenciaPago | null>(null);
  private readonly _cuentas = signal<CuentaCliente[]>([]);
  private readonly _cuentaSel = signal<string | null>(null);
  private readonly _telefono = signal('');
  private readonly _telefonoConfirmacion = signal('');
  private readonly _importeTexto = signal('');
  private readonly _importeMonto = signal<number | null>(null);
  private readonly _errorTelefono = signal<ErrorTelefono | null>(null);
  private readonly _errorImporte = signal<ErrorImporte | null>(null);
  private readonly _intentosPin = signal<{ restantes: number | null } | null>(null);
  private readonly _resultado = signal<ResultadoPago | null>(null);
  private readonly _totalFinal = signal<number | null>(null);
  private readonly _motivoRechazo = signal<MotivoRechazo>('RECHAZADO_PROVEEDOR');
  private readonly _motivoError = signal<MotivoError>('GENERICO');
  private readonly _upc = signal<string | null>(null);
  private readonly _sinConexion = signal(false);
  private readonly _noLlego = signal(false);
  private readonly _sondeando = signal(false);
  private readonly _consultando = signal(false);
  private readonly _comprobanteVisible = signal(false);

  private llave: string | null = null;
  private enVuelo = false;
  private noEncontrados = 0;
  private generacion = 0;
  private temporizador: ReturnType<typeof setTimeout> | null = null;

  readonly estado = this._estado.asReadonly();
  readonly producto = this._producto.asReadonly();
  readonly cuentas = this._cuentas.asReadonly();
  readonly telefono = this._telefono.asReadonly();
  readonly telefonoConfirmacion = this._telefonoConfirmacion.asReadonly();
  readonly importeTexto = this._importeTexto.asReadonly();
  readonly errorTelefono = this._errorTelefono.asReadonly();
  readonly errorImporte = this._errorImporte.asReadonly();
  readonly resultado = this._resultado.asReadonly();
  readonly motivoRechazo = this._motivoRechazo.asReadonly();
  readonly motivoError = this._motivoError.asReadonly();
  readonly upc = this._upc.asReadonly();
  readonly sinConexion = this._sinConexion.asReadonly();
  readonly noLlego = this._noLlego.asReadonly();
  readonly sondeando = this._sondeando.asReadonly();
  readonly consultando = this._consultando.asReadonly();
  readonly comprobanteVisible = this._comprobanteVisible.asReadonly();
  readonly totalFinal = this._totalFinal.asReadonly();

  private readonly _modoTelefono = computed(() => this._producto()?.tipoFront === 1);
  /** 'telefono' (tipoFront 1) o 'referencia' (tipoFront 2). */
  readonly modo = computed<'telefono' | 'referencia'>(() => (this._modoTelefono() ? 'telefono' : 'referencia'));
  /** SOLO_FORMATO: el importe del recibo se captura una vez. */
  readonly capturaImporte = computed(() => this._referencia()?.verificacion === 'SOLO_FORMATO');

  readonly procesando = computed(() => this._estado() === 'procesando');
  readonly modalAbierto = computed(() => this._estado() === 'confirmando' || this._estado() === 'procesando');

  readonly total = computed<number | null>(() => {
    if (this._modoTelefono()) return this._producto()?.precio ?? null;
    const ref = this._referencia();
    if (ref === null) return null;
    return ref.verificacion === 'SOLO_FORMATO' ? this._importeMonto() : ref.montoAPagar;
  });

  readonly cuentaElegida = computed<CuentaCliente | null>(() => {
    const cuentas = this._cuentas();
    return cuentas.find((c) => c.numCuenta === this._cuentaSel()) ?? cuentas[0] ?? null;
  });

  readonly resumen = computed(() => {
    const total = this.total();
    const cuenta = this.cuentaElegida();
    return total === null || cuenta === null ? null : calcularResumen(total, cuenta);
  });

  /** Valor mostrable de lo que se paga: referencia o teléfono capturado. */
  readonly destinoPago = computed(() => (this._modoTelefono() ? this._telefono() : (this._referencia()?.referencia ?? '')));

  readonly avisoPin = computed(() => {
    const intentos = this._intentosPin();
    return intentos === null ? null : textoPinIncorrecto(intentos.restantes);
  });

  /** Total con el que se muestra el resultado (el del comprobante o, si falta, el calculado antes de limpiar). */
  readonly totalMostrado = computed(() => this._resultado()?.monto ?? this._totalFinal());

  async iniciar(contexto: ContextoPago): Promise<void> {
    this.reiniciarTodo();
    const gen = this.generacion;
    this._producto.set(contexto.producto);
    this._referencia.set(contexto.referencia ?? null);
    this._estado.set('preparando');
    const salida = await this.obtenerCuentas.ejecutar();
    if (gen !== this.generacion) return;
    if (salida.tipo !== 'cuentas') return this.fallar('GENERICO');
    if (salida.cuentas.length === 0) return this.fallar('SIN_CUENTA');
    this._cuentas.set(salida.cuentas);
    this._cuentaSel.set(salida.cuentas[0].numCuenta);
    if (this._modoTelefono() || this.capturaImporte()) {
      this._estado.set('capturandoDatos');
    } else if (this.total() === null) {
      this.fallar('GENERICO');
    } else {
      this.entrarResumen();
    }
  }

  editarTelefono(valor: string): void {
    this._telefono.set(soloDigitos(valor).slice(0, 10));
    this._errorTelefono.set(null);
  }

  editarTelefonoConfirmacion(valor: string): void {
    this._telefonoConfirmacion.set(soloDigitos(valor).slice(0, 10));
    this._errorTelefono.set(null);
  }

  editarImporte(valor: string): void {
    this._importeTexto.set(valor.replace(/[^\d.]/g, '').slice(0, 9));
    this._errorImporte.set(null);
  }

  /** Valida la captura y pasa al resumen. */
  continuarCaptura(): void {
    if (this._estado() !== 'capturandoDatos') return;
    if (this._modoTelefono()) {
      const error = validarTelefono(this._telefono(), this._telefonoConfirmacion());
      this._errorTelefono.set(error);
      if (error !== null) return;
    } else {
      const importe = validarImporte(this._importeTexto());
      this._errorImporte.set(importe.ok ? null : importe.error);
      if (!importe.ok) return;
      this._importeMonto.set(importe.monto);
    }
    this.entrarResumen();
  }

  seleccionarCuenta(numCuenta: string): void {
    if (this._estado() !== 'resumen' || this.cuentaElegida()?.numCuenta === numCuenta) return;
    this._cuentaSel.set(numCuenta);
    // Cambian los datos del pago: operación nueva, llave nueva.
    this.llave = nuevaIdempotencyKey();
  }

  /** "Pagar" del resumen: abre la confirmación con PIN. */
  pagar(): void {
    const r = this.resumen();
    if (this._estado() !== 'resumen' || r === null || r.saldoInsuficiente) return;
    this._intentosPin.set(null);
    this._estado.set('confirmando');
  }

  cancelarConfirmacion(): void {
    if (this._estado() !== 'confirmando' || this.enVuelo) return;
    this._intentosPin.set(null);
    this._estado.set('resumen');
  }

  /** Solo se acepta desde `confirmando` y sin petición en vuelo: el doble envío es imposible. */
  async confirmar(pin: string): Promise<void> {
    if (this._estado() !== 'confirmando' || this.enVuelo || !esPinValido(pin)) return;
    const solicitud = this.armarSolicitud(pin);
    const llave = this.llave;
    if (solicitud === null || llave === null) return;
    this.enVuelo = true;
    this._intentosPin.set(null);
    this._estado.set('procesando');
    const gen = this.generacion;
    try {
      const salida = await this.pagarServicio.ejecutar(solicitud, llave);
      if (gen !== this.generacion) return;
      await this.aplicarSalida(salida);
    } finally {
      this.enVuelo = false;
    }
  }

  /** Consulta manual ("Consultar de nuevo"). */
  consultarAhora(): Promise<void> {
    return this.consultar();
  }

  /** Tras comprobar que el pago no llegó: pide el PIN otra vez con LA MISMA llave. */
  reintentarPago(): void {
    if (this._estado() !== 'pendiente' || !this._noLlego() || this.llave === null) return;
    this.detenerSondeo();
    this._noLlego.set(false);
    this._sinConexion.set(false);
    this._intentosPin.set(null);
    this._estado.set('confirmando');
  }

  /** "Intentar de nuevo" tras un rechazo definitivo: la llave se renueva (se descartó al terminar). */
  intentarDeNuevo(): DestinoReintento {
    if (this._modoTelefono()) {
      const producto = this._producto();
      if (producto !== null) void this.iniciar({ producto });
      return 'captura';
    }
    return 'referencia';
  }

  /** Salir de la pantalla: detiene el sondeo y borra todo dato sensible. */
  salir(): void {
    this.generacion++;
    this.detenerSondeo();
    this.limpiarSensibles();
    this.validacion.reiniciar();
    this._comprobanteVisible.set(false);
    this._resultado.set(null);
    this._totalFinal.set(null);
    this._upc.set(null);
    this._noLlego.set(false);
    this._sinConexion.set(false);
    this._producto.set(null);
    this._referencia.set(null);
    this._intentosPin.set(null);
    this.enVuelo = false;
    this._estado.set('preparando');
  }

  verComprobante(): void {
    if (this._resultado() !== null) this._comprobanteVisible.set(true);
  }

  ocultarComprobante(): void {
    this._comprobanteVisible.set(false);
  }

  // ---- internos ----

  private reiniciarTodo(): void {
    this.generacion++;
    this.detenerSondeo();
    this.limpiarSensibles();
    this._comprobanteVisible.set(false);
    this._resultado.set(null);
    this._totalFinal.set(null);
    this._upc.set(null);
    this._noLlego.set(false);
    this._sinConexion.set(false);
    this._intentosPin.set(null);
    this.enVuelo = false;
  }

  /** Borra PIN (nunca guardado), llave, comprobante de referencia, teléfono, importe y cuenta completa. */
  private limpiarSensibles(): void {
    this.llave = null;
    this._cuentas.set([]);
    this._cuentaSel.set(null);
    this._telefono.set('');
    this._telefonoConfirmacion.set('');
    this._importeTexto.set('');
    this._importeMonto.set(null);
    this._errorTelefono.set(null);
    this._errorImporte.set(null);
    this._referencia.set(null);
  }

  private entrarResumen(): void {
    this.llave ??= nuevaIdempotencyKey();
    this._estado.set('resumen');
  }

  private armarSolicitud(pin: string): SolicitudPago | null {
    const producto = this._producto();
    const cuenta = this.cuentaElegida();
    const total = this.total();
    if (producto === null || cuenta === null || total === null) return null;
    const base = { numCuenta: cuenta.numCuenta, idSer: producto.idSer, idProd: producto.idProd, monto: total, twoFA: pin };
    if (this._modoTelefono()) return { ...base, esReferencia: false, telefono: this._telefono() };
    const ref = this._referencia();
    if (ref === null) return null;
    return { ...base, esReferencia: true, reference: ref.referencia, idValidacion: ref.idValidacion };
  }

  private async aplicarSalida(salida: SalidaPago): Promise<void> {
    switch (salida.tipo) {
      case 'exitoso':
        this.terminar('exitoso', salida.pago);
        return;
      case 'pendiente':
        this.pasarAPendiente(salida.pago);
        return;
      case 'rechazado':
        this._motivoRechazo.set(salida.motivo);
        this.terminar('rechazado', salida.pago);
        return;
      case 'pinIncorrecto':
        this._intentosPin.set({ restantes: salida.intentosRestantes });
        this._estado.set('confirmando');
        return;
      case 'pinBloqueado':
        this.terminarConError('PIN_BLOQUEADO');
        return;
      case 'validacionRequerida':
        this.terminarConError('VALIDACION_EXPIRADA');
        return;
      case 'saldoInsuficiente':
        await this.refrescarSaldo();
        return;
      case 'sinRed':
        this._sinConexion.set(true);
        this.pasarAPendiente(null);
        return;
      case 'noAutenticado':
        this.terminarConError('GENERICO');
        return;
      default:
        this.terminarConError('GENERICO');
    }
  }

  private async refrescarSaldo(): Promise<void> {
    const gen = this.generacion;
    const salida = await this.obtenerCuentas.ejecutar();
    if (gen !== this.generacion) return;
    if (salida.tipo === 'cuentas' && salida.cuentas.length > 0) {
      const sel = this._cuentaSel();
      this._cuentas.set(salida.cuentas);
      if (!salida.cuentas.some((c) => c.numCuenta === sel)) this._cuentaSel.set(salida.cuentas[0].numCuenta);
    }
    this._estado.set('resumen');
  }

  private pasarAPendiente(pago: ResultadoPago | null): void {
    this._resultado.set(pago);
    this._upc.set(pago?.upc ?? null);
    this.noEncontrados = 0;
    this._noLlego.set(false);
    this._estado.set('pendiente');
    this._sondeando.set(true);
    this.programarSondeo(0);
  }

  /** Estados finales: se conserva solo lo del resultado y se borra todo dato de la operación. */
  private terminar(estado: 'exitoso' | 'rechazado', pago: ResultadoPago | null): void {
    this.detenerSondeo();
    this._totalFinal.set(this.total());
    this._resultado.set(pago);
    this._upc.set(pago?.upc ?? this._upc());
    this._sinConexion.set(false);
    this._noLlego.set(false);
    this.limpiarSensibles();
    this.validacion.reiniciar();
    this._estado.set(estado);
  }

  private terminarConError(motivo: MotivoError): void {
    this.detenerSondeo();
    this._motivoError.set(motivo);
    this.limpiarSensibles();
    this.validacion.reiniciar();
    this._estado.set('error');
  }

  private fallar(motivo: MotivoError): void {
    this._motivoError.set(motivo);
    this._estado.set('error');
  }

  // ---- sondeo ----

  private programarSondeo(indice: number): void {
    this.detenerTemporizador();
    if (indice >= this.intervalos.length) {
      this._sondeando.set(false);
      return;
    }
    const espera = this.intervalos[indice] - (indice === 0 ? 0 : this.intervalos[indice - 1]);
    const gen = this.generacion;
    this.temporizador = setTimeout(async () => {
      this.temporizador = null;
      await this.consultar();
      if (gen === this.generacion && this._estado() === 'pendiente' && this._sondeando()) this.programarSondeo(indice + 1);
    }, espera);
  }

  private detenerTemporizador(): void {
    if (this.temporizador !== null) {
      clearTimeout(this.temporizador);
      this.temporizador = null;
    }
  }

  private detenerSondeo(): void {
    this.detenerTemporizador();
    this._sondeando.set(false);
  }

  private async consultar(): Promise<void> {
    if (this._estado() !== 'pendiente' || this._consultando()) return;
    const upc = this._upc();
    const consulta = upc !== null ? { upc } : this.llave !== null ? { idempotencyKey: this.llave } : null;
    if (consulta === null) return;
    const gen = this.generacion;
    this._consultando.set(true);
    let salida: SalidaEstado;
    try {
      salida = await this.consultarEstado.ejecutar(consulta);
    } finally {
      this._consultando.set(false);
    }
    if (gen !== this.generacion || this._estado() !== 'pendiente') return;
    this.aplicarEstado(salida, upc !== null);
  }

  private aplicarEstado(salida: SalidaEstado, teniaUpc: boolean): void {
    switch (salida.tipo) {
      case 'pago': {
        this.noEncontrados = 0;
        this._sinConexion.set(false);
        const pago = salida.pago;
        if (pago.estatus === 'CONFIRMADA') {
          this.terminar('exitoso', pago);
        } else if (pago.estatus === 'RECHAZADA') {
          this._motivoRechazo.set(motivoRechazoDe(pago.motivo));
          this.terminar('rechazado', pago);
        } else if (pago.upc !== null) {
          this._upc.set(pago.upc);
        }
        return;
      }
      case 'noEncontrado':
        if (!teniaUpc && ++this.noEncontrados >= NO_ENCONTRADO_MAXIMO) {
          // El pago no llegó: se detiene el sondeo y se ofrece reintentarlo con la misma llave.
          this.detenerSondeo();
          this._noLlego.set(true);
        }
        return;
      case 'noAutenticado':
        this.detenerSondeo();
        return;
      case 'sinRed':
        this._sinConexion.set(true);
        return;
      default:
        return;
    }
  }
}

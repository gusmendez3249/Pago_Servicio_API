import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../../../core/session/session-store';
import { ValidacionReferenciaStore } from '../../../referencia/presentation/store/validacion-referencia.store';
import { CuentaCliente, ProductoPago, ResultadoPago, SalidaEstado, SalidaPago } from '../../domain/models/pago';
import { ReferenciaRepository } from '../../../referencia/domain/repository/referencia-repository';
import { PagoRepository } from '../../domain/repository/pago-repository';
import { INTERVALOS_SONDEO, PagoStore } from './pago.store';

const CUENTA: CuentaCliente = { numCuenta: '695429041188', numCuentaEnmascarada: '********1188', saldo: 1000 };
const TELEFONIA: ProductoPago = { idSer: 5, idProd: 7, servicio: 'Telcel', producto: 'Recarga $100', logoUrl: null, tipoFront: 1, precio: 100 };
const LUZ: ProductoPago = { idSer: 1, idProd: 2, servicio: 'CFE', producto: 'Luz', logoUrl: null, tipoFront: 2, precio: 5 };

function pago(p: Partial<ResultadoPago> = {}): ResultadoPago {
  return {
    numCuentaEnmascarada: '********1188', upc: 'K3J9X0A7ZQ', estatus: 'CONFIRMADA', motivo: null, monto: 100, saldoRestante: 900,
    folio: 'F123', idTx: null, identificadorOperacion: 'F123', pinServicio: null, leyenda: null, servicio: 'Telcel',
    producto: 'Recarga $100', referenciaEnmascarada: null, telefonoEnmascarado: '******4567', fechaTransaccion: null, ...p,
  };
}

describe('PagoStore', () => {
  let store: PagoStore;
  let pagar: ReturnType<typeof vi.fn>;
  let consultarEstado: ReturnType<typeof vi.fn>;
  let cuentasMias: ReturnType<typeof vi.fn>;
  let sesion: SessionStore;

  function preparar(opciones: { intervalos?: readonly number[] } = {}) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ReferenciaRepository, useValue: { validar: vi.fn() } },
        { provide: PagoRepository, useValue: { cuentasMias, pagar, consultarEstado } },
        { provide: INTERVALOS_SONDEO, useValue: opciones.intervalos ?? [10_000, 20_000, 35_000] },
      ],
    });
    sesion = TestBed.inject(SessionStore);
    sesion.iniciar({ token: 't', username: 'u', nombreCliente: 'N', rol: 'CLIENTE' });
    store = TestBed.inject(PagoStore);
  }

  beforeEach(() => {
    vi.useFakeTimers();
    cuentasMias = vi.fn().mockResolvedValue({ tipo: 'cuentas', cuentas: [CUENTA] });
    pagar = vi.fn().mockResolvedValue({ tipo: 'exitoso', pago: pago() } satisfies SalidaPago);
    consultarEstado = vi.fn().mockResolvedValue({ tipo: 'generico' } satisfies SalidaEstado);
  });

  afterEach(() => {
    sesion.cerrar();
    vi.useRealTimers();
  });

  /** Llega al modal de confirmación con un producto de teléfono. */
  async function hastaConfirmando(): Promise<void> {
    await store.iniciar({ producto: TELEFONIA });
    store.editarTelefono('5551234567');
    store.editarTelefonoConfirmacion('5551234567');
    store.continuarCaptura();
    store.pagar();
  }

  it('sin cuentas activas => error SIN_CUENTA', async () => {
    cuentasMias.mockResolvedValue({ tipo: 'cuentas', cuentas: [] });
    preparar();
    await store.iniciar({ producto: TELEFONIA });
    expect(store.estado()).toBe('error');
    expect(store.motivoError()).toBe('SIN_CUENTA');
  });

  it('teléfono: exige 10 dígitos y que ambos coincidan antes del resumen', async () => {
    preparar();
    await store.iniciar({ producto: TELEFONIA });
    expect(store.estado()).toBe('capturandoDatos');
    store.editarTelefono('555123');
    store.editarTelefonoConfirmacion('555123');
    store.continuarCaptura();
    expect(store.errorTelefono()).toBe('formato');
    store.editarTelefono('5551234567');
    store.editarTelefonoConfirmacion('5551234568');
    store.continuarCaptura();
    expect(store.errorTelefono()).toBe('noCoinciden');
    expect(store.estado()).toBe('capturandoDatos');
    store.editarTelefonoConfirmacion('5551234567');
    store.continuarCaptura();
    expect(store.estado()).toBe('resumen');
    expect(store.resumen()).toMatchObject({ total: 100, saldoDespues: 900, saldoInsuficiente: false });
  });

  it('referencia EN_LINEA: no captura nada, va al resumen con montoAPagar', async () => {
    preparar();
    await store.iniciar({ producto: LUZ, referencia: { referencia: 'ABC', idValidacion: 'u1', verificacion: 'EN_LINEA', montoAPagar: 250.5 } });
    expect(store.estado()).toBe('resumen');
    expect(store.total()).toBe(250.5);
  });

  it('referencia SOLO_FORMATO: captura el importe con límites', async () => {
    preparar();
    await store.iniciar({ producto: LUZ, referencia: { referencia: 'ABC', idValidacion: 'u1', verificacion: 'SOLO_FORMATO', montoAPagar: null } });
    expect(store.estado()).toBe('capturandoDatos');
    store.editarImporte('');
    store.continuarCaptura();
    expect(store.errorImporte()).toBe('vacio');
    store.editarImporte('10000.01');
    store.continuarCaptura();
    expect(store.errorImporte()).toBe('rango');
    store.editarImporte('1234.56');
    store.continuarCaptura();
    expect(store.estado()).toBe('resumen');
    expect(store.total()).toBe(1234.56);
  });

  it('saldo insuficiente deshabilita "Pagar"', async () => {
    cuentasMias.mockResolvedValue({ tipo: 'cuentas', cuentas: [{ ...CUENTA, saldo: 50 }] });
    preparar();
    await hastaConfirmando();
    expect(store.resumen()?.saldoInsuficiente).toBe(true);
    expect(store.estado()).toBe('resumen');
  });

  it('doble clic en "Confirmar": una sola petición', async () => {
    let resolver!: (s: SalidaPago) => void;
    pagar.mockReturnValue(new Promise<SalidaPago>((r) => (resolver = r)));
    preparar();
    await hastaConfirmando();
    const a = store.confirmar('1234');
    const b = store.confirmar('1234');
    expect(store.estado()).toBe('procesando');
    resolver({ tipo: 'exitoso', pago: pago() });
    await Promise.all([a, b]);
    expect(pagar).toHaveBeenCalledTimes(1);
    expect(store.estado()).toBe('exitoso');
  });

  it('envía el cuerpo esperado con la llave y el PIN solo en la solicitud', async () => {
    preparar();
    await hastaConfirmando();
    await store.confirmar('4321');
    const [solicitud, llave] = pagar.mock.calls[0];
    expect(solicitud).toEqual({ numCuenta: '695429041188', idSer: 5, idProd: 7, monto: 100, twoFA: '4321', esReferencia: false, telefono: '5551234567' });
    expect(llave).toMatch(/^[0-9a-f-]{36}$/);
    expect(JSON.stringify(store.resultado())).not.toContain('4321');
  });

  it('referencia: envía reference e idValidacion', async () => {
    preparar();
    await store.iniciar({ producto: LUZ, referencia: { referencia: 'ABC', idValidacion: 'uuid-1', verificacion: 'EN_LINEA', montoAPagar: 80 } });
    store.pagar();
    await store.confirmar('1234');
    expect(pagar.mock.calls[0][0]).toEqual({ numCuenta: '695429041188', idSer: 1, idProd: 2, monto: 80, twoFA: '1234', esReferencia: true, reference: 'ABC', idValidacion: 'uuid-1' });
  });

  it('PIN incorrecto: vuelve a confirmando conservando resumen y llave', async () => {
    preparar();
    await hastaConfirmando();
    pagar.mockResolvedValueOnce({ tipo: 'pinIncorrecto', intentosRestantes: 2 });
    await store.confirmar('1111');
    expect(store.estado()).toBe('confirmando');
    expect(store.avisoPin()).toBe('PIN incorrecto. Te quedan 2 intentos.');
    expect(store.resumen()).not.toBeNull();
    await store.confirmar('2222');
    expect(pagar.mock.calls[1][1]).toBe(pagar.mock.calls[0][1]);
  });

  it('423 => error PIN_BLOQUEADO; 428 => VALIDACION_EXPIRADA; otros => GENERICO', async () => {
    const casos: [SalidaPago, string][] = [
      [{ tipo: 'pinBloqueado' }, 'PIN_BLOQUEADO'],
      [{ tipo: 'validacionRequerida' }, 'VALIDACION_EXPIRADA'],
      [{ tipo: 'generico' }, 'GENERICO'],
    ];
    for (const [salida, motivo] of casos) {
      TestBed.resetTestingModule();
      pagar.mockResolvedValue(salida);
      preparar();
      await hastaConfirmando();
      await store.confirmar('1234');
      expect(store.estado()).toBe('error');
      expect(store.motivoError()).toBe(motivo);
      sesion.cerrar();
    }
  });

  it('400 saldo insuficiente => resumen con el saldo refrescado', async () => {
    preparar();
    await hastaConfirmando();
    pagar.mockResolvedValueOnce({ tipo: 'saldoInsuficiente' });
    cuentasMias.mockResolvedValue({ tipo: 'cuentas', cuentas: [{ ...CUENTA, saldo: 30 }] });
    await store.confirmar('1234');
    expect(store.estado()).toBe('resumen');
    expect(store.resumen()?.saldo).toBe(30);
    expect(store.resumen()?.saldoInsuficiente).toBe(true);
  });

  it('422 / 5xx => rechazado con motivo; borra llave y datos', async () => {
    preparar();
    await hastaConfirmando();
    pagar.mockResolvedValueOnce({ tipo: 'rechazado', motivo: 'SERVICIO_NO_DISPONIBLE', pago: null });
    await store.confirmar('1234');
    expect(store.estado()).toBe('rechazado');
    expect(store.motivoRechazo()).toBe('SERVICIO_NO_DISPONIBLE');
    expect(store.cuentas()).toEqual([]);
    expect(store.telefono()).toBe('');
  });

  it('timeout: pendiente sin upc, consulta por llave, nunca llave nueva', async () => {
    pagar.mockResolvedValue({ tipo: 'sinRed' });
    preparar();
    await hastaConfirmando();
    await store.confirmar('1234');
    expect(store.estado()).toBe('pendiente');
    expect(store.sinConexion()).toBe(true);
    const llave = pagar.mock.calls[0][1];
    await vi.advanceTimersByTimeAsync(10_001);
    expect(consultarEstado).toHaveBeenCalledWith({ idempotencyKey: llave });
    await store.consultarAhora();
    expect(consultarEstado).toHaveBeenLastCalledWith({ idempotencyKey: llave });
    expect(pagar).toHaveBeenCalledTimes(1);
  });

  it('5xx incierto (pendiente sin upc): conserva la llave, consulta por llave y no descarta nada', async () => {
    pagar.mockResolvedValue({ tipo: 'pendiente', pago: null });
    preparar();
    await hastaConfirmando();
    await store.confirmar('1234');
    expect(store.estado()).toBe('pendiente');
    expect(store.sinConexion()).toBe(false);
    const llave = pagar.mock.calls[0][1];
    await store.consultarAhora();
    expect(consultarEstado).toHaveBeenLastCalledWith({ idempotencyKey: llave });
    expect(store.cuentas().length).toBe(1);
  });

  it('cambiar la cuenta de cargo en el resumen genera una llave nueva; reelegir la misma no', async () => {
    const otra: CuentaCliente = { numCuenta: '111122223333', numCuentaEnmascarada: '********3333', saldo: 500 };
    cuentasMias.mockResolvedValue({ tipo: 'cuentas', cuentas: [CUENTA, otra] });
    preparar();
    await hastaConfirmando();
    pagar.mockResolvedValue({ tipo: 'pinIncorrecto', intentosRestantes: 2 });
    await store.confirmar('1234');
    const llave1 = pagar.mock.calls[0][1];
    store.cancelarConfirmacion();
    store.seleccionarCuenta(CUENTA.numCuenta);
    store.pagar();
    await store.confirmar('1234');
    expect(pagar.mock.calls[1][1]).toBe(llave1);
    store.cancelarConfirmacion();
    store.seleccionarCuenta(otra.numCuenta);
    store.pagar();
    await store.confirmar('1234');
    expect(pagar.mock.calls[2][1]).not.toBe(llave1);
    expect(pagar.mock.calls[2][0].numCuenta).toBe(otra.numCuenta);
  });

  it('202: pendiente con upc que pasa a CONFIRMADA en el sondeo y muestra folio', async () => {
    pagar.mockResolvedValue({ tipo: 'pendiente', pago: pago({ estatus: 'INDETERMINADA', folio: null }) });
    consultarEstado
      .mockResolvedValueOnce({ tipo: 'pago', pago: pago({ estatus: 'PENDIENTE', folio: null }) })
      .mockResolvedValueOnce({ tipo: 'pago', pago: pago({ folio: 'FOLIO9' }) });
    preparar();
    await hastaConfirmando();
    await store.confirmar('1234');
    expect(store.estado()).toBe('pendiente');
    expect(store.upc()).toBe('K3J9X0A7ZQ');
    await vi.advanceTimersByTimeAsync(10_001);
    expect(consultarEstado).toHaveBeenLastCalledWith({ upc: 'K3J9X0A7ZQ' });
    expect(store.estado()).toBe('pendiente');
    await vi.advanceTimersByTimeAsync(10_001);
    expect(store.estado()).toBe('exitoso');
    expect(store.resultado()?.folio).toBe('FOLIO9');
    expect(store.sondeando()).toBe(false);
    // no hay más consultas después del éxito
    await vi.advanceTimersByTimeAsync(60_000);
    expect(consultarEstado).toHaveBeenCalledTimes(2);
  });

  it('sondeo que devuelve RECHAZADA => rechazado', async () => {
    pagar.mockResolvedValue({ tipo: 'pendiente', pago: pago({ estatus: 'INDETERMINADA' }) });
    consultarEstado.mockResolvedValue({ tipo: 'pago', pago: pago({ estatus: 'RECHAZADA', motivo: 'RECHAZADO_PROVEEDOR' }) });
    preparar();
    await hastaConfirmando();
    await store.confirmar('1234');
    await vi.advanceTimersByTimeAsync(10_001);
    expect(store.estado()).toBe('rechazado');
  });

  it('agota las consultas y queda en pendiente; salir detiene el sondeo', async () => {
    pagar.mockResolvedValue({ tipo: 'pendiente', pago: pago({ estatus: 'INDETERMINADA' }) });
    preparar({ intervalos: [1000, 2000] });
    await hastaConfirmando();
    await store.confirmar('1234');
    await vi.advanceTimersByTimeAsync(10_000);
    expect(consultarEstado).toHaveBeenCalledTimes(2);
    expect(store.estado()).toBe('pendiente');
    expect(store.sondeando()).toBe(false);
  });

  it('salir de la pantalla detiene el sondeo y borra todo', async () => {
    pagar.mockResolvedValue({ tipo: 'pendiente', pago: pago({ estatus: 'INDETERMINADA' }) });
    preparar();
    await hastaConfirmando();
    await store.confirmar('1234');
    store.salir();
    await vi.advanceTimersByTimeAsync(60_000);
    expect(consultarEstado).not.toHaveBeenCalled();
    expect(store.resultado()).toBeNull();
    expect(store.cuentas()).toEqual([]);
  });

  it('tres 404 por llave: ofrece reintentar con la MISMA llave', async () => {
    pagar.mockResolvedValue({ tipo: 'sinRed' });
    consultarEstado.mockResolvedValue({ tipo: 'noEncontrado' });
    preparar();
    await hastaConfirmando();
    await store.confirmar('1234');
    const llave = pagar.mock.calls[0][1];
    await store.consultarAhora();
    await store.consultarAhora();
    expect(store.noLlego()).toBe(false);
    await store.consultarAhora();
    expect(store.noLlego()).toBe(true);
    store.reintentarPago();
    expect(store.estado()).toBe('confirmando');
    pagar.mockResolvedValue({ tipo: 'exitoso', pago: pago() });
    await store.confirmar('1234');
    expect(pagar.mock.calls[1][1]).toBe(llave);
  });

  it('401 en la consulta detiene el sondeo', async () => {
    pagar.mockResolvedValue({ tipo: 'pendiente', pago: pago({ estatus: 'INDETERMINADA' }) });
    consultarEstado.mockResolvedValue({ tipo: 'noAutenticado' });
    preparar();
    await hastaConfirmando();
    await store.confirmar('1234');
    await vi.advanceTimersByTimeAsync(60_000);
    expect(consultarEstado).toHaveBeenCalledTimes(1);
    expect(store.sondeando()).toBe(false);
  });

  it('exitoso invalida la validación de referencia y no escribe en ningún storage', async () => {
    const escritura = vi.spyOn(Storage.prototype, 'setItem');
    preparar();
    const validacion = TestBed.inject(ValidacionReferenciaStore);
    const reiniciar = vi.spyOn(validacion, 'reiniciar');
    await store.iniciar({ producto: LUZ, referencia: { referencia: 'ABC', idValidacion: 'u1', verificacion: 'EN_LINEA', montoAPagar: 80 } });
    store.pagar();
    await store.confirmar('1234');
    expect(store.estado()).toBe('exitoso');
    expect(reiniciar).toHaveBeenCalled();
    expect(store.totalMostrado()).toBe(100);
    expect(escritura).not.toHaveBeenCalled();
    expect(localStorage.length + sessionStorage.length).toBe(0);
    escritura.mockRestore();
  });

  it('solo se confirma desde confirmando y con PIN de 4 dígitos', async () => {
    preparar();
    await store.iniciar({ producto: TELEFONIA });
    await store.confirmar('1234');
    expect(pagar).not.toHaveBeenCalled();
    await hastaConfirmando();
    await store.confirmar('12');
    expect(pagar).not.toHaveBeenCalled();
  });
});

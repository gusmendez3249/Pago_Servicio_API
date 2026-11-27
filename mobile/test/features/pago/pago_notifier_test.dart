import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/core/http/result.dart';
import 'package:gesto_pago_mobile/features/pago/domain/entities/comprobante_pago.dart';
import 'package:gesto_pago_mobile/features/pago/domain/entities/producto_pago.dart';
import 'package:gesto_pago_mobile/features/pago/domain/usecases/pago_usecases.dart';
import 'package:gesto_pago_mobile/features/pago/presentation/providers/pago_notifier.dart';
import 'package:gesto_pago_mobile/features/pago/presentation/providers/pago_state.dart';
import 'package:gesto_pago_mobile/features/referencia/domain/entities/validacion_referencia.dart';

import 'repositorio_falso.dart';

const _telefono = ProductoPago(
  idServicio: 1,
  idProducto: 2,
  tipoFront: 1,
  servicio: 'Telcel',
  producto: 'Recarga \$100',
  precio: 100,
);

const _cfe = ProductoPago(
  idServicio: 5,
  idProducto: 6,
  tipoFront: 2,
  servicio: 'CFE',
  producto: 'Luz',
);

DatosPagoReferencia _datos({
  VerificacionReferencia verificacion = VerificacionReferencia.enLinea,
  double? monto = 350,
}) {
  return DatosPagoReferencia(
    idSer: 5,
    idProd: 6,
    referencia: '0012345678',
    idValidacion: 'uuid-validacion',
    saldo: monto,
    situacion: SituacionSaldo.adeudo,
    servicio: 'CFE',
    producto: 'Luz',
    expiraEn: DateTime(2100),
    verificacion: verificacion,
    montoAPagar: monto,
  );
}

class _Entorno {
  _Entorno(this.repo, {List<Duration>? intervalos}) {
    var n = 0;
    notifier = PagoNotifier(
      obtenerCuentas: ObtenerCuentasUseCase(repo),
      pagar: PagarServicioUseCase(repo),
      consultarEstado: ConsultarEstadoPagoUseCase(repo),
      intervalosSondeo: intervalos ??
          const [
            Duration(milliseconds: 10),
            Duration(milliseconds: 20),
            Duration(milliseconds: 30),
          ],
      generarLlave: () => 'llave-${++n}',
      alTerminar: () => terminados++,
    );
  }

  final RepoPagoFalso repo;
  late final PagoNotifier notifier;
  int terminados = 0;

  PagoState get e => notifier.state;

  Future<void> hastaResumenTelefono() async {
    await notifier.iniciar(_telefono);
    notifier.capturarTelefono('5512345678', '5512345678');
  }

  Future<void> pagarConPin([String pin = '1234']) async {
    notifier.pedirConfirmacion();
    await notifier.confirmar(pin);
  }

  void cerrar() => notifier.dispose();
}

Future<void> _esperar(bool Function() condicion) async {
  for (var i = 0; i < 200 && !condicion(); i++) {
    await Future<void>.delayed(const Duration(milliseconds: 5));
  }
}

void main() {
  group('preparación', () {
    test('sin cuentas termina en error sinCuenta', () async {
      final t = _Entorno(RepoPagoFalso(cuentas: const []));
      await t.notifier.iniciar(_telefono);
      expect(t.e.fase, FasePago.error);
      expect(t.e.error, ErrorPago.sinCuenta);
      t.cerrar();
    });

    test('teléfono: captura dos veces y pasa al resumen con el precio', () async {
      final t = _Entorno(RepoPagoFalso());
      await t.notifier.iniciar(_telefono);
      expect(t.e.fase, FasePago.capturandoDatos);
      expect(t.e.captura, CapturaPago.telefono);

      t.notifier.capturarTelefono('5512345678', '5512345679');
      expect(t.e.errorTelefono, isNotNull);
      expect(t.e.fase, FasePago.capturandoDatos);

      t.notifier.capturarTelefono('5512345678', '5512345678');
      expect(t.e.fase, FasePago.resumen);
      expect(t.e.total, 100);
      expect(t.e.llave, 'llave-1');
      expect(t.e.resumen!.saldoDespues, 900);
      t.cerrar();
    });

    test('referencia EN_LINEA: total = montoAPagar, directo al resumen', () async {
      final t = _Entorno(RepoPagoFalso());
      await t.notifier.iniciar(_cfe, datosReferencia: _datos());
      expect(t.e.fase, FasePago.resumen);
      expect(t.e.total, 350);
      expect(t.e.esReferencia, isTrue);
      t.cerrar();
    });

    test('referencia SOLO_FORMATO: captura el importe una vez con sus límites', () async {
      final t = _Entorno(RepoPagoFalso());
      await t.notifier.iniciar(
        _cfe,
        datosReferencia: _datos(
          verificacion: VerificacionReferencia.soloFormato,
          monto: null,
        ),
      );
      expect(t.e.captura, CapturaPago.importe);
      t.notifier.capturarImporte('0.50');
      expect(t.e.errorImporte, isNotNull);
      t.notifier.capturarImporte('10000.01');
      expect(t.e.fase, FasePago.capturandoDatos);
      t.notifier.capturarImporte('10000');
      expect(t.e.fase, FasePago.resumen);
      expect(t.e.total, 10000);
      t.cerrar();
    });

    test('con saldo insuficiente "Pagar" no avanza', () async {
      final t = _Entorno(RepoPagoFalso(cuentas: const [cuentaPobre]));
      await t.hastaResumenTelefono();
      expect(t.e.saldoSuficiente, isFalse);
      t.notifier.pedirConfirmacion();
      expect(t.e.fase, FasePago.resumen);
      t.cerrar();
    });
  });

  group('envío del pago', () {
    test('doble confirmar produce una sola petición', () async {
      final repo = RepoPagoFalso()
        ..compuertaPago = Completer<void>()
        ..respuestasPago = [ok200(comprobante(EstatusPago.confirmada, folio: 'F1'))];
      final t = _Entorno(repo);
      await t.hastaResumenTelefono();
      t.notifier.pedirConfirmacion();
      final a = t.notifier.confirmar('1234');
      final b = t.notifier.confirmar('1234');
      expect(t.e.fase, FasePago.procesando);
      expect(t.e.enVuelo, isTrue);
      repo.compuertaPago!.complete();
      await Future.wait([a, b]);
      expect(repo.pagos, hasLength(1));
      expect(t.e.fase, FasePago.exitoso);
      t.cerrar();
    });

    test('confirmar solo se acepta desde confirmando', () async {
      final repo = RepoPagoFalso()
        ..respuestasPago = [ok200(comprobante(EstatusPago.confirmada))];
      final t = _Entorno(repo);
      await t.hastaResumenTelefono();
      await t.notifier.confirmar('1234');
      expect(repo.pagos, isEmpty);
      t.cerrar();
    });

    test('envía la solicitud correcta y la llave del resumen', () async {
      final repo = RepoPagoFalso()
        ..respuestasPago = [ok200(comprobante(EstatusPago.confirmada, folio: 'F1'))];
      final t = _Entorno(repo);
      await t.hastaResumenTelefono();
      await t.pagarConPin('4321');
      final s = repo.pagos.single;
      expect(s.numCuenta, '695429041188');
      expect(s.telefono, '5512345678');
      expect(s.reference, isNull);
      expect(s.monto, 100);
      expect(s.twoFA, '4321');
      expect(repo.llaves.single, 'llave-1');
      t.cerrar();
    });

    test('200 CONFIRMADA: exitoso con folio y se borran los datos sensibles', () async {
      final repo = RepoPagoFalso()
        ..respuestasPago = [
          ok200(comprobante(EstatusPago.confirmada, folio: 'F123', pinServicio: 'ABCD-1234')),
        ];
      final t = _Entorno(repo);
      await t.notifier.iniciar(_cfe, datosReferencia: _datos());
      await t.pagarConPin();
      expect(t.e.fase, FasePago.exitoso);
      expect(t.e.comprobante!.folio, 'F123');
      expect(t.e.comprobante!.pinServicio, 'ABCD-1234');
      expect(t.e.cuentas, isEmpty);
      expect(t.e.idValidacion, isEmpty);
      expect(t.e.referencia, isEmpty);
      expect(t.e.telefono, isEmpty);
      expect(t.e.llave, isNull);
      expect(t.e.importe, isNull);
      expect(t.terminados, 1);
      t.cerrar();
    });

    test('422: rechazado con motivo y datos borrados', () async {
      final repo = RepoPagoFalso()
        ..respuestasPago = [
          fallo(
            422,
            comprobante: comprobante(EstatusPago.rechazada, motivo: MotivoPago.referenciaInvalida),
          ),
        ];
      final t = _Entorno(repo);
      await t.notifier.iniciar(_cfe, datosReferencia: _datos());
      await t.pagarConPin();
      expect(t.e.fase, FasePago.rechazado);
      expect(t.e.motivoRechazo, MotivoPago.referenciaInvalida);
      expect(t.e.idValidacion, isEmpty);
      expect(t.terminados, 1);
      t.cerrar();
    });

    test('503 sin motivo: pendiente con la misma llave y consulta por llave', () async {
      final repo = RepoPagoFalso()..respuestasPago = [fallo(503)];
      final t = _Entorno(repo);
      await t.hastaResumenTelefono();
      final llave = t.e.llave;
      await t.pagarConPin();
      expect(t.e.fase, FasePago.pendiente);
      expect(t.e.llave, llave);
      expect(t.e.motivoRechazo, isNull);
      t.cerrar();
    });

    test('503 con PROVEEDOR_NO_DISPONIBLE: rechazado', () async {
      final repo = RepoPagoFalso()
        ..respuestasPago = [fallo(503, motivo: MotivoPago.proveedorNoDisponible)];
      final t = _Entorno(repo);
      await t.hastaResumenTelefono();
      await t.pagarConPin();
      expect(t.e.fase, FasePago.rechazado);
      expect(t.e.motivoRechazo, MotivoPago.proveedorNoDisponible);
      t.cerrar();
    });

    test('rechazado en teléfono: reiniciar da llave nueva', () async {
      final repo = RepoPagoFalso()
        ..respuestasPago = [fallo(503, motivo: MotivoPago.proveedorNoDisponible)];
      final t = _Entorno(repo);
      await t.hastaResumenTelefono();
      await t.pagarConPin();
      await t.notifier.reiniciar();
      expect(t.e.fase, FasePago.capturandoDatos);
      t.notifier.capturarTelefono('5512345678', '5512345678');
      expect(t.e.llave, 'llave-2');
      t.cerrar();
    });

    test('PIN incorrecto: vuelve a confirmando conservando resumen y llave', () async {
      final repo = RepoPagoFalso()
        ..respuestasPago = [
          fallo(403, motivo: MotivoPago.pinIncorrecto, intentos: 2),
          ok200(comprobante(EstatusPago.confirmada, folio: 'F1')),
        ];
      final t = _Entorno(repo);
      await t.hastaResumenTelefono();
      await t.pagarConPin('0000');
      expect(t.e.fase, FasePago.confirmando);
      expect(t.e.avisoPin, isTrue);
      expect(t.e.intentosRestantes, 2);
      expect(t.e.llave, 'llave-1');
      expect(t.e.total, 100);
      expect(t.e.enVuelo, isFalse);
      await t.notifier.confirmar('1234');
      expect(repo.llaves, ['llave-1', 'llave-1']);
      expect(t.e.fase, FasePago.exitoso);
      t.cerrar();
    });

    test('cancelar el PIN vuelve al resumen', () async {
      final t = _Entorno(RepoPagoFalso());
      await t.hastaResumenTelefono();
      t.notifier.pedirConfirmacion();
      t.notifier.cancelarConfirmacion();
      expect(t.e.fase, FasePago.resumen);
      t.cerrar();
    });

    test('423: error PIN_BLOQUEADO, fin del flujo', () async {
      final repo = RepoPagoFalso()
        ..respuestasPago = [fallo(423, motivo: MotivoPago.pinBloqueado)];
      final t = _Entorno(repo);
      await t.hastaResumenTelefono();
      await t.pagarConPin();
      expect(t.e.fase, FasePago.error);
      expect(t.e.error, ErrorPago.pinBloqueado);
      expect(t.e.cuentas, isEmpty);
      t.cerrar();
    });

    test('428: error de validación expirada', () async {
      final repo = RepoPagoFalso()
        ..respuestasPago = [fallo(428, motivo: MotivoPago.validacionRequerida)];
      final t = _Entorno(repo);
      await t.notifier.iniciar(_cfe, datosReferencia: _datos());
      await t.pagarConPin();
      expect(t.e.error, ErrorPago.validacionExpirada);
      expect(t.terminados, 1);
      t.cerrar();
    });

    test('400 saldo insuficiente: resumen con saldo refrescado y Pagar deshabilitado', () async {
      final repo = RepoPagoFalso()
        ..respuestasPago = [fallo(400, motivo: MotivoPago.saldoInsuficienteCuenta)];
      final t = _Entorno(repo);
      await t.hastaResumenTelefono();
      repo.cuentas = const [cuentaPobre];
      await t.pagarConPin();
      await _esperar(() => t.e.cuentas.first.saldo == 10);
      expect(t.e.fase, FasePago.resumen);
      expect(t.e.cuentas.first.saldo, 10);
      expect(t.e.saldoSuficiente, isFalse);
      t.notifier.pedirConfirmacion();
      expect(t.e.fase, FasePago.resumen);
      t.cerrar();
    });

    test('otros (400, 403, 404, 409): error genérico', () async {
      for (final s in [400, 403, 404, 409]) {
        final repo = RepoPagoFalso()..respuestasPago = [fallo(s)];
        final t = _Entorno(repo);
        await t.hastaResumenTelefono();
        await t.pagarConPin();
        expect(t.e.error, ErrorPago.generico, reason: '$s');
        t.cerrar();
      }
    });
  });

  group('pendiente y sondeo', () {
    test('timeout: pendiente sin upc, consulta por llave y NUNCA llave nueva', () async {
      final repo = RepoPagoFalso()
        ..respuestasPago = [sinRed]
        ..respuestasEstado = [
          fallo(404, tipo: TipoFallo.noEncontrado),
          ok200(comprobante(EstatusPago.confirmada, folio: 'F9', upc: null)),
        ];
      final t = _Entorno(repo);
      await t.hastaResumenTelefono();
      await t.pagarConPin();
      expect(t.e.fase, FasePago.pendiente);
      expect(t.e.sinConexion, isTrue);
      expect(t.e.upc, isNull);
      await _esperar(() => t.e.fase == FasePago.exitoso);
      expect(t.e.fase, FasePago.exitoso);
      expect(t.e.comprobante!.folio, 'F9');
      expect(repo.consultas.every((c) => c.upc == null && c.llave == 'llave-1'), isTrue);
      expect(repo.pagos, hasLength(1));
      t.cerrar();
    });

    test('202 con upc que pasa a CONFIRMADA en el sondeo muestra folio', () async {
      final repo = RepoPagoFalso()
        ..respuestasPago = [ok202(comprobante(EstatusPago.indeterminada))]
        ..respuestasEstado = [
          ok200(comprobante(EstatusPago.indeterminada)),
          ok200(comprobante(EstatusPago.confirmada, folio: 'F77')),
        ];
      final t = _Entorno(repo);
      await t.hastaResumenTelefono();
      await t.pagarConPin();
      expect(t.e.fase, FasePago.pendiente);
      expect(t.e.upc, 'K3J9X0A7ZQ');
      expect(t.e.sondeando, isTrue);
      await _esperar(() => t.e.fase == FasePago.exitoso);
      expect(t.e.comprobante!.folio, 'F77');
      expect(repo.consultas.first.upc, 'K3J9X0A7ZQ');
      expect(repo.consultas.first.llave, isNull);
      expect(t.terminados, 1);
      t.cerrar();
    });

    test('RECHAZADA en el sondeo pasa a rechazado', () async {
      final repo = RepoPagoFalso()
        ..respuestasPago = [ok202(comprobante(EstatusPago.indeterminada))]
        ..respuestasEstado = [
          ok200(comprobante(EstatusPago.rechazada, motivo: MotivoPago.rechazadoProveedor)),
        ];
      final t = _Entorno(repo);
      await t.hastaResumenTelefono();
      await t.pagarConPin();
      await _esperar(() => t.e.fase == FasePago.rechazado);
      expect(t.e.fase, FasePago.rechazado);
      t.cerrar();
    });

    test('agotadas las consultas queda pendiente sin sondear; consultar de nuevo reanuda', () async {
      final repo = RepoPagoFalso()
        ..respuestasPago = [ok202(comprobante(EstatusPago.pendiente))]
        ..respuestasEstado = [ok200(comprobante(EstatusPago.pendiente))];
      final t = _Entorno(repo);
      await t.hastaResumenTelefono();
      await t.pagarConPin();
      await _esperar(() => !t.e.sondeando);
      expect(t.e.fase, FasePago.pendiente);
      expect(t.e.sondeando, isFalse);
      expect(repo.consultas, hasLength(3));
      repo.respuestasEstado = [ok200(comprobante(EstatusPago.confirmada, folio: 'F5'))];
      await t.notifier.consultarDeNuevo();
      expect(t.e.fase, FasePago.exitoso);
      t.cerrar();
    });

    test('tres 404 por llave: no llegó; reintentar pide PIN y usa la MISMA llave', () async {
      final repo = RepoPagoFalso()
        ..respuestasPago = [sinRed, ok200(comprobante(EstatusPago.confirmada, folio: 'F2'))]
        ..respuestasEstado = [fallo(404, tipo: TipoFallo.noEncontrado)];
      final t = _Entorno(repo);
      await t.hastaResumenTelefono();
      await t.pagarConPin();
      await _esperar(() => t.e.pagoNoLlego);
      expect(t.e.pagoNoLlego, isTrue);
      expect(t.e.sondeando, isFalse);
      expect(repo.consultas, hasLength(3));

      t.notifier.reintentarPago();
      expect(t.e.fase, FasePago.confirmando);
      await t.notifier.confirmar('1234');
      expect(repo.llaves, ['llave-1', 'llave-1']);
      expect(t.e.fase, FasePago.exitoso);
      t.cerrar();
    });

    test('cancelar el PIN del reintento vuelve a pendiente', () async {
      final repo = RepoPagoFalso()
        ..respuestasPago = [sinRed]
        ..respuestasEstado = [fallo(404, tipo: TipoFallo.noEncontrado)];
      final t = _Entorno(repo);
      await t.hastaResumenTelefono();
      await t.pagarConPin();
      await _esperar(() => t.e.pagoNoLlego);
      t.notifier.reintentarPago();
      t.notifier.cancelarConfirmacion();
      expect(t.e.fase, FasePago.pendiente);
      t.cerrar();
    });

    test('dispose cancela el sondeo (no hay más consultas)', () async {
      final repo = RepoPagoFalso()
        ..respuestasPago = [ok202(comprobante(EstatusPago.pendiente))]
        ..respuestasEstado = [ok200(comprobante(EstatusPago.pendiente))];
      final t = _Entorno(
        repo,
        intervalos: const [Duration(milliseconds: 30), Duration(milliseconds: 60)],
      );
      await t.hastaResumenTelefono();
      await t.pagarConPin();
      t.cerrar();
      await Future<void>.delayed(const Duration(milliseconds: 120));
      expect(repo.consultas, isEmpty);
    });
  });

  test('el estado nunca contiene el PIN', () async {
    final repo = RepoPagoFalso()..respuestasPago = [sinRed];
    final t = _Entorno(repo);
    await t.hastaResumenTelefono();
    await t.pagarConPin('7391');
    final volcado = [
      t.e.referencia,
      t.e.telefono,
      t.e.llave,
      t.e.comprobante?.upc,
      t.e.toString(),
    ].join('|');
    expect(volcado.contains('7391'), isFalse);
    t.cerrar();
  });
}

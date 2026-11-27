import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/core/http/result.dart';
import 'package:gesto_pago_mobile/features/referencia/domain/entities/validacion_referencia.dart';
import 'package:gesto_pago_mobile/features/referencia/domain/repositories/referencia_repository.dart';
import 'package:gesto_pago_mobile/features/referencia/domain/usecases/validar_referencia_usecase.dart';
import 'package:gesto_pago_mobile/features/referencia/domain/utils/formato_referencia.dart';
import 'package:gesto_pago_mobile/features/referencia/presentation/providers/referencia_providers.dart';
import 'package:gesto_pago_mobile/features/referencia/presentation/textos_referencia.dart';
import 'package:gesto_pago_mobile/features/referencia/presentation/widgets/tarjeta_resultado_referencia.dart';

class _RepoFalso implements ReferenciaRepository {
  _RepoFalso(this.resultado);

  Result<ValidacionReferencia> resultado;
  int llamadas = 0;
  String? ultimaReferencia;

  /// Si no es null, la respuesta se retiene hasta completarlo.
  Completer<void>? compuerta;

  @override
  Future<Result<ValidacionReferencia>> validar({
    required int idSer,
    required int idProd,
    required String referencia,
  }) async {
    llamadas++;
    ultimaReferencia = referencia;
    final c = compuerta;
    if (c != null) await c.future;
    return resultado;
  }
}

Result<ValidacionReferencia> _valida({
  String situacion = 'ADEUDO',
  double saldo = 350,
  bool puedePagar = true,
  String? idValidacion = 'uuid-1',
  int? vigencia = 300,
  double? montoAPagar,
}) {
  return Ok<ValidacionReferencia>(
    ValidacionReferencia(
      estado: EstadoValidacion.valida,
      situacion: SituacionSaldo.desdeTexto(situacion),
      saldo: saldo,
      idValidacion: idValidacion,
      vigenciaSegundos: vigencia,
      puedePagar: puedePagar,
      montoAPagar: montoAPagar ?? (puedePagar ? saldo : null),
      idSer: 1,
      idProd: 2,
      servicio: 'CFE',
      producto: 'Luz',
    ),
  );
}

Result<ValidacionReferencia> _soloFormato({
  bool puedePagar = true,
  String? idValidacion = 'uuid-2',
}) {
  return Ok<ValidacionReferencia>(
    ValidacionReferencia(
      estado: EstadoValidacion.valida,
      verificacion: VerificacionReferencia.soloFormato,
      puedePagar: puedePagar,
      idValidacion: idValidacion,
      vigenciaSegundos: 300,
      idSer: 1,
      idProd: 2,
      servicio: 'Agua',
      producto: 'Recibo',
    ),
  );
}

const _invalida = Ok<ValidacionReferencia>(
  ValidacionReferencia(
    estado: EstadoValidacion.invalida,
    motivo: MotivoValidacion.referenciaInvalida,
  ),
);

Failure _fallo(TipoFallo tipo) => Failure(
      tipo: tipo,
      mensaje: 'detalle tecnico del servidor: token API 502',
      httpStatus: 502,
    );

ReferenciaNotifier _crear(
  _RepoFalso repo, {
  Duration? vigencia,
  DateTime Function()? reloj,
}) {
  final n = ReferenciaNotifier(
    ValidarReferenciaUseCase(repo),
    reloj: reloj,
    vigenciaFija: vigencia,
  );
  n.iniciar(1, 2);
  return n;
}

void main() {
  test('estado inicial es capturando y no se puede continuar', () {
    final n = _crear(_RepoFalso(_valida()));
    expect(n.state.estado, EstadoReferencia.capturando);
    expect(n.state.puedeContinuar, isFalse);
    n.dispose();
  });

  test('formato incorrecto no llama al backend', () async {
    final repo = _RepoFalso(_valida());
    final n = _crear(repo);
    n.editar('abc-123');
    await n.validar();
    expect(n.state.estado, EstadoReferencia.formatoInvalido);
    expect(n.state.errorFormato, ErrorFormatoReferencia.invalida);
    expect(repo.llamadas, 0);
    n.dispose();
  });

  test('referencia vacia da formatoInvalido (vacia)', () async {
    final repo = _RepoFalso(_valida());
    final n = _crear(repo);
    await n.validar();
    expect(n.state.estado, EstadoReferencia.formatoInvalido);
    expect(n.state.errorFormato, ErrorFormatoReferencia.vacia);
    expect(repo.llamadas, 0);
    n.dispose();
  });

  test('envia la referencia recortada', () async {
    final repo = _RepoFalso(_valida());
    final n = _crear(repo);
    n.editar('  0012345678 ');
    await n.validar();
    expect(repo.ultimaReferencia, '0012345678');
    n.dispose();
  });

  test('VALIDA con puedePagar pasa a valida y habilita continuar', () async {
    final n = _crear(_RepoFalso(_valida()));
    n.editar('123');
    await n.validar();
    expect(n.state.estado, EstadoReferencia.valida);
    expect(n.state.puedeContinuar, isTrue);
    final datos = n.state.datosPago!;
    expect(datos.idValidacion, 'uuid-1');
    expect(datos.idSer, 1);
    expect(datos.idProd, 2);
    expect(datos.referencia, '123');
    expect(datos.situacion, SituacionSaldo.adeudo);
    n.dispose();
  });

  test('VALIDA sin puedePagar pasa a soloConsulta y no permite continuar',
      () async {
    final n = _crear(_RepoFalso(_valida(puedePagar: false, idValidacion: null)));
    n.editar('123');
    await n.validar();
    expect(n.state.estado, EstadoReferencia.soloConsulta);
    expect(n.state.puedeContinuar, isFalse);
    expect(n.state.datosPago, isNull);
    n.dispose();
  });

  test('puedePagar sin idValidacion no deja continuar (error)', () async {
    final n = _crear(_RepoFalso(_valida(idValidacion: null)));
    n.editar('123');
    await n.validar();
    expect(n.state.estado, EstadoReferencia.error);
    expect(n.state.puedeContinuar, isFalse);
    n.dispose();
  });

  test('SOLO_FORMATO sin saldo ni situacion pasa a valida y continua',
      () async {
    final n = _crear(_RepoFalso(_soloFormato()));
    n.editar('123');
    await n.validar();
    expect(n.state.estado, EstadoReferencia.valida);
    expect(n.state.puedeContinuar, isTrue);
    final datos = n.state.datosPago!;
    expect(datos.verificacion, VerificacionReferencia.soloFormato);
    expect(datos.montoAPagar, isNull);
    expect(datos.saldo, isNull);
    expect(lineasAdicionalesReferencia(n.state), isEmpty);
    n.dispose();
  });

  test('SOLO_FORMATO con puedePagar y sin idValidacion es error', () async {
    final n = _crear(_RepoFalso(_soloFormato(idValidacion: null)));
    n.editar('123');
    await n.validar();
    expect(n.state.estado, EstadoReferencia.error);
    n.dispose();
  });

  test('EN_LINEA con adeudo muestra total a pagar y permite continuar',
      () async {
    final n = _crear(_RepoFalso(_valida()));
    n.editar('123');
    await n.validar();
    expect(n.state.datosPago!.montoAPagar, 350);
    expect(lineasAdicionalesReferencia(n.state), ['Total a pagar: \$350.00']);
    n.dispose();
  });

  test('EN_LINEA sin adeudo o saldo a favor pasa a soloConsulta', () async {
    final sin = _crear(_RepoFalso(_valida(
        situacion: 'SIN_ADEUDO',
        saldo: 0,
        puedePagar: false,
        idValidacion: null)));
    sin.editar('123');
    await sin.validar();
    expect(sin.state.estado, EstadoReferencia.soloConsulta);
    expect(sin.state.puedeContinuar, isFalse);
    expect(lineasAdicionalesReferencia(sin.state), ['No hay nada por pagar.']);

    final favor = _crear(_RepoFalso(_valida(
        situacion: 'SALDO_A_FAVOR',
        saldo: -50,
        puedePagar: false,
        idValidacion: null)));
    favor.editar('123');
    await favor.validar();
    expect(favor.state.estado, EstadoReferencia.soloConsulta);
    expect(textoResultadoReferencia(favor.state),
        'Referencia verificada. Tienes un saldo a favor de \$50.00.');
    expect(
        lineasAdicionalesReferencia(favor.state), ['No hay nada por pagar.']);

    final consulta = _crear(_RepoFalso(
        _valida(puedePagar: false, idValidacion: null)));
    consulta.editar('123');
    await consulta.validar();
    expect(lineasAdicionalesReferencia(consulta.state),
        [TextosReferencia.soloConsulta]);
    sin.dispose();
    favor.dispose();
    consulta.dispose();
  });

  test('INVALIDA pasa a invalida', () async {
    final n = _crear(_RepoFalso(_invalida));
    n.editar('123');
    await n.validar();
    expect(n.state.estado, EstadoReferencia.invalida);
    expect(n.state.puedeContinuar, isFalse);
    n.dispose();
  });

  test('estado desconocido del contrato pasa a error', () async {
    final n = _crear(_RepoFalso(const Ok<ValidacionReferencia>(
      ValidacionReferencia(estado: EstadoValidacion.desconocido),
    )));
    n.editar('123');
    await n.validar();
    expect(n.state.estado, EstadoReferencia.error);
    n.dispose();
  });

  test('fallos (400/404/500/502/503/504/red) pasan a error', () async {
    for (final tipo in [
      TipoFallo.validacion,
      TipoFallo.noEncontrado,
      TipoFallo.servidor,
      TipoFallo.red,
      TipoFallo.prohibido,
      TipoFallo.desconocido,
    ]) {
      final n = _crear(_RepoFalso(_fallo(tipo)));
      n.editar('123');
      await n.validar();
      expect(n.state.estado, EstadoReferencia.error, reason: '$tipo');
      n.dispose();
    }
  });

  test('401 descarta el estado (el interceptor cierra la sesion)', () async {
    final n = _crear(_RepoFalso(_fallo(TipoFallo.noAutenticado)));
    n.editar('123');
    await n.validar();
    expect(n.state.idSer, isNull);
    expect(n.state.puedeContinuar, isFalse);
    n.dispose();
  });

  test('mientras valida no hay envios dobles', () async {
    final repo = _RepoFalso(_valida())..compuerta = Completer<void>();
    final n = _crear(repo);
    n.editar('123');
    final primera = n.validar();
    expect(n.state.estado, EstadoReferencia.validando);
    final segunda = n.validar();
    repo.compuerta!.complete();
    await Future.wait([primera, segunda]);
    expect(repo.llamadas, 1);
    expect(n.state.estado, EstadoReferencia.valida);
    n.dispose();
  });

  test('editar un caracter invalida la validacion previa', () async {
    final n = _crear(_RepoFalso(_valida()));
    n.editar('123');
    await n.validar();
    expect(n.state.puedeContinuar, isTrue);
    n.editar('1234');
    expect(n.state.estado, EstadoReferencia.capturando);
    expect(n.state.puedeContinuar, isFalse);
    expect(n.state.datosPago, isNull);
    n.dispose();
  });

  test('editar tras invalida, error o expirada vuelve a capturando', () async {
    final n = _crear(_RepoFalso(_invalida));
    n.editar('123');
    await n.validar();
    expect(n.state.estado, EstadoReferencia.invalida);
    n.editar('124');
    expect(n.state.estado, EstadoReferencia.capturando);
    n.dispose();
  });

  test('reintentar tras error vuelve a validar', () async {
    final repo = _RepoFalso(_fallo(TipoFallo.red));
    final n = _crear(repo);
    n.editar('123');
    await n.validar();
    expect(n.state.estado, EstadoReferencia.error);
    repo.resultado = _valida();
    await n.reintentar();
    expect(n.state.estado, EstadoReferencia.valida);
    expect(repo.llamadas, 2);
    n.dispose();
  });

  test('la vigencia agotada pasa a expirada y descarta el comprobante',
      () async {
    final n = _crear(
      _RepoFalso(_valida()),
      vigencia: const Duration(milliseconds: 40),
    );
    n.editar('123');
    await n.validar();
    expect(n.state.estado, EstadoReferencia.valida);
    await Future<void>.delayed(const Duration(milliseconds: 120));
    expect(n.state.estado, EstadoReferencia.expirada);
    expect(n.state.puedeContinuar, isFalse);
    expect(n.state.datosPago, isNull);
    n.dispose();
  });

  test('verificarVigencia expira con el reloj aunque el timer no haya corrido',
      () async {
    var ahora = DateTime(2026, 1, 1, 12);
    final n = _crear(_RepoFalso(_valida()), reloj: () => ahora);
    n.editar('123');
    await n.validar();
    expect(n.state.estado, EstadoReferencia.valida);
    ahora = ahora.add(const Duration(seconds: 301));
    n.verificarVigencia();
    expect(n.state.estado, EstadoReferencia.expirada);
    n.dispose();
  });

  test('cambiar de producto o limpiar descarta la validacion', () async {
    final n = _crear(_RepoFalso(_valida()));
    n.editar('123');
    await n.validar();
    n.iniciar(1, 3);
    expect(n.state.estado, EstadoReferencia.capturando);
    expect(n.state.datosPago, isNull);
    expect(n.state.referencia, '');

    final m = _crear(_RepoFalso(_valida()));
    m.editar('123');
    await m.validar();
    m.limpiar();
    expect(m.state.puedeContinuar, isFalse);
    expect(m.state.idSer, isNull);
    n.dispose();
    m.dispose();
  });

  test('iniciar con el mismo producto conserva la validacion', () async {
    final n = _crear(_RepoFalso(_valida()));
    n.editar('123');
    await n.validar();
    n.iniciar(1, 2);
    expect(n.state.estado, EstadoReferencia.valida);
    n.dispose();
  });

  test('una respuesta tardia tras editar se descarta', () async {
    final repo = _RepoFalso(_valida())..compuerta = Completer<void>();
    final n = _crear(repo);
    n.editar('123');
    final futuro = n.validar();
    n.limpiar();
    repo.compuerta!.complete();
    await futuro;
    expect(n.state.puedeContinuar, isFalse);
    expect(n.state.idSer, isNull);
    n.dispose();
  });

  test('salir del flujo limpia solo si es el producto activo', () async {
    final n = _crear(_RepoFalso(_valida()));
    n.editar('123');
    await n.validar();
    n.salir(9, 9);
    expect(n.state.estado, EstadoReferencia.valida);
    n.salir(1, 2);
    expect(n.state.puedeContinuar, isFalse);
    n.dispose();
  });

  group('los textos mostrados son los de la tabla', () {
    test('ningun estado muestra texto del backend', () async {
      final casos = <Result<ValidacionReferencia>, String>{
        _valida(): 'Referencia verificada. Tienes un adeudo de \$350.00.',
        _valida(situacion: 'SIN_ADEUDO', saldo: 0):
            'Referencia verificada. No tienes adeudo pendiente.',
        _valida(situacion: 'SALDO_A_FAVOR', saldo: -50):
            'Referencia verificada. Tienes un saldo a favor de \$50.00.',
        _valida(puedePagar: false, idValidacion: null):
            'Referencia verificada. Tienes un adeudo de \$350.00.',
        _valida(situacion: 'SIN_ADEUDO', saldo: 0, puedePagar: false,
            idValidacion: null): TextosReferencia.sinAdeudo,
        _soloFormato(): TextosReferencia.soloFormato,
        _invalida: TextosReferencia.invalida,
        _fallo(TipoFallo.servidor): TextosReferencia.error,
        _fallo(TipoFallo.red): TextosReferencia.error,
      };
      for (final e in casos.entries) {
        final n = _crear(_RepoFalso(e.key));
        n.editar('123');
        await n.validar();
        final texto = textoResultadoReferencia(n.state);
        expect(texto, e.value);
        final minus = texto!.toLowerCase();
        for (final prohibida in ['servidor', 'api', 'token', 'backend', '502']) {
          expect(minus, isNot(contains(prohibida)));
        }
        n.dispose();
      }
    });

    test('expirada muestra el texto de expiracion', () async {
      final n = _crear(
        _RepoFalso(_valida()),
        vigencia: const Duration(milliseconds: 20),
      );
      n.editar('123');
      await n.validar();
      await Future<void>.delayed(const Duration(milliseconds: 100));
      expect(textoResultadoReferencia(n.state), TextosReferencia.expirada);
      n.dispose();
    });
  });
}

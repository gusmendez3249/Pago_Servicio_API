import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/features/historial/data/models/historial_models.dart';
import 'package:gesto_pago_mobile/features/historial/domain/entities/elemento_historial.dart';
import 'package:gesto_pago_mobile/features/historial/domain/utils/reglas_historial.dart';
import 'package:gesto_pago_mobile/features/pago/domain/entities/comprobante_pago.dart';

void main() {
  group('estado visual', () {
    test('CONFIRMADA es exitoso, RECHAZADA rechazado', () {
      expect(estadoVisualDe(EstatusPago.confirmada), EstadoVisual.exitoso);
      expect(estadoVisualDe(EstatusPago.rechazada), EstadoVisual.rechazado);
    });
    test('INDETERMINADA, PENDIENTE y desconocido son pendiente', () {
      expect(estadoVisualDe(EstatusPago.indeterminada), EstadoVisual.pendiente);
      expect(estadoVisualDe(EstatusPago.pendiente), EstadoVisual.pendiente);
      expect(estadoVisualDe(EstatusPago.desconocido), EstadoVisual.pendiente);
      expect(estadoVisualDe(EstatusPago.desdeTexto('RARO')), EstadoVisual.pendiente);
    });
  });

  group('formato de fecha es-MX', () {
    test('formato "13 nov 2026, 19:39"', () {
      expect(formatearFechaHistorial('2026-11-13T19:39:00'), '13 nov 2026, 19:39');
    });
    test('día sin cero a la izquierda y hora con cero', () {
      expect(formatearFechaHistorial('2026-03-05T09:05:30'), '5 mar 2026, 09:05');
    });
    test('todos los meses', () {
      const esperados = [
        'ene', 'feb', 'mar', 'abr', 'may', 'jun',
        'jul', 'ago', 'sep', 'oct', 'nov', 'dic',
      ];
      for (var m = 1; m <= 12; m++) {
        final mes = m.toString().padLeft(2, '0');
        expect(
          formatearFechaHistorial('2026-$mes-10T10:00:00'),
          '10 ${esperados[m - 1]} 2026, 10:00',
        );
      }
    });
    test('nulo o inválido devuelve null', () {
      expect(formatearFechaHistorial(null), isNull);
      expect(formatearFechaHistorial('no es fecha'), isNull);
    });
  });

  group('folio u número de operación', () {
    test('con folio usa "Folio"', () {
      const e = ElementoHistorial(
        estatus: EstatusPago.confirmada,
        folio: 'F1',
        identificadorOperacion: 'OP9',
      );
      expect(identificadorDeOperacion(e), (etiqueta: 'Folio', valor: 'F1'));
    });
    test('sin folio usa "Número de operación"', () {
      const e = ElementoHistorial(
        estatus: EstatusPago.pendiente,
        folio: '  ',
        identificadorOperacion: 'OP9',
      );
      expect(
        identificadorDeOperacion(e),
        (etiqueta: 'Número de operación', valor: 'OP9'),
      );
    });
    test('sin ninguno devuelve null', () {
      expect(
        identificadorDeOperacion(const ElementoHistorial(estatus: EstatusPago.pendiente)),
        isNull,
      );
    });
  });

  group('filtros', () {
    test('estatus del servidor', () {
      expect(FiltroHistorial.todos.estatusServidor, isNull);
      expect(FiltroHistorial.exitosos.estatusServidor, 'CONFIRMADA');
      expect(FiltroHistorial.rechazados.estatusServidor, 'RECHAZADA');
      expect(FiltroHistorial.pendientes.estatusServidor, isNull);
      expect(FiltroHistorial.pendientes.estatusesServidor, ['PENDIENTE', 'INDETERMINADA']);
    });
    test('pendientes acepta indeterminada, pendiente y desconocido', () {
      for (final s in [
        EstatusPago.indeterminada,
        EstatusPago.pendiente,
        EstatusPago.desconocido,
      ]) {
        expect(FiltroHistorial.pendientes.acepta(ElementoHistorial(estatus: s)), isTrue);
      }
      expect(
        FiltroHistorial.pendientes.acepta(
          const ElementoHistorial(estatus: EstatusPago.confirmada),
        ),
        isFalse,
      );
    });
  });

  group('modelos', () {
    test('tolera nulos y tipos distintos', () {
      final p = paginaHistorialDesdeJson({
        'elementos': [
          {
            'upc': 'A1',
            'estatus': 'CONFIRMADA',
            'monto': '100.5',
            'idServicio': '7',
            'logoUrl': '',
            'pinServicio': 'SECRETO',
            'mensajeRespuesta': 'texto tecnico',
          },
          'basura',
          {'estatus': null},
        ],
        'pagina': 0,
        'hayMas': true,
      });
      expect(p.elementos, hasLength(2));
      expect(p.elementos.first.monto, 100.5);
      expect(p.elementos.first.idServicio, 7);
      expect(p.elementos.first.logoUrl, isNull);
      expect(p.elementos.last.estatus, EstatusPago.desconocido);
      expect(p.hayMas, isTrue);
    });
    test('data que no es objeto lanza FormatException', () {
      expect(() => paginaHistorialDesdeJson(null), throwsFormatException);
    });
    test('el cuerpo solo lleva filtros si hay estatus', () {
      expect(
        solicitudHistorialAJson(pagina: 0, tamanio: 20),
        {'pagina': 0, 'tamanio': 20},
      );
      expect(
        solicitudHistorialAJson(estatus: 'CONFIRMADA', pagina: 1, tamanio: 20),
        {
          'filtros': {'estatus': 'CONFIRMADA'},
          'pagina': 1,
          'tamanio': 20,
        },
      );
    });
  });
}

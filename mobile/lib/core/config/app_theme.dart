import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';

/// Tokens del sistema: interfaz CLARA con el negro metálico como color de
/// marca e interacción. Colores planos y opacos: sin degradados ni
/// transparencias. Es el ÚNICO lugar donde viven los colores y las medidas.
/// Los contrastes (AA) están comprobados: no cambies un valor sin
/// recalcularlos.
abstract final class Paleta {
  // Marca: negro metálico, un solo tono sólido con matiz acero.
  static const Color negro = Color(0xFF121417);
  static const Color negroPresionado = Color(0xFF2A2E35);

  // Filo metálico: líneas sólidas de 1 px que simulan el canto biselado.
  static const Color filoLuz = Color(0xFF5B616B);
  static const Color filoSombra = Color(0xFF000000);
  static const Color filoBoton = Color(0xFF3A3F47);

  // Sobre negro.
  static const Color textoSobreNegro = Color(0xFFFFFFFF);
  static const Color secundarioSobreNegro = Color(0xFFB4BAC4);
  static const Color lineaSobreNegro = Color(0xFF2A2E35);
  static const Color campoSobreNegro = Color(0xFF1E2127);
  static const Color lineaCampoSobreNegro = Color(0xFF5B616B);

  // Claros.
  static const Color fondo = Color(0xFFF4F5F7);
  static const Color blanco = Color(0xFFFFFFFF);
  static const Color linea = Color(0xFFE3E5E8);
  static const Color lineaFuerte = Color(0xFFC9CDD2);

  // Texto sobre claro.
  static const Color texto = Color(0xFF121417);
  static const Color textoSecundario = Color(0xFF5B616B);
  static const Color deshabilitado = Color(0xFF8A9099);

  // Estados (solo icono y barra izquierda; el texto nunca va en color).
  static const Color exito = Color(0xFF1E7A4C);
  static const Color pendiente = Color(0xFF8A5A12);
  static const Color error = Color(0xFFB3261E);
  static const Color info = Color(0xFF5B616B);

  /// Relleno rojo SOLO para acciones destructivas confirmadas (p. ej.
  /// "Cerrar sesión" en su diálogo); pulsado en [peligroFuerte].
  static const Color peligro = Color(0xFFB3261E);
  static const Color peligroFuerte = Color(0xFF8C1D17);

  static const Color transparente = Color(0x00000000);

  /// Sombra neutra, solo para diálogos y hojas inferiores.
  static const Color sombra = Color(0x29000000);

  /// Velo detrás de diálogos y hojas inferiores: negro de marca al 50 %.
  static const Color velo = Color(0x80121417);
}

/// Superficie de marca con filo metálico: fondo negro sólido, línea de 1 px
/// de luz arriba y de sombra abajo. Úsala en todas las superficies negras
/// grandes (barra y franja, bloque de marca del login, bloque del
/// comprobante).
BoxDecoration superficieMetal({bool filoArriba = true, bool filoAbajo = true}) {
  return BoxDecoration(
    color: Paleta.negro,
    border: Border(
      top: filoArriba
          ? const BorderSide(color: Paleta.filoLuz)
          : BorderSide.none,
      bottom: filoAbajo
          ? const BorderSide(color: Paleta.filoSombra)
          : BorderSide.none,
    ),
  );
}

/// Radios.
abstract final class Radios {
  /// Botones y campos: esquinas casi rectas.
  static const double campo = 4;

  /// Tarjetas.
  static const double tarjeta = 8;

  /// Parte superior de las hojas inferiores (bottom sheets).
  static const double hoja = 16;
}

/// Escala única de espaciado: 4, 8, 12, 16, 24, 32, 48 y 64. No se usan
/// otros valores. Relacionados a 8–12, grupos a 24, secciones a 32; el
/// relleno interno nunca supera la separación entre componentes.
abstract final class Esp {
  static const double x1 = 4;
  static const double x2 = 8;
  static const double x3 = 12;
  static const double x4 = 16;
  static const double x6 = 24;
  static const double x8 = 32;
  static const double x12 = 48;
  static const double x16 = 64;

  /// Margen lateral único de las pantallas.
  static const double margen = x4;

  /// Margen lateral del inicio de sesión.
  static const double margenLogin = x6;
}

/// Medidas de controles.
abstract final class Medidas {
  /// Objetivo táctil mínimo en móvil.
  static const double tactil = 48;

  /// Alto de los botones (igual que la web: 48).
  static const double boton = 48;

  /// Alto de los campos.
  static const double campo = 56;

  /// Ancho máximo de lectura del contenido.
  static const double anchoContenido = 560;

  /// Grosor del indicador de pestaña activa.
  static const double indicador = 2;

  /// Logotipo en los renglones de las listas.
  static const double logoLista = 44;

  /// Círculo de los accesos rápidos.
  static const double acceso = 56;

  /// Indicador de actividad dentro de botones y barras.
  static const double giro = 20;
}

/// Escala tipográfica.
abstract final class Escala {
  static const double s13 = 13;
  static const double s14 = 14;
  static const double s16 = 16;
  static const double s20 = 20;
  static const double s28 = 28;
  static const double s40 = 40;
}

/// Cifras tabulares: importes, referencias, teléfonos y folios.
const List<FontFeature> kCifras = <FontFeature>[FontFeature.tabularFigures()];

/// Acceso a las fuentes. En pruebas se apaga para no descargar nada.
abstract final class AppFuentes {
  /// `false` en pruebas: los estilos salen sin familia.
  static bool habilitadas = true;
}

/// Texto general en Libre Franklin (grotesca): cuerpo, etiquetas, botones e
/// importes.
TextStyle estiloTexto({
  double? tamano,
  FontWeight? peso,
  Color? color,
  double? altura,
  bool cifras = false,
  double? espaciado,
  TextDecoration? decoracion,
}) {
  final base = TextStyle(
    fontSize: tamano,
    fontWeight: peso,
    color: color,
    height: altura,
    letterSpacing: espaciado,
    decoration: decoracion,
    fontFeatures: cifras ? kCifras : null,
  );
  return AppFuentes.habilitadas
      ? GoogleFonts.libreFranklin(textStyle: base)
      : base;
}

/// Titulares, saludos, marca y títulos de pantalla en Source Serif 4 (600,
/// interletrado −0.01 em).
TextStyle estiloTitular({double tamano = Escala.s28, Color? color}) {
  final base = TextStyle(
    fontSize: tamano,
    fontWeight: FontWeight.w600,
    color: color,
    height: 1.15,
    letterSpacing: -tamano * 0.01,
  );
  return AppFuentes.habilitadas
      ? GoogleFonts.sourceSerif4(textStyle: base)
      : base;
}

/// Importes: Libre Franklin 600 con cifras tabulares (nunca en serif).
TextStyle estiloImporte({
  double tamano = Escala.s28,
  FontWeight peso = FontWeight.w600,
  Color? color,
}) {
  return estiloTexto(
    tamano: tamano,
    peso: peso,
    color: color,
    altura: 1.1,
    cifras: true,
    espaciado: -tamano * 0.015,
  );
}

/// Barra de estado con iconos claros sobre la barra negra; barra de
/// navegación del sistema clara.
const SystemUiOverlayStyle kBarraEstadoSobreNegro = SystemUiOverlayStyle(
  statusBarColor: Paleta.negro,
  statusBarIconBrightness: Brightness.light,
  statusBarBrightness: Brightness.dark,
  systemNavigationBarColor: Paleta.blanco,
  systemNavigationBarIconBrightness: Brightness.dark,
);

/// Colores por contexto. [claro] es el de la app; [sobreNegro] es el de las
/// superficies de marca (la franja), donde todo se invierte.
@immutable
class ColoresApp extends ThemeExtension<ColoresApp> {
  const ColoresApp({
    required this.fondo,
    required this.superficie,
    required this.circulo,
    required this.linea,
    required this.lineaFuerte,
    required this.texto,
    required this.textoSecundario,
    required this.deshabilitado,
    required this.marca,
    required this.marcaPresionada,
    required this.sobreMarca,
    required this.exito,
    required this.pendiente,
    required this.error,
    required this.info,
  });

  static const ColoresApp claro = ColoresApp(
    fondo: Paleta.fondo,
    superficie: Paleta.blanco,
    circulo: Paleta.fondo,
    linea: Paleta.linea,
    lineaFuerte: Paleta.lineaFuerte,
    texto: Paleta.texto,
    textoSecundario: Paleta.textoSecundario,
    deshabilitado: Paleta.deshabilitado,
    marca: Paleta.negro,
    marcaPresionada: Paleta.negroPresionado,
    sobreMarca: Paleta.blanco,
    exito: Paleta.exito,
    pendiente: Paleta.pendiente,
    error: Paleta.error,
    info: Paleta.info,
  );

  static const ColoresApp sobreNegro = ColoresApp(
    fondo: Paleta.negro,
    superficie: Paleta.campoSobreNegro,
    circulo: Paleta.campoSobreNegro,
    linea: Paleta.lineaSobreNegro,
    lineaFuerte: Paleta.lineaCampoSobreNegro,
    texto: Paleta.textoSobreNegro,
    textoSecundario: Paleta.secundarioSobreNegro,
    deshabilitado: Paleta.secundarioSobreNegro,
    marca: Paleta.blanco,
    marcaPresionada: Paleta.linea,
    sobreMarca: Paleta.negro,
    exito: Paleta.exito,
    pendiente: Paleta.pendiente,
    error: Paleta.error,
    info: Paleta.info,
  );

  /// Fondo de la pantalla.
  final Color fondo;

  /// Tarjetas, listas, formularios y hojas.
  final Color superficie;

  /// Círculos de accesos e iniciales.
  final Color circulo;
  final Color linea;
  final Color lineaFuerte;
  final Color texto;
  final Color textoSecundario;
  final Color deshabilitado;

  /// Color de marca e interacción (relleno del primario, seleccionado,
  /// activo).
  final Color marca;
  final Color marcaPresionada;

  /// Texto e icono sobre [marca].
  final Color sobreMarca;
  final Color exito;
  final Color pendiente;
  final Color error;
  final Color info;

  /// Lee la extensión del tema; si no existe usa la paleta clara.
  static ColoresApp de(BuildContext context) =>
      Theme.of(context).extension<ColoresApp>() ?? claro;

  @override
  ColoresApp copyWith({Color? fondo, Color? texto}) => ColoresApp(
        fondo: fondo ?? this.fondo,
        superficie: superficie,
        circulo: circulo,
        linea: linea,
        lineaFuerte: lineaFuerte,
        texto: texto ?? this.texto,
        textoSecundario: textoSecundario,
        deshabilitado: deshabilitado,
        marca: marca,
        marcaPresionada: marcaPresionada,
        sobreMarca: sobreMarca,
        exito: exito,
        pendiente: pendiente,
        error: error,
        info: info,
      );

  @override
  ColoresApp lerp(ThemeExtension<ColoresApp>? other, double t) =>
      (other is ColoresApp && t >= 0.5) ? other : this;
}

const ColorScheme _esquema = ColorScheme(
  brightness: Brightness.light,
  primary: Paleta.negro,
  onPrimary: Paleta.blanco,
  primaryContainer: Paleta.fondo,
  onPrimaryContainer: Paleta.texto,
  secondary: Paleta.negro,
  onSecondary: Paleta.blanco,
  secondaryContainer: Paleta.fondo,
  onSecondaryContainer: Paleta.texto,
  tertiary: Paleta.pendiente,
  onTertiary: Paleta.blanco,
  error: Paleta.error,
  onError: Paleta.blanco,
  surface: Paleta.blanco,
  onSurface: Paleta.texto,
  onSurfaceVariant: Paleta.textoSecundario,
  outline: Paleta.lineaFuerte,
  outlineVariant: Paleta.linea,
  shadow: Paleta.sombra,
  scrim: Paleta.velo,
  inverseSurface: Paleta.negro,
  onInverseSurface: Paleta.blanco,
  inversePrimary: Paleta.blanco,
  surfaceTint: Paleta.transparente,
  surfaceDim: Paleta.fondo,
  surfaceBright: Paleta.blanco,
  surfaceContainerLowest: Paleta.blanco,
  surfaceContainerLow: Paleta.blanco,
  surfaceContainer: Paleta.blanco,
  surfaceContainerHigh: Paleta.blanco,
  surfaceContainerHighest: Paleta.fondo,
);

TextTheme _textTheme(Color principal, Color secundario) {
  TextStyle t(double tamano, FontWeight peso, {Color? color, double? alt}) =>
      estiloTexto(
        tamano: tamano,
        peso: peso,
        color: color ?? principal,
        altura: alt,
      );
  TextStyle serif(double tamano) =>
      estiloTitular(tamano: tamano, color: principal);
  return TextTheme(
    displayLarge: serif(Escala.s40),
    displayMedium: serif(Escala.s40),
    displaySmall: serif(Escala.s40),
    headlineLarge: serif(Escala.s40),
    headlineMedium: serif(Escala.s28),
    headlineSmall: serif(Escala.s20),
    titleLarge: serif(Escala.s20),
    titleMedium: t(Escala.s16, FontWeight.w600),
    titleSmall: t(Escala.s14, FontWeight.w600),
    bodyLarge: t(Escala.s16, FontWeight.w400, alt: 1.4),
    bodyMedium: t(Escala.s14, FontWeight.w400, alt: 1.4),
    bodySmall: t(Escala.s14, FontWeight.w400, color: secundario),
    labelLarge: t(Escala.s14, FontWeight.w600),
    labelMedium: t(Escala.s13, FontWeight.w500),
    labelSmall: t(Escala.s13, FontWeight.w500, color: secundario),
  );
}

const RoundedRectangleBorder _formaBoton = RoundedRectangleBorder(
  borderRadius: BorderRadius.all(Radius.circular(Radios.campo)),
);

/// Filo metálico del botón primario negro: línea de 1 px arriba
/// ([Paleta.filoBoton]) y abajo ([Paleta.filoSombra]); al pulsar se invierten
/// para que parezca hundido. Se pinta dentro de la forma del botón.
Widget filoPrimario(Set<WidgetState> estados, Widget? hijo) {
  if (estados.contains(WidgetState.disabled)) return hijo ?? const SizedBox();
  final hundido = estados.contains(WidgetState.pressed);
  return DecoratedBox(
    decoration: BoxDecoration(
      border: Border(
        top: BorderSide(
          color: hundido ? Paleta.filoSombra : Paleta.filoBoton,
        ),
        bottom: BorderSide(
          color: hundido ? Paleta.filoBoton : Paleta.filoSombra,
        ),
      ),
    ),
    child: hijo,
  );
}

ButtonStyle _primario({required bool sobreNegro}) {
  final fondo = sobreNegro ? Paleta.blanco : Paleta.negro;
  final presionado = sobreNegro ? Paleta.linea : Paleta.negroPresionado;
  final texto = sobreNegro ? Paleta.negro : Paleta.blanco;
  return ButtonStyle(
    backgroundColor: WidgetStateProperty.resolveWith((e) {
      if (e.contains(WidgetState.disabled)) {
        return sobreNegro ? Paleta.campoSobreNegro : Paleta.linea;
      }
      if (e.contains(WidgetState.pressed) || e.contains(WidgetState.hovered)) {
        return presionado;
      }
      return fondo;
    }),
    foregroundColor: WidgetStateProperty.resolveWith(
      (e) => e.contains(WidgetState.disabled)
          ? (sobreNegro ? Paleta.secundarioSobreNegro : Paleta.deshabilitado)
          : texto,
    ),
    overlayColor: const WidgetStatePropertyAll(Paleta.transparente),
    backgroundBuilder: sobreNegro
        ? null
        : (context, estados, hijo) => filoPrimario(estados, hijo),
    side: WidgetStateProperty.resolveWith(
      (e) => e.contains(WidgetState.focused)
          ? BorderSide(
              color: sobreNegro ? Paleta.blanco : Paleta.negro,
              width: 2,
              strokeAlign: BorderSide.strokeAlignOutside,
            )
          : null,
    ),
    minimumSize: const WidgetStatePropertyAll(Size(64, Medidas.boton)),
    padding: const WidgetStatePropertyAll(
      EdgeInsets.symmetric(horizontal: Esp.x6),
    ),
    elevation: const WidgetStatePropertyAll(0),
    shape: const WidgetStatePropertyAll(_formaBoton),
    textStyle: WidgetStatePropertyAll(
      estiloTexto(tamano: Escala.s16, peso: FontWeight.w600),
    ),
  );
}

ButtonStyle _secundario({required bool sobreNegro}) {
  final color = sobreNegro ? Paleta.blanco : Paleta.negro;
  return ButtonStyle(
    foregroundColor: WidgetStateProperty.resolveWith(
      (e) => e.contains(WidgetState.disabled)
          ? (sobreNegro ? Paleta.secundarioSobreNegro : Paleta.deshabilitado)
          : color,
    ),
    backgroundColor: WidgetStateProperty.resolveWith(
      (e) => e.contains(WidgetState.pressed) || e.contains(WidgetState.hovered)
          ? (sobreNegro ? Paleta.campoSobreNegro : Paleta.fondo)
          : Paleta.transparente,
    ),
    overlayColor: const WidgetStatePropertyAll(Paleta.transparente),
    side: WidgetStateProperty.resolveWith((e) {
      if (e.contains(WidgetState.disabled)) {
        return BorderSide(
          color: sobreNegro ? Paleta.lineaSobreNegro : Paleta.linea,
        );
      }
      return BorderSide(
        color: color,
        width: e.contains(WidgetState.focused) ? 2 : 1,
      );
    }),
    minimumSize: const WidgetStatePropertyAll(Size(64, Medidas.boton)),
    padding: const WidgetStatePropertyAll(
      EdgeInsets.symmetric(horizontal: Esp.x6),
    ),
    elevation: const WidgetStatePropertyAll(0),
    shape: const WidgetStatePropertyAll(_formaBoton),
    textStyle: WidgetStatePropertyAll(
      estiloTexto(tamano: Escala.s16, peso: FontWeight.w600),
    ),
  );
}

ButtonStyle _terciario({required bool sobreNegro}) {
  final color = sobreNegro ? Paleta.blanco : Paleta.negro;
  return ButtonStyle(
    foregroundColor: WidgetStateProperty.resolveWith(
      (e) => e.contains(WidgetState.disabled)
          ? (sobreNegro ? Paleta.secundarioSobreNegro : Paleta.deshabilitado)
          : color,
    ),
    overlayColor: const WidgetStatePropertyAll(Paleta.transparente),
    backgroundColor: WidgetStateProperty.resolveWith(
      (e) => e.contains(WidgetState.pressed) || e.contains(WidgetState.hovered)
          ? (sobreNegro ? Paleta.campoSobreNegro : Paleta.fondo)
          : Paleta.transparente,
    ),
    side: WidgetStateProperty.resolveWith(
      (e) => e.contains(WidgetState.focused)
          ? BorderSide(color: color, width: 2)
          : null,
    ),
    minimumSize: const WidgetStatePropertyAll(
      Size(Medidas.tactil, Medidas.tactil),
    ),
    padding: const WidgetStatePropertyAll(
      EdgeInsets.symmetric(horizontal: Esp.x3),
    ),
    shape: const WidgetStatePropertyAll(_formaBoton),
    textStyle: WidgetStatePropertyAll(
      estiloTexto(
        tamano: Escala.s16,
        peso: FontWeight.w600,
        decoracion: TextDecoration.underline,
      ),
    ),
  );
}

/// Campos estilo banca: relleno plano, esquinas superiores de 4, línea
/// inferior de 1 px que pasa a 2 px con foco y etiqueta flotante. El error va
/// en texto principal con la línea en el color de error.
InputDecorationTheme _temaCampos({required bool sobreNegro}) {
  final relleno = sobreNegro ? Paleta.campoSobreNegro : Paleta.fondo;
  final linea = sobreNegro ? Paleta.lineaCampoSobreNegro : Paleta.deshabilitado;
  final foco = sobreNegro ? Paleta.blanco : Paleta.negro;
  final texto = sobreNegro ? Paleta.blanco : Paleta.texto;
  final secundario =
      sobreNegro ? Paleta.secundarioSobreNegro : Paleta.textoSecundario;
  UnderlineInputBorder borde(Color color, double ancho) => UnderlineInputBorder(
        borderRadius: const BorderRadius.vertical(
          top: Radius.circular(Radios.campo),
        ),
        borderSide: BorderSide(color: color, width: ancho),
      );
  return InputDecorationTheme(
    filled: true,
    fillColor: relleno,
    isDense: false,
    floatingLabelBehavior: FloatingLabelBehavior.auto,
    contentPadding: const EdgeInsets.fromLTRB(Esp.x4, Esp.x6, Esp.x4, Esp.x2),
    constraints: const BoxConstraints(minHeight: Medidas.campo),
    border: borde(linea, 1),
    enabledBorder: borde(linea, 1),
    disabledBorder:
        borde(sobreNegro ? Paleta.lineaSobreNegro : Paleta.linea, 1),
    focusedBorder: borde(foco, 2),
    errorBorder: borde(Paleta.error, 1),
    focusedErrorBorder: borde(Paleta.error, 2),
    labelStyle: estiloTexto(tamano: Escala.s16, color: secundario),
    floatingLabelStyle: estiloTexto(
      tamano: Escala.s14,
      peso: FontWeight.w500,
      color: secundario,
    ),
    hintStyle: estiloTexto(tamano: Escala.s16, color: secundario),
    errorStyle: estiloTexto(
      tamano: Escala.s13,
      peso: FontWeight.w600,
      color: texto,
    ),
    helperStyle: estiloTexto(tamano: Escala.s13, color: secundario),
    counterStyle: estiloTexto(tamano: Escala.s13, color: secundario),
    prefixStyle: estiloTexto(
      tamano: Escala.s20,
      peso: FontWeight.w500,
      color: texto,
    ),
    prefixIconColor: secundario,
    suffixIconColor: secundario,
  );
}

ThemeData construirTema() {
  const formaCampo = RoundedRectangleBorder(
    borderRadius: BorderRadius.all(Radius.circular(Radios.campo)),
  );
  return ThemeData(
    useMaterial3: true,
    brightness: Brightness.light,
    colorScheme: _esquema,
    scaffoldBackgroundColor: Paleta.fondo,
    canvasColor: Paleta.fondo,
    textTheme: _textTheme(Paleta.texto, Paleta.textoSecundario),
    extensions: const <ThemeExtension<dynamic>>[ColoresApp.claro],
    splashFactory: NoSplash.splashFactory,
    materialTapTargetSize: MaterialTapTargetSize.padded,
    visualDensity: VisualDensity.standard,
    dividerTheme: const DividerThemeData(
      color: Paleta.linea,
      thickness: 1,
      space: 1,
    ),
    iconTheme: const IconThemeData(color: Paleta.texto, size: 24),
    appBarTheme: AppBarTheme(
      backgroundColor: Paleta.negro,
      foregroundColor: Paleta.blanco,
      surfaceTintColor: Paleta.transparente,
      elevation: 0,
      scrolledUnderElevation: 0,
      centerTitle: false,
      systemOverlayStyle: kBarraEstadoSobreNegro,
      titleTextStyle: estiloTitular(tamano: Escala.s20, color: Paleta.blanco),
      iconTheme: const IconThemeData(color: Paleta.blanco),
    ),
    cardTheme: const CardThemeData(
      color: Paleta.blanco,
      surfaceTintColor: Paleta.transparente,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.all(Radius.circular(Radios.tarjeta)),
        side: BorderSide(color: Paleta.linea),
      ),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: _primario(sobreNegro: false),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: _secundario(sobreNegro: false),
    ),
    textButtonTheme: TextButtonThemeData(style: _terciario(sobreNegro: false)),
    iconButtonTheme: const IconButtonThemeData(
      style: ButtonStyle(
        foregroundColor: WidgetStatePropertyAll(Paleta.texto),
        minimumSize: WidgetStatePropertyAll(
          Size(Medidas.tactil, Medidas.tactil),
        ),
      ),
    ),
    inputDecorationTheme: _temaCampos(sobreNegro: false),
    textSelectionTheme: const TextSelectionThemeData(
      cursorColor: Paleta.negro,
      selectionColor: Paleta.lineaFuerte,
      selectionHandleColor: Paleta.negro,
    ),
    chipTheme: ChipThemeData(
      backgroundColor: Paleta.blanco,
      selectedColor: Paleta.negro,
      disabledColor: Paleta.fondo,
      surfaceTintColor: Paleta.transparente,
      checkmarkColor: Paleta.blanco,
      side: const BorderSide(color: Paleta.lineaFuerte),
      shape: formaCampo,
      elevation: 0,
      pressElevation: 0,
      labelStyle: estiloTexto(
        tamano: Escala.s14,
        peso: FontWeight.w500,
        color: Paleta.texto,
      ),
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: Paleta.blanco,
      surfaceTintColor: Paleta.transparente,
      elevation: 8,
      shadowColor: Paleta.sombra,
      barrierColor: Paleta.velo,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.all(Radius.circular(Radios.tarjeta)),
        side: BorderSide(color: Paleta.linea),
      ),
      titleTextStyle: estiloTitular(tamano: Escala.s20, color: Paleta.texto),
      contentTextStyle:
          estiloTexto(tamano: Escala.s14, color: Paleta.textoSecundario),
    ),
    progressIndicatorTheme: const ProgressIndicatorThemeData(
      color: Paleta.negro,
      linearTrackColor: Paleta.linea,
      circularTrackColor: Paleta.transparente,
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: Paleta.negro,
      contentTextStyle: estiloTexto(tamano: Escala.s14, color: Paleta.blanco),
      elevation: 0,
      shape: formaCampo,
    ),
    bottomSheetTheme: const BottomSheetThemeData(
      backgroundColor: Paleta.blanco,
      surfaceTintColor: Paleta.transparente,
      modalBackgroundColor: Paleta.blanco,
      // La única sombra de la app: suave y neutra, en hojas y diálogos.
      elevation: 8,
      modalElevation: 8,
      shadowColor: Paleta.sombra,
      modalBarrierColor: Paleta.velo,
      showDragHandle: false,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(Radios.hoja)),
      ),
    ),
    tooltipTheme: TooltipThemeData(
      decoration: const BoxDecoration(
        color: Paleta.negro,
        borderRadius: BorderRadius.all(Radius.circular(Radios.campo)),
      ),
      textStyle: estiloTexto(tamano: Escala.s13, color: Paleta.blanco),
    ),
  );
}

/// Tema para el contenido de las superficies negras de marca (franja,
/// bloque del login y del comprobante): texto blanco, campos oscuros,
/// primario invertido (blanco con texto negro) y colores [ColoresApp.sobreNegro].
ThemeData temaSobreNegro(ThemeData base) {
  return base.copyWith(
    textTheme: _textTheme(Paleta.textoSobreNegro, Paleta.secundarioSobreNegro),
    extensions: const <ThemeExtension<dynamic>>[ColoresApp.sobreNegro],
    iconTheme: const IconThemeData(color: Paleta.blanco, size: 24),
    dividerTheme: const DividerThemeData(
      color: Paleta.lineaSobreNegro,
      thickness: 1,
      space: 1,
    ),
    inputDecorationTheme: _temaCampos(sobreNegro: true),
    textSelectionTheme: const TextSelectionThemeData(
      cursorColor: Paleta.blanco,
      selectionColor: Paleta.lineaCampoSobreNegro,
      selectionHandleColor: Paleta.blanco,
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: _primario(sobreNegro: true),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: _secundario(sobreNegro: true),
    ),
    textButtonTheme: TextButtonThemeData(style: _terciario(sobreNegro: true)),
    iconButtonTheme: const IconButtonThemeData(
      style: ButtonStyle(
        foregroundColor: WidgetStatePropertyAll(Paleta.blanco),
        minimumSize: WidgetStatePropertyAll(
          Size(Medidas.tactil, Medidas.tactil),
        ),
      ),
    ),
    progressIndicatorTheme: const ProgressIndicatorThemeData(
      color: Paleta.blanco,
    ),
  );
}

/// Envuelve el contenido de una superficie negra de marca.
class SobreNegro extends StatelessWidget {
  const SobreNegro({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final tema = temaSobreNegro(Theme.of(context));
    return Theme(
      data: tema,
      child: DefaultTextStyle.merge(
        style: const TextStyle(color: Paleta.textoSobreNegro),
        child: IconTheme.merge(
          data: const IconThemeData(color: Paleta.blanco),
          child: child,
        ),
      ),
    );
  }
}

/// Texto de los campos de referencia, teléfono e importe: 20 con cifras
/// tabulares.
TextStyle estiloCampoCifras() => estiloTexto(
      tamano: Escala.s20,
      peso: FontWeight.w500,
      color: Paleta.texto,
      cifras: true,
    );

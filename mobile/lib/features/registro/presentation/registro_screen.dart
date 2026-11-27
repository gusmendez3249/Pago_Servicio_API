import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/config/app_theme.dart';
import '../../../core/error/mensajes_error.dart';
import '../../../core/http/result.dart';
import '../../../core/router/app_router.dart';
import '../../../shared/widgets/banner_mensaje.dart';
import '../../../shared/widgets/enlace.dart';
import '../../../shared/widgets/ficha_datos.dart';
import '../../../shared/widgets/indicador_pasos.dart';
import '../../auth/presentation/screens/login_screen.dart';
import '../../catalogo/domain/utils/formato_catalogo.dart';
import '../data/registro_remote_datasource.dart';
import '../domain/reglas_registro.dart';

const List<String> _pasos = [
  'Tus datos',
  'Contacto y domicilio',
  'Trabajo',
  'Acceso',
];

/// Campos de cada paso: para llevar al usuario al paso de un campo rechazado.
const List<List<String>> _camposPorPaso = [
  [
    'nombre', 'segundoNombre', 'apellidoPaterno', 'apellidoMaterno', //
    'fechaNacimiento', 'sexo', 'estadoCivil', 'nacionalidad', 'curp', 'rfc',
  ],
  [
    'correo', 'telefono', 'calle', 'numeroExterior', 'numeroInterior', //
    'colonia', 'municipio', 'estado', 'codigoPostal',
  ],
  ['ocupacion', 'empresa', 'ingresoMensual'],
  ['username', 'password', 'pin'],
];

/// Campo que reporta el backend → campo del formulario.
const Map<String, String> _campoDe = {
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

const List<String> _controles = [
  'nombre', 'segundoNombre', 'apellidoPaterno', 'apellidoMaterno', 'curp', //
  'rfc', 'correo', 'telefono', 'calle', 'numeroExterior', 'numeroInterior',
  'colonia', 'municipio', 'estado', 'codigoPostal', 'ocupacion', 'empresa',
  'ingresoMensual', 'username', 'password', 'confirmacion', 'pin',
  'confirmacionPin', 'fechaNacimiento',
];

/// Crear cuenta: registro público en 4 pasos. La cuenta nace con saldo $0.00
/// y solo un administrador puede recargarla.
class RegistroScreen extends ConsumerStatefulWidget {
  const RegistroScreen({super.key, this.hoy});

  /// Fecha de referencia para la mayoría de edad (pruebas).
  final DateTime? hoy;

  @override
  ConsumerState<RegistroScreen> createState() => _RegistroScreenState();
}

class _RegistroScreenState extends ConsumerState<RegistroScreen> {
  final _formKeys = List.generate(4, (_) => GlobalKey<FormState>());
  final Map<String, TextEditingController> _c = {
    for (final k in _controles) k: TextEditingController(),
  };
  DateTime? _nacimiento;
  String? _sexo;
  String? _estadoCivil;
  String _nacionalidad = 'MEXICANA';
  List<String> _nacionalidades = const ['MEXICANA'];

  int _paso = 0;
  bool _enviando = false;
  bool _verPassword = false;
  String? _error;
  Map<String, String> _rechazados = const {};
  CuentaCreada? _creada;

  DateTime get _hoy => widget.hoy ?? DateTime.now();

  @override
  void initState() {
    super.initState();
    Future.microtask(_cargarNacionalidades);
  }

  Future<void> _cargarNacionalidades() async {
    final r = await ref.read(registroDatasourceProvider).nacionalidades();
    if (r is Ok<List<String>> && r.data.isNotEmpty && mounted) {
      setState(() {
        _nacionalidades = r.data;
        if (!_nacionalidades.contains(_nacionalidad)) {
          _nacionalidad = _nacionalidades.first;
        }
      });
    }
  }

  @override
  void dispose() {
    for (final c in _c.values) {
      c.dispose();
    }
    super.dispose();
  }

  String _t(String k) => _c[k]!.text.trim();

  void _limpiar(String campo) {
    if (_error == null && !_rechazados.containsKey(campo)) return;
    setState(() {
      _error = null;
      _rechazados = Map.of(_rechazados)..remove(campo);
    });
  }

  String? _rechazo(String campo) => _rechazados[campo];

  // ------------------------------------------------------------ validadores

  String? Function(String?) _nombre({bool requerido = true}) {
    return (v) {
      final t = (v ?? '').trim();
      if (t.isEmpty) return requerido ? 'Este dato es obligatorio.' : null;
      return nombreValido(t)
          ? null
          : 'Usa de 3 a 50 letras, con un solo espacio entre palabras.';
    };
  }

  String? Function(String?) _texto(int max, {bool requerido = true}) {
    return (v) {
      final t = (v ?? '').trim();
      if (t.isEmpty) return requerido ? 'Este dato es obligatorio.' : null;
      if (t.length > max) return 'Máximo $max caracteres.';
      return patronTextoSeguro.hasMatch(t) ? null : 'Quita los símbolos < y >.';
    };
  }

  String? _validarFecha(String? _) {
    final f = _nacimiento;
    if (f == null) return 'Indica tu fecha de nacimiento.';
    final anios = edad(f, _hoy);
    if (anios < 18) return 'Debes ser mayor de edad para abrir una cuenta.';
    return anios > 120 ? 'Revisa el año de nacimiento.' : null;
  }

  String? _validarCurp(String? v) {
    final t = (v ?? '').trim().toUpperCase();
    if (t.isEmpty) return 'Escribe tu CURP.';
    return switch (errorCurp(t, _nacimiento, _sexo)) {
      ErrorCurp.formato =>
        'La CURP tiene 18 caracteres. Revísala en tu acta o credencial.',
      ErrorCurp.digito => 'La CURP no es válida. Revisa el último carácter.',
      ErrorCurp.fecha => 'La CURP no coincide con tu fecha de nacimiento.',
      ErrorCurp.sexo => 'La CURP no coincide con el sexo que elegiste.',
      null => null,
    };
  }

  String? _validarRfc(String? v) {
    final t = (v ?? '').trim().toUpperCase();
    if (t.isEmpty) return 'Escribe tu RFC.';
    return switch (errorRfc(t, _t('curp').toUpperCase())) {
      ErrorRfc.formato => 'El RFC tiene 13 caracteres, con homoclave.',
      ErrorRfc.generico => 'Usa tu RFC personal, no el genérico.',
      ErrorRfc.curp => 'El RFC no corresponde a tu CURP.',
      null => null,
    };
  }

  // ------------------------------------------------------------ navegación

  void _continuar() {
    FocusScope.of(context).unfocus();
    if (!(_formKeys[_paso].currentState?.validate() ?? false)) return;
    if (_paso < 3) {
      setState(() {
        _paso++;
        _error = null;
      });
    } else {
      _crear();
    }
  }

  void _atras() => setState(() {
        _paso--;
        _error = null;
      });

  Future<void> _elegirFecha() async {
    final hoy = _hoy;
    final elegida = await showDatePicker(
      context: context,
      initialDate: _nacimiento ?? DateTime(hoy.year - 25, hoy.month, hoy.day),
      firstDate: DateTime(hoy.year - 120),
      lastDate: hoy,
      helpText: 'Fecha de nacimiento',
      cancelText: 'Cancelar',
      confirmText: 'Aceptar',
      fieldLabelText: 'Fecha',
    );
    if (elegida == null || !mounted) return;
    setState(() {
      _nacimiento = elegida;
      _c['fechaNacimiento']!.text =
          '${elegida.day.toString().padLeft(2, '0')}/'
          '${elegida.month.toString().padLeft(2, '0')}/${elegida.year}';
    });
    _limpiar('fechaNacimiento');
  }

  Future<void> _crear() async {
    if (_enviando) return;
    setState(() {
      _enviando = true;
      _error = null;
    });
    final r =
        await ref.read(registroDatasourceProvider).registrar(_solicitud());
    if (!mounted) return;
    setState(() => _enviando = false);
    switch (r) {
      case Ok<CuentaCreada>(:final data):
        for (final k in ['password', 'confirmacion', 'pin', 'confirmacionPin']) {
          _c[k]!.clear();
        }
        setState(() => _creada = data);
      case Failure():
        if (r.tipo == TipoFallo.validacion && r.campos.isNotEmpty) {
          final rechazos = <String, String>{
            for (final c in r.campos)
              if (_campoDe[c.campo] != null)
                _campoDe[c.campo]!: 'Revisa este dato.',
          };
          final paso =
              _camposPorPaso.indexWhere((cs) => cs.any(rechazos.containsKey));
          setState(() {
            _rechazados = rechazos;
            _error = 'Revisa los datos marcados.';
            if (paso >= 0) _paso = paso;
          });
          return;
        }
        setState(() {
          _error = r.httpStatus == 409
              ? 'Alguno de tus datos (CURP, RFC, correo o usuario) ya está '
                  'registrado. Si ya tienes cuenta, inicia sesión.'
              : mensajeDeFallo(r);
        });
    }
  }

  Map<String, Object?> _solicitud() {
    final f = _nacimiento!;
    final fecha = '${f.year.toString().padLeft(4, '0')}-'
        '${f.month.toString().padLeft(2, '0')}-'
        '${f.day.toString().padLeft(2, '0')}';
    return {
      'bandera': 1,
      'datosPersonales': {
        'nombre': _t('nombre'),
        if (_t('segundoNombre').isNotEmpty) 'segundoNombre': _t('segundoNombre'),
        'apellidoPaterno': _t('apellidoPaterno'),
        'apellidoMaterno': _t('apellidoMaterno'),
        'fechaNacimiento': fecha,
        'curp': _t('curp').toUpperCase(),
        'rfc': _t('rfc').toUpperCase(),
        'sexo': _sexo,
        'nacionalidad': _nacionalidad,
        'estadoCivil': _estadoCivil,
      },
      'datosContacto': {
        'correo': _t('correo').toLowerCase(),
        'telefonoMovil': int.parse(_t('telefono')),
      },
      'domicilio': {
        'calle': _t('calle'),
        'numeroExterior': _t('numeroExterior'),
        if (_t('numeroInterior').isNotEmpty)
          'numeroInterior': _t('numeroInterior'),
        'colonia': _t('colonia'),
        'municipio': _t('municipio'),
        'estado': _t('estado'),
        'codigoPostal': _t('codigoPostal'),
        'pais': 'MEXICO',
      },
      'informacionLaboral': {
        'ocupacion': _t('ocupacion'),
        'empresa': _t('empresa'),
        'ingresoMensual': double.parse(_t('ingresoMensual')),
      },
      'loginCredenciales': {
        'username': _t('username'),
        'password': _c['password']!.text,
        'pin': _c['pin']!.text,
      },
    };
  }

  // ------------------------------------------------------------ vista

  @override
  Widget build(BuildContext context) {
    final ancho = MediaQuery.sizeOf(context).width;
    final colores = ColoresApp.de(context);
    final contenido = ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 420),
      child: _creada == null
          ? _formulario(context)
          : _exito(context, _creada!),
    );
    // Misma marca y foto que el inicio de sesión.
    return AnnotatedRegion(
      value: kBarraEstadoSobreNegro,
      child: Scaffold(
        backgroundColor: colores.superficie,
        body: ancho >= kAnchoLoginAncho
            ? Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [MarcaLogin(), Expanded(child: FotoLogin())],
                    ),
                  ),
                  Expanded(
                    child: SafeArea(
                      child: SingleChildScrollView(
                        keyboardDismissBehavior:
                            ScrollViewKeyboardDismissBehavior.onDrag,
                        padding: const EdgeInsets.all(Esp.x12),
                        child: Align(
                          alignment: Alignment.topCenter,
                          child: contenido,
                        ),
                      ),
                    ),
                  ),
                ],
              )
            : SingleChildScrollView(
                keyboardDismissBehavior:
                    ScrollViewKeyboardDismissBehavior.onDrag,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const MarcaLogin(),
                    SafeArea(
                      top: false,
                      child: Padding(
                        padding: const EdgeInsets.fromLTRB(
                          Esp.margenLogin,
                          Esp.x8,
                          Esp.margenLogin,
                          Esp.x8,
                        ),
                        child: contenido,
                      ),
                    ),
                  ],
                ),
              ),
      ),
    );
  }

  Widget _titulo(BuildContext context, String texto) {
    return Semantics(
      header: true,
      child: Text(
        texto,
        style: estiloTitular(
          tamano: Escala.s40,
          color: ColoresApp.de(context).texto,
        ),
      ),
    );
  }

  Widget _formulario(BuildContext context) {
    final colores = ColoresApp.de(context);
    final campos = switch (_paso) {
      0 => _pasoDatos(),
      1 => _pasoContacto(),
      2 => _pasoTrabajo(),
      _ => _pasoAcceso(colores),
    };
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _titulo(context, 'Crear cuenta'),
        const SizedBox(height: Esp.x6),
        IndicadorPasos(actual: _paso + 1, etiquetas: _pasos),
        const SizedBox(height: Esp.x6),
        if (_error != null) ...[
          BannerMensaje(tipo: TipoBanner.error, mensaje: _error!),
          const SizedBox(height: Esp.x4),
        ],
        Form(
          key: _formKeys[_paso],
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (var i = 0; i < campos.length; i++) ...[
                if (i > 0) const SizedBox(height: Esp.x4),
                campos[i],
              ],
            ],
          ),
        ),
        const SizedBox(height: Esp.x6),
        FilledButton(
          onPressed: _enviando ? null : _continuar,
          child: _enviando
              ? Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    SizedBox(
                      height: Medidas.giro,
                      width: Medidas.giro,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: colores.textoSecundario,
                      ),
                    ),
                    const SizedBox(width: Esp.x3),
                    const Flexible(child: Text('Creando tu cuenta…')),
                  ],
                )
              : Text(_paso == 3 ? 'Crear cuenta' : 'Continuar'),
        ),
        if (_paso > 0) ...[
          const SizedBox(height: Esp.x3),
          OutlinedButton(
            onPressed: _enviando ? null : _atras,
            child: const Text('Atrás'),
          ),
        ],
        const SizedBox(height: Esp.x6),
        Wrap(
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            Text(
              '¿Ya tienes cuenta? ',
              style: estiloTexto(
                tamano: Escala.s14,
                color: colores.textoSecundario,
              ),
            ),
            Enlace(
              texto: 'Inicia sesión',
              alTocar: () => context.go(Rutas.login),
            ),
          ],
        ),
      ],
    );
  }

  Widget _campo(
    String k,
    String etiqueta, {
    String? Function(String?)? validador,
    TextInputType? teclado,
    List<TextInputFormatter> formatos = const [],
    List<String>? autofill,
    bool mayusculas = false,
    String? ayuda,
    int? maximo,
  }) {
    return TextFormField(
      key: ValueKey('registro-$k'),
      controller: _c[k],
      enabled: !_enviando,
      keyboardType: teclado,
      textCapitalization:
          mayusculas ? TextCapitalization.characters : TextCapitalization.none,
      inputFormatters: [
        if (maximo != null) LengthLimitingTextInputFormatter(maximo),
        ...formatos,
      ],
      autofillHints: autofill,
      autocorrect: false,
      textInputAction: TextInputAction.next,
      decoration: InputDecoration(
        labelText: etiqueta,
        helperText: ayuda,
        helperMaxLines: 2,
        errorMaxLines: 3,
      ),
      validator: validador,
      forceErrorText: _rechazo(k),
      onChanged: (_) => _limpiar(k),
    );
  }

  Widget _lista(
    String k,
    String etiqueta,
    String? valor,
    Map<String, String> opciones,
    ValueChanged<String?> alCambiar,
  ) {
    return DropdownButtonFormField<String>(
      key: ValueKey('registro-$k'),
      initialValue: valor,
      isExpanded: true,
      decoration: InputDecoration(labelText: etiqueta),
      items: [
        for (final e in opciones.entries)
          DropdownMenuItem(value: e.key, child: Text(e.value)),
      ],
      validator: (v) => v == null ? 'Elige una opción.' : null,
      forceErrorText: _rechazo(k),
      onChanged: _enviando
          ? null
          : (v) {
              alCambiar(v);
              _limpiar(k);
            },
    );
  }

  List<Widget> _pasoDatos() => [
        _campo('nombre', 'Nombre',
            validador: _nombre(), autofill: const [AutofillHints.givenName]),
        _campo('segundoNombre', 'Segundo nombre (opcional)',
            validador: _nombre(requerido: false),
            autofill: const [AutofillHints.middleName]),
        _campo('apellidoPaterno', 'Apellido paterno',
            validador: _nombre(), autofill: const [AutofillHints.familyName]),
        _campo('apellidoMaterno', 'Apellido materno', validador: _nombre()),
        TextFormField(
          key: const ValueKey('registro-fechaNacimiento'),
          controller: _c['fechaNacimiento'],
          readOnly: true,
          enabled: !_enviando,
          decoration: const InputDecoration(
            labelText: 'Fecha de nacimiento',
            suffixIcon: Icon(Icons.calendar_today_outlined),
          ),
          validator: _validarFecha,
          forceErrorText: _rechazo('fechaNacimiento'),
          onTap: _elegirFecha,
        ),
        _lista(
          'sexo',
          'Sexo',
          _sexo,
          const {'FEMENINO': 'Femenino', 'MASCULINO': 'Masculino', 'OTRO': 'Otro'},
          (v) => setState(() => _sexo = v),
        ),
        _lista(
          'estadoCivil',
          'Estado civil',
          _estadoCivil,
          const {
            'SOLTERO': 'Soltero(a)',
            'CASADO': 'Casado(a)',
            'UNION LIBRE': 'Unión libre',
            'DIVORCIADO': 'Divorciado(a)',
            'VIUDO': 'Viudo(a)',
          },
          (v) => setState(() => _estadoCivil = v),
        ),
        _lista(
          'nacionalidad',
          'Nacionalidad',
          _nacionalidad,
          {for (final n in _nacionalidades) n: n},
          (v) => setState(() => _nacionalidad = v ?? 'MEXICANA'),
        ),
        _campo('curp', 'CURP',
            validador: _validarCurp,
            mayusculas: true,
            maximo: 18,
            ayuda: 'La encuentras en tu acta de nacimiento o en tu INE.'),
        _campo('rfc', 'RFC con homoclave',
            validador: _validarRfc, mayusculas: true, maximo: 13),
      ];

  List<Widget> _pasoContacto() => [
        _campo('correo', 'Correo electrónico',
            teclado: TextInputType.emailAddress,
            autofill: const [AutofillHints.email], validador: (v) {
          final t = (v ?? '').trim();
          if (t.isEmpty) return 'Escribe tu correo.';
          return t.length <= 100 && patronCorreo.hasMatch(t)
              ? null
              : 'Revisa tu correo (ejemplo: nombre@dominio.com).';
        }),
        _campo('telefono', 'Celular (10 dígitos)',
            teclado: TextInputType.phone,
            maximo: 10,
            formatos: [FilteringTextInputFormatter.digitsOnly],
            autofill: const [AutofillHints.telephoneNumberNational],
            validador: (v) {
          final t = (v ?? '').trim();
          if (t.isEmpty) return 'Escribe tu celular.';
          return RegExp(r'^[1-9]\d{9}$').hasMatch(t)
              ? null
              : 'El celular tiene 10 dígitos.';
        }),
        _campo('calle', 'Calle',
            validador: _texto(100),
            autofill: const [AutofillHints.streetAddressLine1]),
        _campo('numeroExterior', 'Número exterior', validador: _texto(20)),
        _campo('numeroInterior', 'Número interior (opcional)',
            validador: _texto(20, requerido: false)),
        _campo('colonia', 'Colonia', validador: _texto(100)),
        _campo('codigoPostal', 'Código postal',
            teclado: TextInputType.number,
            maximo: 5,
            formatos: [FilteringTextInputFormatter.digitsOnly],
            autofill: const [AutofillHints.postalCode], validador: (v) {
          final t = (v ?? '').trim();
          if (t.isEmpty) return 'Escribe tu código postal.';
          return RegExp(r'^\d{5}$').hasMatch(t)
              ? null
              : 'El código postal tiene 5 dígitos.';
        }),
        _campo('municipio', 'Municipio o alcaldía',
            validador: _texto(100), autofill: const [AutofillHints.addressCity]),
        _campo('estado', 'Estado',
            validador: _texto(100),
            autofill: const [AutofillHints.addressState]),
      ];

  List<Widget> _pasoTrabajo() => [
        _campo('ocupacion', 'Ocupación',
            validador: _texto(100), autofill: const [AutofillHints.jobTitle]),
        _campo('empresa', 'Empresa donde trabajas',
            validador: _texto(100),
            autofill: const [AutofillHints.organizationName]),
        _campo('ingresoMensual', 'Ingreso mensual (MXN)',
            teclado: const TextInputType.numberWithOptions(decimal: true),
            formatos: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.]'))],
            validador: (v) {
          final t = (v ?? '').trim();
          if (t.isEmpty) return 'Indica tu ingreso mensual.';
          if (!RegExp(r'^\d{1,12}(\.\d{1,2})?$').hasMatch(t)) {
            return 'Escribe solo números, con hasta 2 decimales.';
          }
          return double.parse(t) <= 0 ? 'El ingreso debe ser mayor a cero.' : null;
        }),
      ];

  List<Widget> _pasoAcceso(ColoresApp colores) {
    Widget contrasena(
      String k,
      String etiqueta,
      String? Function(String?) validador,
    ) {
      return TextFormField(
        key: ValueKey('registro-$k'),
        controller: _c[k],
        enabled: !_enviando,
        obscureText: !_verPassword,
        enableSuggestions: false,
        autocorrect: false,
        autofillHints: const [AutofillHints.newPassword],
        decoration: InputDecoration(
          labelText: etiqueta,
          errorMaxLines: 3,
          suffixIcon: IconButton(
            tooltip: _verPassword ? 'Ocultar contraseña' : 'Mostrar contraseña',
            icon: Icon(
              _verPassword
                  ? Icons.visibility_off_outlined
                  : Icons.visibility_outlined,
            ),
            onPressed: () => setState(() => _verPassword = !_verPassword),
          ),
        ),
        validator: validador,
        forceErrorText: _rechazo(k),
        onChanged: (_) => _limpiar(k),
      );
    }

    Widget pin(String k, String etiqueta, String? Function(String?) validador) {
      return TextFormField(
        key: ValueKey('registro-$k'),
        controller: _c[k],
        enabled: !_enviando,
        obscureText: true,
        keyboardType: TextInputType.number,
        inputFormatters: [
          FilteringTextInputFormatter.digitsOnly,
          LengthLimitingTextInputFormatter(4),
        ],
        decoration: InputDecoration(labelText: etiqueta),
        validator: validador,
        forceErrorText: _rechazo(k),
        onChanged: (_) => _limpiar(k),
      );
    }

    return [
      _campo('username', 'Usuario',
          autofill: const [AutofillHints.newUsername],
          ayuda: 'Con él iniciarás sesión.', validador: (v) {
        final t = (v ?? '').trim();
        if (t.isEmpty) return 'Elige un usuario.';
        return patronUsuario.hasMatch(t)
            ? null
            : 'De 3 a 50 caracteres: letras, números, punto, guion o guion bajo.';
      }),
      contrasena('password', 'Contraseña', (v) {
        if ((v ?? '').isEmpty) return 'Crea una contraseña.';
        return passwordValida(v!)
            ? null
            : 'Usa de 8 a 72 caracteres, con mayúscula, minúscula, número y '
                'un símbolo, sin espacios.';
      }),
      contrasena('confirmacion', 'Confirma la contraseña', (v) {
        if ((v ?? '').isEmpty) return 'Escribe otra vez tu contraseña.';
        return v != _c['password']!.text ? 'Las contraseñas no coinciden.' : null;
      }),
      pin('pin', 'PIN de 4 dígitos', (v) {
        final t = v ?? '';
        if (t.isEmpty) return 'Crea tu PIN de 4 dígitos.';
        if (!RegExp(r'^\d{4}$').hasMatch(t)) return 'El PIN tiene 4 dígitos.';
        return pinTrivial(t) ? 'Evita PIN como 1111 o 1234.' : null;
      }),
      pin('confirmacionPin', 'Confirma el PIN', (v) {
        if ((v ?? '').isEmpty) return 'Escribe otra vez tu PIN.';
        return v != _c['pin']!.text ? 'Los PIN no coinciden.' : null;
      }),
      Text(
        'El PIN autoriza cada pago que hagas. No lo compartas con nadie.',
        style: estiloTexto(tamano: Escala.s14, color: colores.textoSecundario),
      ),
    ];
  }

  Widget _exito(BuildContext context, CuentaCreada cuenta) {
    final colores = ColoresApp.de(context);
    final saldo = formatearMoneda(cuenta.saldoInicial);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _titulo(context, 'Tu cuenta está lista'),
        const SizedBox(height: Esp.x2),
        Text(
          'Ya puedes iniciar sesión con tu usuario y tu contraseña.',
          style: estiloTexto(
            tamano: Escala.s14,
            altura: 1.4,
            color: colores.textoSecundario,
          ),
        ),
        const SizedBox(height: Esp.x6),
        FichaDatos(
          filas: [
            FilaDato('Titular', cuenta.nombreCompleto),
            FilaDato('Usuario', cuenta.username),
            FilaDato('Número de cuenta', cuenta.numeroCuenta),
            FilaDato('Saldo disponible', saldo),
          ],
        ),
        const SizedBox(height: Esp.x6),
        BannerMensaje(
          tipo: TipoBanner.info,
          mensaje: 'Tu cuenta inicia con saldo de $saldo. Para pagar servicios, '
              'pide una recarga en tu sucursal BanGus con tu número de cuenta.',
        ),
        const SizedBox(height: Esp.x6),
        FilledButton(
          onPressed: () => context.go(Rutas.login),
          child: const Text('Iniciar sesión'),
        ),
      ],
    );
  }
}

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/config/app_theme.dart';
import '../../../../core/router/app_router.dart';
import '../../../../core/session/session_controller.dart';
import '../../../../shared/widgets/banner_imagen.dart';
import '../../../../shared/widgets/banner_mensaje.dart';
import '../../../../shared/widgets/enlace.dart';
import '../../../../shared/widgets/logo_bangus.dart';
import '../providers/login_controller.dart';
import '../widgets/validadores_login.dart';
import '../../../../shared/entrada/entrada.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _usuarioCtrl = TextEditingController();
  final _passwordCtrl = TextEditingController();
  bool _verPassword = false;

  @override
  void initState() {
    super.initState();
    Future.microtask(() {
      if (mounted) ref.read(loginControllerProvider.notifier).reiniciar();
    });
  }

  @override
  void dispose() {
    _usuarioCtrl.dispose();
    _passwordCtrl.dispose();
    super.dispose();
  }

  Future<void> _enviar() async {
    FocusScope.of(context).unfocus();
    if (!(_formKey.currentState?.validate() ?? false)) return;
    final usuario = _usuarioCtrl.text.trim();
    final password = _passwordCtrl.text;
    // La contraseña se limpia tras cada intento.
    _passwordCtrl.clear();
    await ref
        .read(loginControllerProvider.notifier)
        .iniciarSesion(username: usuario, password: password);
  }

  void _alEditar() {
    final controlador = ref.read(loginControllerProvider.notifier);
    controlador.limpiarErrores();
  }

  @override
  Widget build(BuildContext context) {
    final ancho = MediaQuery.sizeOf(context).width;
    final colores = ColoresApp.de(context);
    // Barra negra sólida con el logotipo, debajo la foto (sin fundidos) y el
    // formulario sobre blanco.
    return AnnotatedRegion(
      value: kBarraEstadoSobreNegro,
      child: Scaffold(
        backgroundColor: colores.superficie,
        body: ancho >= kAnchoLoginAncho ? _anchoAmplio() : _telefono(context),
      ),
    );
  }

  /// Teléfono: una columna. Bloque de marca negro, foto de 200 y formulario.
  Widget _telefono(BuildContext context) {
    return SingleChildScrollView(
      keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const MarcaLogin(),
          const FotoLogin(alto: 200),
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(
                Esp.margenLogin,
                Esp.x8,
                Esp.margenLogin,
                Esp.x8,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const _TituloLogin(),
                  const SizedBox(height: Esp.x8),
                  _formulario(context),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// Pantalla ancha: marca y foto a la izquierda, formulario a la derecha.
  Widget _anchoAmplio() {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              MarcaLogin(),
              Expanded(child: FotoLogin()),
            ],
          ),
        ),
        Expanded(
          child: SafeArea(
            child: Center(
              child: SingleChildScrollView(
                keyboardDismissBehavior:
                    ScrollViewKeyboardDismissBehavior.onDrag,
                padding: const EdgeInsets.all(Esp.x12),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 380),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const _TituloLogin(),
                      const SizedBox(height: Esp.x8),
                      _formulario(context),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _formulario(BuildContext context) {
    final estado = ref.watch(loginControllerProvider);
    final aviso = ref.watch(
      sessionControllerProvider.select((s) => s.avisoSesion),
    );
    final cargando = estado.cargando;

    return Form(
      key: _formKey,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (aviso != null) ...[
            BannerMensaje(tipo: TipoBanner.info, mensaje: aviso),
            const SizedBox(height: Esp.x6),
          ],
          TextFormField(
            controller: _usuarioCtrl,
            enabled: !cargando,
            keyboardType: TextInputType.text,
            textInputAction: TextInputAction.next,
            enableSuggestions: false,
            autocorrect: false,
            autofillHints: const [AutofillHints.username],
            inputFormatters: Entrada.usuario.formatos(),
            decoration: const InputDecoration(labelText: 'Usuario'),
            validator: ValidadoresLogin.usuario,
            forceErrorText: estado.erroresCampo['username'],
            onChanged: (_) => _alEditar(),
          ),
          const SizedBox(height: Esp.x4),
          TextFormField(
            controller: _passwordCtrl,
            enabled: !cargando,
            obscureText: !_verPassword,
            enableSuggestions: false,
            autocorrect: false,
            autofillHints: const [AutofillHints.password],
            inputFormatters: Entrada.password.formatos(),
            textInputAction: TextInputAction.done,
            onFieldSubmitted: (_) => cargando ? null : _enviar(),
            decoration: InputDecoration(
              labelText: 'Contraseña',
              suffixIcon: IconButton(
                tooltip:
                    _verPassword ? 'Ocultar contraseña' : 'Mostrar contraseña',
                icon: Icon(
                  _verPassword
                      ? Icons.visibility_off_outlined
                      : Icons.visibility_outlined,
                ),
                onPressed: () => setState(() => _verPassword = !_verPassword),
              ),
            ),
            validator: ValidadoresLogin.password,
            forceErrorText: estado.erroresCampo['password'],
            onChanged: (_) => _alEditar(),
          ),
          const SizedBox(height: Esp.x6),
          if (estado.estado == EstadoLogin.error && estado.mensaje != null) ...[
            BannerMensaje(tipo: TipoBanner.error, mensaje: estado.mensaje!),
            const SizedBox(height: Esp.x4),
          ],
          Semantics(
            liveRegion: estado.estado == EstadoLogin.cargando,
            child: FilledButton(
              onPressed: cargando ? null : _enviar,
              child: cargando
                  ? Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        SizedBox(
                          height: Medidas.giro,
                          width: Medidas.giro,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: ColoresApp.de(context).textoSecundario,
                          ),
                        ),
                        const SizedBox(width: Esp.x3),
                        const Flexible(child: Text('Iniciando sesión…')),
                      ],
                    )
                  : const Text('Iniciar sesión'),
            ),
          ),
          const SizedBox(height: Esp.x6),
          Wrap(
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              Text(
                '¿Aún no tienes cuenta? ',
                style: estiloTexto(
                  tamano: Escala.s14,
                  color: ColoresApp.de(context).textoSecundario,
                ),
              ),
              Enlace(
                texto: 'Crear cuenta',
                alTocar: cargando ? () {} : () => context.go(Rutas.registro),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// Ancho a partir del cual el inicio de sesión usa dos paneles.
const double kAnchoLoginAncho = 720;

/// Franja superior con la foto fundida a negro y el logotipo encima. Sin
/// [alto], ocupa todo el alto disponible (pantalla ancha).
/// Bloque de marca: barra negra sólida con filo metálico y el logotipo.
class MarcaLogin extends StatelessWidget {
  const MarcaLogin({super.key});

  @override
  Widget build(BuildContext context) {
    return SobreNegro(
      child: DecoratedBox(
        decoration: superficieMetal(filoArriba: false),
        child: const SafeArea(
          bottom: false,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              ColoredBox(
                color: Paleta.filoLuz,
                child: SizedBox(height: 1),
              ),
              Padding(
                padding: EdgeInsets.symmetric(
                  horizontal: Esp.margenLogin,
                  vertical: Esp.x6,
                ),
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: LogotipoBanGus(tamanoTexto: Escala.s28),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Foto del login con recorte limpio (sin fundidos ni texto encima). Sin
/// [alto], ocupa todo el alto disponible (pantalla ancha).
class FotoLogin extends StatelessWidget {
  const FotoLogin({super.key, this.alto});

  final double? alto;

  @override
  Widget build(BuildContext context) {
    final foto = ImagenBanner(
      asset: FotosBanner.login,
      semantica: 'Una pareja en un sillón revisa sus pagos en una laptop.',
      alineacion: const Alignment(0, -0.35),
      alto: alto ?? double.infinity,
    );
    return alto == null ? foto : SizedBox(height: alto, child: foto);
  }
}

class _TituloLogin extends StatelessWidget {
  const _TituloLogin();

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Semantics(
          header: true,
          child: Text(
            'Inicia sesión',
            style: estiloTitular(
              tamano: Escala.s40,
              color: colores.texto,
            ),
          ),
        ),
        const SizedBox(height: Esp.x2),
        Text(
          'Paga luz, agua, teléfono y recargas con el usuario y la '
          'contraseña de tu banca.',
          style: estiloTexto(
            tamano: Escala.s14,
            altura: 1.4,
            color: colores.textoSecundario,
          ),
        ),
      ],
    );
  }
}

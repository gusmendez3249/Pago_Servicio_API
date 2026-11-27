package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.config.onboarding.PasswordEncoderUtil;
import com.proyecto.servicios.dto.onboarding.*;
import com.proyecto.servicios.entity.onboarding.*;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.exception.ClienteInactivoException;
import com.proyecto.servicios.exception.ClienteNoEncontradoException;
import com.proyecto.servicios.exception.NacionalidadNoValidaException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.exception.RegistroDuplicadoException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.repositorys.onboarding.*;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.security.SecureRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClienteLayawayService {

    private final ClienteRepository clienteRepository;
    private final DomicilioRepository domicilioRepository;
    private final CuentaRepository cuentaRepository;
    private final UsuarioLoginRepository usuarioLoginRepository;
    private final CatNacionalidadRepository catNacionalidadRepository;
    private final PasswordEncoderUtil passwordEncoderUtil;
    private final TransactionTemplate transactionTemplate;
    private final SesionService sesionService;

    /**
     * Saldo con el que nace una cuenta. En producción 0.00: el registro es público y un saldo regalado sería dinero
     * gratis ilimitado. Solo un ambiente de pruebas lo sube (APP_SALDO_INICIAL).
     */
    @Value("${app.cuentas.saldo-inicial:0.00}")
    private BigDecimal saldoInicialCuenta = new BigDecimal("0.00");

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Tope de clientes por consulta (bandera 4): protege la memoria cuando no se envían filtros. */
    /** Clientes por página en la consulta (bandera 4). */
    static final int TAMANIO_PAGINA_CONSULTA = 50;

    /**
     * Cada operación corre en su propia transacción (TransactionTemplate) en lugar de @Transactional
     * sobre todo el método: el hash PBKDF2 de la contraseña es costoso a propósito y debe calcularse
     * ANTES de abrir la transacción, para no retener una conexión del pool mientras tanto
     * (bajo carga con JMeter eso agotaba el pool y producía respuestas 503).
     */
    public LayawayClienteResponse procesarOperacion(LayawayClienteRequest request, String authorization) {
        // El cuerpo ya llega validado por @Valid en el controlador (anotaciones del DTO y @OperacionClienteValida).
        // Aquí: 1) autenticación (401), 2) autorización por rol (403) y 3) reglas de negocio contra la base de datos.
        // El registro (bandera 1) es público: no requiere token.
        Integer bandera = request.getBandera();
        Long propietario = autorizar(bandera, authorization);

        return switch (bandera) {
            case 1 -> {
                LoginCredencialesDto login = request.getLoginCredenciales();
                String passwordHash = login != null ? passwordEncoderUtil.encode(login.getPassword()) : null;
                String pinHash = hashPin(login);
                Biometricos biometricos = hashBiometricosRegistro(request);
                yield transactionTemplate.execute(status -> registrarCliente(request, passwordHash, pinHash, biometricos));
            }
            case 2 -> {
                LoginCredencialesDto login = request.getLoginCredenciales();
                String passwordHash = (login != null && login.getPassword() != null) ? passwordEncoderUtil.encode(login.getPassword()) : null;
                String pinHash = hashPin(login);
                Biometricos biometricos = new Biometricos(
                        passwordEncoderUtil.encodeBiometrico(login != null ? login.getFaceIdBiometrico() : null),
                        passwordEncoderUtil.encodeBiometrico(request.getDatosPersonales() != null
                                ? request.getDatosPersonales().getDatosBiometricos() : null));
                yield transactionTemplate.execute(status -> actualizarCliente(request, passwordHash, pinHash, biometricos, propietario));
            }
            case 3 -> transactionTemplate.execute(status -> darDeBajaCliente(request));
            case 4 -> transactionTemplate.execute(status -> consultarClientes(request, propietario));
            default -> throw new ReglaNegocioException("Bandera de operación no válida: " + bandera + ". Use 1 (Insertar), 2 (Actualizar), 3 (Eliminar) o 4 (Consultar).");
        };
    }

    /** Hashes PBKDF2 del Face ID del usuario y de los datos biométricos del cliente (nulos si no se enviaron). */
    private record Biometricos(String faceIdHash, String datosBiometricosHash) {
    }

    /**
     * En el registro, el Face ID y los datos biométricos se usan uno como respaldo del otro. Se calculan FUERA de la
     * transacción (PBKDF2 es costoso) y, si son el mismo valor, se hashea una sola vez.
     */
    private Biometricos hashBiometricosRegistro(LayawayClienteRequest request) {
        Long faceId = request.getLoginCredenciales() != null ? request.getLoginCredenciales().getFaceIdBiometrico() : null;
        Long datos = request.getDatosPersonales() != null ? request.getDatosPersonales().getDatosBiometricos() : null;
        Long paraLogin = faceId != null ? faceId : datos;
        Long paraCliente = datos != null ? datos : faceId;
        String hashLogin = passwordEncoderUtil.encodeBiometrico(paraLogin);
        String hashCliente = java.util.Objects.equals(paraLogin, paraCliente)
                ? hashLogin : passwordEncoderUtil.encodeBiometrico(paraCliente);
        return new Biometricos(hashLogin, hashCliente);
    }

    /** El PIN (2FA de los pagos) se guarda con PBKDF2, igual que la contraseña: nunca en claro. */
    private String hashPin(LoginCredencialesDto login) {
        return (login != null && login.getPin() != null) ? passwordEncoderUtil.encode(login.getPin()) : null;
    }

    /**
     * Autentica (401) y autoriza por rol (403) las banderas 2 a 4. Devuelve el cliente al que se limita la
     * operación, o null cuando no aplica (registro público, baja por ejecutivo o ejecutivo con acceso total).
     */
    private Long autorizar(Integer bandera, String authorization) {
        if (bandera < 2 || bandera > 4) {
            return null;
        }
        SesionUsuario sesion = sesionService.autenticar(authorization);
        if (bandera == 3) {
            if (!sesion.esEjecutivo()) {
                throw new AccesoDenegadoException("Solo un ejecutivo puede dar de baja clientes.");
            }
            return null;
        }
        // Autorización a nivel de recurso: un cliente solo accede a SU registro; el ejecutivo, a cualquiera (null)
        return propietarioPermitido(sesion);
    }

    private Long propietarioPermitido(SesionUsuario sesion) {
        if (sesion.esEjecutivo()) {
            return null;
        }
        if (sesion.clienteId() == null) {
            throw new AccesoDenegadoException("El usuario no tiene un cliente asociado.");
        }
        return sesion.clienteId();
    }

    /**
     * Mismo mensaje para CURP, RFC, correo o usuario repetidos y sin repetir el dato: el registro es público y no debe
     * servir para comprobar si una persona ya es cliente ni devolver sus datos personales.
     */
    private static final String REGISTRO_DUPLICADO = "No es posible completar el registro: alguno de los datos "
            + "proporcionados (CURP, RFC, correo o usuario) ya está registrado. Si ya es cliente, inicie sesión.";

    private LayawayClienteResponse registrarCliente(LayawayClienteRequest request, String passwordHash, String pinHash,
                                                   Biometricos biometricos) {
        DatosPersonalesDto dp = request.getDatosPersonales();
        DatosContactoDto dc = request.getDatosContacto();
        DomicilioDto dom = request.getDomicilio();
        InformacionLaboralDto infoLab = request.getInformacionLaboral();
        LoginCredencialesDto loginDto = request.getLoginCredenciales();

        // El formato, las secciones obligatorias y la mayoría de edad (@MayorDeEdad) ya se validaron en el DTO.
        // 1. Validar catálogo de nacionalidad en la base de datos (coincidencia exacta)
        validarNacionalidadEnBD(dp.getNacionalidad());

        // 2. Validar Unicidad de CURP, RFC, Correo
        if (clienteRepository.existsByCurp(dp.getCurp().trim().toUpperCase())) {
            throw new RegistroDuplicadoException(REGISTRO_DUPLICADO);
        }
        if (clienteRepository.existsByRfc(dp.getRfc().trim().toUpperCase())) {
            throw new RegistroDuplicadoException(REGISTRO_DUPLICADO);
        }
        if (clienteRepository.existsByCorreo(dc.getCorreo().trim().toLowerCase())) {
            throw new RegistroDuplicadoException(REGISTRO_DUPLICADO);
        }

        String username = (loginDto != null && loginDto.getUsername() != null && !loginDto.getUsername().trim().isEmpty())
                ? loginDto.getUsername().trim()
                : dp.getCurp().trim().toLowerCase();

        if (usuarioLoginRepository.existsByUsername(username)) {
            throw new RegistroDuplicadoException(REGISTRO_DUPLICADO);
        }

        // 3. Crear Domicilio
        DomicilioEntity domicilioEntity = DomicilioEntity.builder()
                .calle(limpiar(dom.getCalle()))
                .numeroExterior(limpiar(dom.getNumeroExterior()))
                .numeroInterior(limpiar(dom.getNumeroInterior()))
                .colonia(limpiar(dom.getColonia()))
                .municipio(limpiar(dom.getMunicipio()))
                .estado(limpiar(dom.getEstado()))
                .codigoPostal(dom.getCodigoPostal())
                .pais(dom.getPais() != null ? limpiar(dom.getPais()) : "México")
                .build();
        domicilioRepository.save(domicilioEntity);

        // 4. Crear Cliente
        ClienteEntity clienteEntity = ClienteEntity.builder()
                .nombre(limpiar(dp.getNombre()))
                .segundoNombre(limpiar(dp.getSegundoNombre()))
                .apellidoPaterno(limpiar(dp.getApellidoPaterno()))
                .apellidoMaterno(limpiar(dp.getApellidoMaterno()))
                .fechaNacimiento(dp.getFechaNacimiento())
                .curp(dp.getCurp().trim().toUpperCase())
                .rfc(dp.getRfc().trim().toUpperCase())
                // Valores de catálogo ya validados como exactos: se guardan tal cual se recibieron
                .sexo(dp.getSexo())
                .nacionalidad(dp.getNacionalidad())
                .estadoCivil(dp.getEstadoCivil())
                .correo(dc.getCorreo().trim().toLowerCase())
                .telefonoMovil(String.valueOf(dc.getTelefonoMovil()))
                .telefonoAlt(dc.getTelefonoAlt() != null ? String.valueOf(dc.getTelefonoAlt()) : null)
                .ocupacion(limpiar(infoLab.getOcupacion()))
                .empresa(limpiar(infoLab.getEmpresa()))
                .ingresoMensual(infoLab.getIngresoMensual())
                .datosBiometricosHash(biometricos.datosBiometricosHash())
                .pin(pinHash)
                .activo(true)
                .fechaRegistro(LocalDateTime.now())
                .domicilio(domicilioEntity)
                .build();
        clienteRepository.save(clienteEntity);

        // 5. Creación Automática de Cuenta Bancaria
        String numeroCuenta = generarNumeroCuentaUnico();
        BigDecimal saldoInicial = saldoInicialCuenta.setScale(2, java.math.RoundingMode.HALF_UP);

        CuentaEntity cuentaEntity = CuentaEntity.builder()
                .numeroCuenta(numeroCuenta)
                .saldo(saldoInicial)
                .estatus("ACTIVA")
                .fechaApertura(LocalDateTime.now())
                .cliente(clienteEntity)
                .build();
        cuentaRepository.save(cuentaEntity);

        // 6. Crear Registro de Login Cifrado (el hash se calculó antes de abrir la transacción).
        // Sin contraseña enviada NO se asigna una por defecto (sería conocida por todos): el hash queda nulo
        // y el acceso por contraseña permanece deshabilitado; solo podrá entrar por Face ID si lo registró.

        UsuarioLoginEntity usuarioLogin = UsuarioLoginEntity.builder()
                .username(username)
                .passwordHash(passwordHash)
                .faceIdHash(biometricos.faceIdHash())
                .isLoggedIn(false)
                .cliente(clienteEntity)
                .build();
        usuarioLoginRepository.save(usuarioLogin);

        log.info("Cliente registrado exitosamente. ID: {}, Cuenta: {}", clienteEntity.getId(),
                com.proyecto.servicios.mapper.PagoServicioMapper.enmascarar(numeroCuenta));

        return LayawayClienteResponse.builder()
                .nombreCompleto(nombreCompleto(clienteEntity))
                .curp(clienteEntity.getCurp())
                .rfc(clienteEntity.getRfc())
                .correo(clienteEntity.getCorreo())
                .activo(clienteEntity.getActivo())
                .numeroCuenta(numeroCuenta)
                .saldoInicial(saldoInicial)
                .estatusCuenta("ACTIVA")
                .username(username)
                .operacionRealizada("INSERTAR")
                .fechaOperacion(LocalDateTime.now())
                .build();
    }

    private LayawayClienteResponse actualizarCliente(LayawayClienteRequest request, String passwordHash, String pinHash,
                                                    Biometricos biometricos, Long soloClienteId) {
        ClienteEntity cliente = localizarCliente(request, soloClienteId);

        if (Boolean.FALSE.equals(cliente.getActivo())) {
            throw new ClienteInactivoException("No se puede actualizar la información de un cliente inactivo.");
        }

        actualizarDatosPersonales(cliente, request.getDatosPersonales());
        actualizarContacto(cliente, request.getDatosContacto());
        actualizarDomicilio(cliente, request.getDomicilio());
        actualizarInformacionLaboral(cliente, request.getInformacionLaboral());
        siPresente(biometricos.datosBiometricosHash(), cliente::setDatosBiometricosHash);
        actualizarCredenciales(cliente, request.getLoginCredenciales(), passwordHash, pinHash, biometricos.faceIdHash());

        clienteRepository.save(cliente);

        List<CuentaEntity> cuentas = cuentaRepository.findByClienteId(cliente.getId());
        String numCuenta = cuentas.isEmpty() ? "N/A" : cuentas.get(0).getNumeroCuenta();
        BigDecimal saldo = cuentas.isEmpty() ? BigDecimal.ZERO : cuentas.get(0).getSaldo();
        String estatusCta = cuentas.isEmpty() ? "N/A" : cuentas.get(0).getEstatus();

        return LayawayClienteResponse.builder()
                .nombreCompleto(nombreCompleto(cliente))
                .curp(cliente.getCurp())
                .rfc(cliente.getRfc())
                .correo(cliente.getCorreo())
                .activo(cliente.getActivo())
                .numeroCuenta(numCuenta)
                .saldoInicial(saldo)
                .estatusCuenta(estatusCta)
                .operacionRealizada("ACTUALIZAR")
                .fechaOperacion(LocalDateTime.now())
                .build();
    }

    private LayawayClienteResponse darDeBajaCliente(LayawayClienteRequest request) {
        ClienteEntity cliente = localizarCliente(request, null);

        // 1. Marcar cliente como inactivo
        cliente.setActivo(false);
        clienteRepository.save(cliente);

        // 2. Desactivar cuentas bancarias asociadas
        List<CuentaEntity> cuentas = cuentaRepository.findByClienteId(cliente.getId());
        for (CuentaEntity c : cuentas) {
            c.setEstatus("INACTIVA");
            cuentaRepository.save(c);
        }

        // 3. Desactivar sesión de login
        usuarioLoginRepository.findByClienteId(cliente.getId()).ifPresent(u -> {
            u.cerrarSesion();
            usuarioLoginRepository.save(u);
        });

        log.info("Baja lógica realizada exitosamente para el cliente ID: {}", cliente.getId());

        String numCuenta = cuentas.isEmpty() ? "N/A" : cuentas.get(0).getNumeroCuenta();
        BigDecimal saldo = cuentas.isEmpty() ? BigDecimal.ZERO : cuentas.get(0).getSaldo();

        return LayawayClienteResponse.builder()
                .nombreCompleto(nombreCompleto(cliente))
                .curp(cliente.getCurp())
                .rfc(cliente.getRfc())
                .correo(cliente.getCorreo())
                .activo(false)
                .numeroCuenta(numCuenta)
                .saldoInicial(saldo)
                .estatusCuenta("INACTIVA")
                .operacionRealizada("ELIMINAR_BAJA_LOGICA")
                .fechaOperacion(LocalDateTime.now())
                .build();
    }

    /**
     * Bandera 4: consulta de clientes por campos llave inmutables (curp, rfc, numeroCuenta).
     * Los filtros de texto son "contiene" sin distinguir mayúsculas; varios filtros se combinan con AND.
     * Sin filtros devuelve el listado paginado (50 por página) con los datos personales enmascarados.
     */
    private LayawayClienteResponse consultarClientes(LayawayClienteRequest request, Long soloClienteId) {
        // Que solo se envíe 'filtros' ya lo garantiza @OperacionClienteValida en el DTO.
        FiltrosClienteDto filtros = request.getFiltros() != null ? request.getFiltros() : new FiltrosClienteDto();
        Specification<ClienteEntity> spec = especificacionConsulta(filtros, false, soloClienteId);

        int numeroPagina = request.getPagina() != null ? request.getPagina() : 0;
        Page<ClienteEntity> pagina = clienteRepository.findAll(spec,
                PageRequest.of(numeroPagina, TAMANIO_PAGINA_CONSULTA, Sort.by("id")));
        // Listado general (sin filtros): datos personales enmascarados. Para ver un registro completo se busca por su llave
        boolean enmascarar = filtros.estaVacio() && soloClienteId == null;

        // Se buscó un cliente concreto y no existe (o no está entre los registros a los que se tiene acceso)
        if (!filtros.estaVacio() && pagina.isEmpty()) {
            throw new ClienteNoEncontradoException("No se encontró ningún cliente con los filtros indicados entre los registros a los que tiene acceso.");
        }

        List<Long> ids = pagina.getContent().stream().map(ClienteEntity::getId).toList();
        Map<Long, List<CuentaEntity>> cuentasPorCliente = ids.isEmpty() ? Map.of()
                : cuentaRepository.findByClienteIdIn(ids).stream()
                        .collect(Collectors.groupingBy(c -> c.getCliente().getId()));

        List<ClienteConsultaDto> clientes = pagina.getContent().stream().map(c -> ClienteConsultaDto.builder()
                .nombreCompleto(nombreCompleto(c))
                .curp(enmascarar ? mascara(c.getCurp()) : c.getCurp())
                .rfc(enmascarar ? mascara(c.getRfc()) : c.getRfc())
                .correo(enmascarar ? mascaraCorreo(c.getCorreo()) : c.getCorreo())
                .telefonoMovil(enmascarar ? mascara(c.getTelefonoMovil()) : c.getTelefonoMovil())
                .activo(c.getActivo())
                .fechaRegistro(c.getFechaRegistro())
                .cuentas(cuentasPorCliente.getOrDefault(c.getId(), List.of()).stream()
                        .map(k -> ClienteConsultaDto.CuentaConsultaDto.builder()
                                .numeroCuenta(enmascarar ? mascara(k.getNumeroCuenta()) : k.getNumeroCuenta())
                                .saldo(k.getSaldo()).estatus(k.getEstatus()).build())
                        .toList())
                .build()).toList();

        return LayawayClienteResponse.builder()
                .operacionRealizada("CONSULTAR")
                .fechaOperacion(LocalDateTime.now())
                .clientes(clientes)
                .totalCoincidencias(pagina.getTotalElements())
                .resultadosTruncados(pagina.hasNext())
                .pagina(numeroPagina)
                .tamanioPagina(TAMANIO_PAGINA_CONSULTA)
                .totalPaginas(pagina.getTotalPages())
                .build();
    }

    private static String mascara(String valor) {
        return com.proyecto.servicios.mapper.PagoServicioMapper.enmascarar(valor);
    }

    /** a****@dominio.com */
    static String mascaraCorreo(String correo) {
        if (correo == null || correo.indexOf('@') < 1) {
            return mascara(correo);
        }
        int arroba = correo.indexOf('@');
        return correo.charAt(0) + "*".repeat(Math.max(1, arroba - 1)) + correo.substring(arroba);
    }

    /**
     * @param exacta       true = coincidencia exacta (identificar a UN cliente para modificarlo);
     *                     false = "contiene" (consulta).
     * @param soloClienteId si no es nulo, limita la búsqueda a ese cliente (autorización a nivel de recurso).
     */
    static Specification<ClienteEntity> especificacionConsulta(FiltrosClienteDto f, boolean exacta, Long soloClienteId) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (soloClienteId != null) {
                // Control de acceso por recurso: la búsqueda queda limitada al registro del propio usuario
                condiciones.add(cb.equal(root.get("id"), soloClienteId));
            }
            if (f.getCurp() != null) {
                String curp = f.getCurp().toUpperCase(Locale.ROOT);
                condiciones.add(exacta ? cb.equal(cb.upper(root.get("curp")), curp)
                        : cb.like(cb.upper(root.get("curp")), "%" + curp + "%"));
            }
            if (f.getRfc() != null) {
                String rfc = f.getRfc().toUpperCase(Locale.ROOT);
                condiciones.add(exacta ? cb.equal(cb.upper(root.get("rfc")), rfc)
                        : cb.like(cb.upper(root.get("rfc")), "%" + rfc + "%"));
            }
            if (f.getNumeroCuenta() != null) {
                Subquery<Long> cuentas = query.subquery(Long.class);
                Root<CuentaEntity> cuenta = cuentas.from(CuentaEntity.class);
                cuentas.select(cuenta.get("id")).where(
                        cb.equal(cuenta.get("cliente"), root),
                        exacta ? cb.equal(cuenta.get("numeroCuenta"), f.getNumeroCuenta())
                                : cb.like(cuenta.get("numeroCuenta"), "%" + f.getNumeroCuenta() + "%"));
                condiciones.add(cb.exists(cuentas));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
    }

    /**
     * Localiza a UN cliente para actualizarlo (bandera 2) o darlo de baja (bandera 3) usando solo llaves
     * que no cambian (curp, rfc, numeroCuenta) con el valor COMPLETO. Nunca se usa el ID interno.
     * Que las llaves vengan y estén completas ya lo valida @OperacionClienteValida en el DTO.
     */
    private ClienteEntity localizarCliente(LayawayClienteRequest request, Long soloClienteId) {
        FiltrosClienteDto filtros = request.getFiltros();
        List<ClienteEntity> encontrados = clienteRepository
                .findAll(especificacionConsulta(filtros, true, soloClienteId), PageRequest.of(0, 2, Sort.by("id"))).getContent();
        if (encontrados.isEmpty()) {
            throw new ClienteNoEncontradoException("No se encontró ningún cliente con las llaves indicadas entre los registros a los que tiene acceso.");
        }
        if (encontrados.size() > 1) {
            throw new ReglaNegocioException("Las llaves indicadas corresponden a más de un cliente.");
        }
        return encontrados.get(0);
    }

    private static String nombreCompleto(ClienteEntity c) {
        return c.getNombre() + " " + (c.getSegundoNombre() != null ? c.getSegundoNombre() + " " : "")
                + c.getApellidoPaterno() + " " + c.getApellidoMaterno();
    }

    private String generarNumeroCuentaUnico() {
        // SecureRandom: java.util.Random es predecible y permitiría adivinar números de cuenta.
        // 300000000000 + [0, 699999999999] mantiene siempre 12 dígitos; el UNIQUE de BD garantiza la unicidad final.
        String numeroCuenta;
        do {
            long number = 300_000_000_000L + (long) (RANDOM.nextDouble() * 699_999_999_999L);
            numeroCuenta = String.valueOf(number);
        } while (cuentaRepository.existsByNumeroCuenta(numeroCuenta));
        return numeroCuenta;
    }

    /**
     * Catálogo estricto: la nacionalidad debe coincidir EXACTAMENTE (mayúsculas incluidas) con el nombre
     * de un registro activo de cat_nacionalidad. No se aceptan claves ("MEX") ni variantes ("mexicana").
     */
    private void validarNacionalidadEnBD(String nacionalidad) {
        if (!catNacionalidadRepository.existsByNombreAndActivoTrue(nacionalidad)) {
            throw new NacionalidadNoValidaException("La nacionalidad '" + nacionalidad + "' no es válida. Debe ser exactamente uno de los nombres activos del catálogo cat_nacionalidad (consulte GET /api/v1/cat/nacionalidades).");
        }
    }

    /** Recorta espacios y convierte cadenas vacías en null (ej. segundo nombre o número interior ""). */
    /** CURP y RFC son inmutables: si se envían deben coincidir con los registrados; el resto se actualiza si viene. */
    private void actualizarDatosPersonales(ClienteEntity cliente, DatosPersonalesDto dp) {
        if (dp == null) {
            return;
        }
        if (dp.getCurp() != null && !dp.getCurp().trim().equalsIgnoreCase(cliente.getCurp())) {
            throw new ReglaNegocioException("La CURP no puede modificarse. Envíe la CURP registrada o omita el campo.");
        }
        if (dp.getRfc() != null && !dp.getRfc().trim().equalsIgnoreCase(cliente.getRfc())) {
            throw new ReglaNegocioException("El RFC no puede modificarse. Envíe el RFC registrado o omita el campo.");
        }
        if (dp.getNacionalidad() != null) {
            validarNacionalidadEnBD(dp.getNacionalidad());
        }
        // La CURP no cambia: la fecha de nacimiento y el sexo nuevos deben seguir coincidiendo con ella
        String fecha = com.proyecto.servicios.validation.IdentidadCurp.fechaCongruente(cliente.getCurp(), dp.getFechaNacimiento());
        String sexo = com.proyecto.servicios.validation.IdentidadCurp.sexoCongruente(cliente.getCurp(), dp.getSexo());
        if (fecha != null || sexo != null) {
            throw new ReglaNegocioException(fecha != null ? fecha : sexo);
        }
        textoSiPresente(dp.getNombre(), cliente::setNombre);
        textoSiPresente(dp.getSegundoNombre(), cliente::setSegundoNombre);
        textoSiPresente(dp.getApellidoPaterno(), cliente::setApellidoPaterno);
        textoSiPresente(dp.getApellidoMaterno(), cliente::setApellidoMaterno);
        siPresente(dp.getFechaNacimiento(), cliente::setFechaNacimiento);
        siPresente(dp.getSexo(), cliente::setSexo);
        siPresente(dp.getNacionalidad(), cliente::setNacionalidad);
        siPresente(dp.getEstadoCivil(), cliente::setEstadoCivil);
        log.info("Actualizando cliente ID {}: CURP y RFC se mantienen inmutables.", cliente.getId());
    }

    private void actualizarContacto(ClienteEntity cliente, DatosContactoDto dc) {
        if (dc == null) {
            return;
        }
        if (dc.getCorreo() != null) {
            String correo = dc.getCorreo().trim().toLowerCase(Locale.ROOT);
            if (!correo.equalsIgnoreCase(cliente.getCorreo())) {
                if (clienteRepository.existsByCorreo(correo)) {
                    throw new RegistroDuplicadoException("El nuevo correo ya está registrado por otro cliente.");
                }
                cliente.setCorreo(correo);
            }
        }
        siPresente(dc.getTelefonoMovil(), telefono -> cliente.setTelefonoMovil(String.valueOf(telefono)));
        siPresente(dc.getTelefonoAlt(), telefono -> cliente.setTelefonoAlt(String.valueOf(telefono)));
    }

    private static void actualizarDomicilio(ClienteEntity cliente, DomicilioDto dom) {
        if (dom == null) {
            return;
        }
        DomicilioEntity d = cliente.getDomicilio();
        if (d == null) {
            d = new DomicilioEntity();
            cliente.setDomicilio(d);
        }
        textoSiPresente(dom.getCalle(), d::setCalle);
        textoSiPresente(dom.getNumeroExterior(), d::setNumeroExterior);
        textoSiPresente(dom.getNumeroInterior(), d::setNumeroInterior);
        textoSiPresente(dom.getColonia(), d::setColonia);
        textoSiPresente(dom.getMunicipio(), d::setMunicipio);
        textoSiPresente(dom.getEstado(), d::setEstado);
        siPresente(dom.getCodigoPostal(), d::setCodigoPostal);
        textoSiPresente(dom.getPais(), d::setPais);
    }

    private static void actualizarInformacionLaboral(ClienteEntity cliente, InformacionLaboralDto info) {
        if (info == null) {
            return;
        }
        textoSiPresente(info.getOcupacion(), cliente::setOcupacion);
        textoSiPresente(info.getEmpresa(), cliente::setEmpresa);
        siPresente(info.getIngresoMensual(), cliente::setIngresoMensual);
    }

    /** Se puede cambiar la contraseña, el Face ID y el PIN; el nombre de usuario no cambia. */
    private void actualizarCredenciales(ClienteEntity cliente, LoginCredencialesDto credenciales,
                                        String passwordHash, String pinHash, String faceIdHash) {
        if (credenciales == null) {
            return;
        }
        UsuarioLoginEntity usuario = usuarioLoginRepository.findByClienteId(cliente.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El cliente no tiene un usuario de acceso registrado."));
        if (credenciales.getUsername() != null && !credenciales.getUsername().equals(usuario.getUsername())) {
            throw new ReglaNegocioException("El nombre de usuario no puede modificarse. Envíe el usuario registrado u omita 'loginCredenciales'.");
        }
        if (passwordHash != null) {
            usuario.setPasswordHash(passwordHash);
            usuario.cerrarSesion(); // al cambiar la contraseña se cierra la sesión activa
        }
        siPresente(faceIdHash, usuario::setFaceIdHash);
        usuarioLoginRepository.save(usuario);
        if (pinHash != null) {
            // Un PIN nuevo reinicia el contador de intentos y levanta el bloqueo del anterior
            cliente.setPin(pinHash);
            cliente.setPinIntentosFallidos((short) 0);
            cliente.setPinBloqueadoHasta(null);
        }
    }

    private static <T> void siPresente(T valor, Consumer<T> asignar) {
        if (valor != null) {
            asignar.accept(valor);
        }
    }

    private static void textoSiPresente(String valor, Consumer<String> asignar) {
        if (valor != null) {
            asignar.accept(limpiar(valor));
        }
    }

    private static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

}

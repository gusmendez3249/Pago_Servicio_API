package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.config.onboarding.PasswordEncoderUtil;
import com.proyecto.servicios.dto.onboarding.*;
import com.proyecto.servicios.entity.onboarding.*;
import com.proyecto.servicios.exception.OnboardingException;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Tope de clientes por consulta (bandera 4): protege la memoria cuando no se envían filtros. */
    private static final int MAX_RESULTADOS = 1000;

    /**
     * Cada operación corre en su propia transacción (TransactionTemplate) en lugar de @Transactional
     * sobre todo el método: el hash PBKDF2 de la contraseña es costoso a propósito y debe calcularse
     * ANTES de abrir la transacción, para no retener una conexión del pool mientras tanto
     * (bajo carga con JMeter eso agotaba el pool y producía respuestas 503).
     */
    public LayawayClienteResponse procesarOperacion(LayawayClienteRequest request) {
        Integer bandera = request.getBandera();
        if (bandera == null) {
            throw new OnboardingException("La bandera de operación es obligatoria (1 = Insertar, 2 = Actualizar, 3 = Eliminar/Baja Lógica, 4 = Consultar).", HttpStatus.BAD_REQUEST, 400);
        }

        return switch (bandera) {
            case 1 -> {
                LoginCredencialesDto login = request.getLoginCredenciales();
                String passwordHash = login != null ? passwordEncoderUtil.encode(login.getPassword()) : null;
                yield transactionTemplate.execute(status -> registrarCliente(request, passwordHash));
            }
            case 2 -> transactionTemplate.execute(status -> actualizarCliente(request));
            case 3 -> transactionTemplate.execute(status -> darDeBajaCliente(request));
            case 4 -> transactionTemplate.execute(status -> consultarClientes(request));
            default -> throw new OnboardingException("Bandera de operación no válida: " + bandera + ". Use 1 (Insertar), 2 (Actualizar), 3 (Eliminar) o 4 (Consultar).", HttpStatus.BAD_REQUEST, 400);
        };
    }

    private LayawayClienteResponse registrarCliente(LayawayClienteRequest request, String passwordHash) {
        DatosPersonalesDto dp = request.getDatosPersonales();
        DatosContactoDto dc = request.getDatosContacto();
        DomicilioDto dom = request.getDomicilio();
        InformacionLaboralDto infoLab = request.getInformacionLaboral();
        LoginCredencialesDto loginDto = request.getLoginCredenciales();

        // Validación estricta Postura B: Para bandera = 1 (Insertar), clienteId debe ser null o 0
        if (request.getClienteId() != null && request.getClienteId() != 0) {
            throw new OnboardingException("Para la operación INSERTAR (bandera = 1), el clienteId debe ser nulo o 0. No se permite enviar un ID preexistente o arbitrario.", HttpStatus.BAD_REQUEST, 400);
        }

        if (dp == null || dc == null || dom == null || infoLab == null) {
            throw new OnboardingException("Los datos personales, de contacto, domicilio e información laboral son obligatorios para la creación.", HttpStatus.BAD_REQUEST, 400);
        }

        // 1. Validar mayoría de edad
        if (dp.getFechaNacimiento() == null || Period.between(dp.getFechaNacimiento(), LocalDate.now()).getYears() < 18) {
            throw new OnboardingException("El cliente debe ser mayor de edad (18 años o más).", HttpStatus.BAD_REQUEST, 400);
        }

        // 1.1 Validar catálogo de nacionalidad en la base de datos (coincidencia exacta)
        validarNacionalidadEnBD(dp.getNacionalidad());

        // 2. Validar Unicidad de CURP, RFC, Correo
        if (clienteRepository.existsByCurp(dp.getCurp().trim().toUpperCase())) {
            throw new OnboardingException("Ya existe un cliente registrado con la CURP: " + dp.getCurp(), HttpStatus.CONFLICT, 409);
        }
        if (clienteRepository.existsByRfc(dp.getRfc().trim().toUpperCase())) {
            throw new OnboardingException("Ya existe un cliente registrado con el RFC: " + dp.getRfc(), HttpStatus.CONFLICT, 409);
        }
        if (clienteRepository.existsByCorreo(dc.getCorreo().trim().toLowerCase())) {
            throw new OnboardingException("Ya existe un cliente registrado con el correo: " + dc.getCorreo(), HttpStatus.CONFLICT, 409);
        }

        String username = (loginDto != null && loginDto.getUsername() != null && !loginDto.getUsername().trim().isEmpty())
                ? loginDto.getUsername().trim()
                : dp.getCurp().trim().toLowerCase();

        if (usuarioLoginRepository.existsByUsername(username)) {
            throw new OnboardingException("El nombre de usuario '" + username + "' ya se encuentra en uso.", HttpStatus.CONFLICT, 409);
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
                .datosBiometricos(dp.getDatosBiometricos() != null ? dp.getDatosBiometricos() : (loginDto != null ? loginDto.getFaceIdBiometrico() : null))
                .activo(true)
                .fechaRegistro(LocalDateTime.now())
                .domicilio(domicilioEntity)
                .build();
        clienteRepository.save(clienteEntity);

        // 5. Creación Automática de Cuenta Bancaria
        String numeroCuenta = generarNumeroCuentaUnico();
        BigDecimal saldoInicial = new BigDecimal("1000.00");

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
        Long faceId = (loginDto != null && loginDto.getFaceIdBiometrico() != null) ? loginDto.getFaceIdBiometrico() : dp.getDatosBiometricos();

        UsuarioLoginEntity usuarioLogin = UsuarioLoginEntity.builder()
                .username(username)
                .passwordHash(passwordHash)
                .faceIdBiometrico(faceId)
                .isLoggedIn(false)
                .cliente(clienteEntity)
                .build();
        usuarioLoginRepository.save(usuarioLogin);

        log.info("Cliente registrado exitosamente. ID: {}, Cuenta: {}", clienteEntity.getId(), numeroCuenta);

        String nombreCompleto = clienteEntity.getNombre() + " " +
                (clienteEntity.getSegundoNombre() != null ? clienteEntity.getSegundoNombre() + " " : "") +
                clienteEntity.getApellidoPaterno() + " " + clienteEntity.getApellidoMaterno();

        return LayawayClienteResponse.builder()
                .clienteId(clienteEntity.getId())
                .nombreCompleto(nombreCompleto)
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

    private LayawayClienteResponse actualizarCliente(LayawayClienteRequest request) {
        if (request.getClienteId() == null || request.getClienteId() <= 0) {
            throw new OnboardingException("El clienteId debe ser un identificador numérico válido mayor a 0 para la operación ACTUALIZAR (bandera = 2).", HttpStatus.BAD_REQUEST, 400);
        }

        ClienteEntity cliente = clienteRepository.findById(request.getClienteId())
                .orElseThrow(() -> new OnboardingException("Cliente no encontrado con ID: " + request.getClienteId(), HttpStatus.NOT_FOUND, 404));

        if (Boolean.FALSE.equals(cliente.getActivo())) {
            throw new OnboardingException("No se puede actualizar la información de un cliente inactivo.", HttpStatus.FORBIDDEN, 403);
        }

        // Actualizar datos personales (PRESERVANDO INMUTABILIDAD DE CURP Y RFC)
        if (request.getDatosPersonales() != null) {
            DatosPersonalesDto dp = request.getDatosPersonales();
            // CURP y RFC no son modificables: si se envían deben coincidir con los registrados
            if (dp.getCurp() != null && !dp.getCurp().trim().equalsIgnoreCase(cliente.getCurp())) {
                throw new OnboardingException("La CURP no puede modificarse. Envíe la CURP registrada o omita el campo.", HttpStatus.BAD_REQUEST, 400);
            }
            if (dp.getRfc() != null && !dp.getRfc().trim().equalsIgnoreCase(cliente.getRfc())) {
                throw new OnboardingException("El RFC no puede modificarse. Envíe el RFC registrado o omita el campo.", HttpStatus.BAD_REQUEST, 400);
            }
            if (dp.getNombre() != null) cliente.setNombre(limpiar(dp.getNombre()));
            if (dp.getSegundoNombre() != null) cliente.setSegundoNombre(limpiar(dp.getSegundoNombre()));
            if (dp.getApellidoPaterno() != null) cliente.setApellidoPaterno(limpiar(dp.getApellidoPaterno()));
            if (dp.getApellidoMaterno() != null) cliente.setApellidoMaterno(limpiar(dp.getApellidoMaterno()));
            if (dp.getFechaNacimiento() != null) {
                if (Period.between(dp.getFechaNacimiento(), LocalDate.now()).getYears() < 18) {
                    throw new OnboardingException("El cliente debe ser mayor de edad (18 años o más).", HttpStatus.BAD_REQUEST, 400);
                }
                cliente.setFechaNacimiento(dp.getFechaNacimiento());
            }
            if (dp.getSexo() != null) cliente.setSexo(dp.getSexo());
            if (dp.getNacionalidad() != null) {
                validarNacionalidadEnBD(dp.getNacionalidad());
                cliente.setNacionalidad(dp.getNacionalidad());
            }
            if (dp.getEstadoCivil() != null) cliente.setEstadoCivil(dp.getEstadoCivil());
            if (dp.getDatosBiometricos() != null) cliente.setDatosBiometricos(dp.getDatosBiometricos());

            // NOTA: Intencionalmente NO se actualizan CURP ni RFC para proteger la inmutabilidad de negocio.
            log.info("Actualizando cliente ID {}: CURP ({}) y RFC ({}) se mantienen inmutables.", cliente.getId(), cliente.getCurp(), cliente.getRfc());
        }

        // Actualizar datos de contacto
        if (request.getDatosContacto() != null) {
            DatosContactoDto dc = request.getDatosContacto();
            if (dc.getCorreo() != null && !dc.getCorreo().trim().equalsIgnoreCase(cliente.getCorreo())) {
                if (clienteRepository.existsByCorreo(dc.getCorreo().trim().toLowerCase())) {
                    throw new OnboardingException("El nuevo correo ya está registrado por otro cliente.", HttpStatus.CONFLICT, 409);
                }
                cliente.setCorreo(dc.getCorreo().trim().toLowerCase());
            }
            if (dc.getTelefonoMovil() != null) cliente.setTelefonoMovil(String.valueOf(dc.getTelefonoMovil()));
            if (dc.getTelefonoAlt() != null) cliente.setTelefonoAlt(String.valueOf(dc.getTelefonoAlt()));
        }

        // Actualizar domicilio
        if (request.getDomicilio() != null) {
            DomicilioDto dom = request.getDomicilio();
            DomicilioEntity d = cliente.getDomicilio();
            if (d == null) {
                d = new DomicilioEntity();
                cliente.setDomicilio(d);
            }
            if (dom.getCalle() != null) d.setCalle(limpiar(dom.getCalle()));
            if (dom.getNumeroExterior() != null) d.setNumeroExterior(limpiar(dom.getNumeroExterior()));
            if (dom.getNumeroInterior() != null) d.setNumeroInterior(limpiar(dom.getNumeroInterior()));
            if (dom.getColonia() != null) d.setColonia(limpiar(dom.getColonia()));
            if (dom.getMunicipio() != null) d.setMunicipio(limpiar(dom.getMunicipio()));
            if (dom.getEstado() != null) d.setEstado(limpiar(dom.getEstado()));
            if (dom.getCodigoPostal() != null) d.setCodigoPostal(dom.getCodigoPostal());
            if (dom.getPais() != null) d.setPais(limpiar(dom.getPais()));
        }

        // Actualizar información laboral
        if (request.getInformacionLaboral() != null) {
            InformacionLaboralDto info = request.getInformacionLaboral();
            if (info.getOcupacion() != null) cliente.setOcupacion(limpiar(info.getOcupacion()));
            if (info.getEmpresa() != null) cliente.setEmpresa(limpiar(info.getEmpresa()));
            if (info.getIngresoMensual() != null) cliente.setIngresoMensual(info.getIngresoMensual());
        }

        clienteRepository.save(cliente);

        List<CuentaEntity> cuentas = cuentaRepository.findByClienteId(cliente.getId());
        String numCuenta = cuentas.isEmpty() ? "N/A" : cuentas.get(0).getNumeroCuenta();
        BigDecimal saldo = cuentas.isEmpty() ? BigDecimal.ZERO : cuentas.get(0).getSaldo();
        String estatusCta = cuentas.isEmpty() ? "N/A" : cuentas.get(0).getEstatus();

        String nombreCompleto = cliente.getNombre() + " " +
                (cliente.getSegundoNombre() != null ? cliente.getSegundoNombre() + " " : "") +
                cliente.getApellidoPaterno() + " " + cliente.getApellidoMaterno();

        return LayawayClienteResponse.builder()
                .clienteId(cliente.getId())
                .nombreCompleto(nombreCompleto)
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
        if (request.getClienteId() == null || request.getClienteId() <= 0) {
            throw new OnboardingException("El clienteId debe ser un identificador numérico válido mayor a 0 para la operación ELIMINAR / BAJA LÓGICA (bandera = 3).", HttpStatus.BAD_REQUEST, 400);
        }

        ClienteEntity cliente = clienteRepository.findById(request.getClienteId())
                .orElseThrow(() -> new OnboardingException("Cliente no encontrado con ID: " + request.getClienteId(), HttpStatus.NOT_FOUND, 404));

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
            u.setIsLoggedIn(false);
            usuarioLoginRepository.save(u);
        });

        log.info("Baja lógica realizada exitosamente para el cliente ID: {}", cliente.getId());

        String numCuenta = cuentas.isEmpty() ? "N/A" : cuentas.get(0).getNumeroCuenta();
        BigDecimal saldo = cuentas.isEmpty() ? BigDecimal.ZERO : cuentas.get(0).getSaldo();

        String nombreCompleto = cliente.getNombre() + " " +
                (cliente.getSegundoNombre() != null ? cliente.getSegundoNombre() + " " : "") +
                cliente.getApellidoPaterno() + " " + cliente.getApellidoMaterno();

        return LayawayClienteResponse.builder()
                .clienteId(cliente.getId())
                .nombreCompleto(nombreCompleto)
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
     * Bandera 4: consulta de clientes por campos llave inmutables (clienteId, curp, rfc, numeroCuenta).
     * Los filtros de texto son "contiene" sin distinguir mayúsculas; varios filtros se combinan con AND.
     * Sin filtros devuelve todos los clientes (hasta MAX_RESULTADOS; se indica si hubo más).
     */
    private LayawayClienteResponse consultarClientes(LayawayClienteRequest request) {
        // Para la consulta solo se usa "filtros": cualquier otra sección enviada se rechaza en lugar de
        // ignorarla, porque ignorarla devolvería todos los clientes como si no hubiera filtro.
        if (request.getClienteId() != null || request.getDatosPersonales() != null || request.getDatosContacto() != null
                || request.getDomicilio() != null || request.getInformacionLaboral() != null
                || request.getLoginCredenciales() != null) {
            throw new OnboardingException("Para la consulta (bandera = 4) envíe únicamente el objeto 'filtros' (clienteId, curp, rfc, numeroCuenta) o ninguno para obtener todos los clientes.", HttpStatus.BAD_REQUEST, 400);
        }

        FiltrosClienteDto filtros = request.getFiltros() != null ? request.getFiltros() : new FiltrosClienteDto();
        Specification<ClienteEntity> spec = especificacionConsulta(filtros);

        Page<ClienteEntity> pagina = clienteRepository.findAll(spec, PageRequest.of(0, MAX_RESULTADOS, Sort.by("id")));

        List<Long> ids = pagina.getContent().stream().map(ClienteEntity::getId).toList();
        Map<Long, List<CuentaEntity>> cuentasPorCliente = ids.isEmpty() ? Map.of()
                : cuentaRepository.findByClienteIdIn(ids).stream()
                        .collect(Collectors.groupingBy(c -> c.getCliente().getId()));

        List<ClienteConsultaDto> clientes = pagina.getContent().stream().map(c -> ClienteConsultaDto.builder()
                .clienteId(c.getId())
                .nombreCompleto(nombreCompleto(c))
                .curp(c.getCurp())
                .rfc(c.getRfc())
                .correo(c.getCorreo())
                .telefonoMovil(c.getTelefonoMovil())
                .activo(c.getActivo())
                .fechaRegistro(c.getFechaRegistro())
                .cuentas(cuentasPorCliente.getOrDefault(c.getId(), List.of()).stream()
                        .map(k -> ClienteConsultaDto.CuentaConsultaDto.builder()
                                .numeroCuenta(k.getNumeroCuenta()).saldo(k.getSaldo()).estatus(k.getEstatus()).build())
                        .toList())
                .build()).toList();

        return LayawayClienteResponse.builder()
                .operacionRealizada("CONSULTAR")
                .fechaOperacion(LocalDateTime.now())
                .clientes(clientes)
                .totalCoincidencias(pagina.getTotalElements())
                .resultadosTruncados(pagina.getTotalElements() > clientes.size())
                .build();
    }

    private Specification<ClienteEntity> especificacionConsulta(FiltrosClienteDto f) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (f.getClienteId() != null) {
                condiciones.add(cb.equal(root.get("id"), f.getClienteId()));
            }
            if (f.getCurp() != null) {
                condiciones.add(cb.like(cb.upper(root.get("curp")), "%" + f.getCurp().toUpperCase(Locale.ROOT) + "%"));
            }
            if (f.getRfc() != null) {
                condiciones.add(cb.like(cb.upper(root.get("rfc")), "%" + f.getRfc().toUpperCase(Locale.ROOT) + "%"));
            }
            if (f.getNumeroCuenta() != null) {
                Subquery<Long> cuentas = query.subquery(Long.class);
                Root<CuentaEntity> cuenta = cuentas.from(CuentaEntity.class);
                cuentas.select(cuenta.get("id")).where(
                        cb.equal(cuenta.get("cliente"), root),
                        cb.like(cuenta.get("numeroCuenta"), "%" + f.getNumeroCuenta() + "%"));
                condiciones.add(cb.exists(cuentas));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
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
        if (nacionalidad == null || nacionalidad.isEmpty()) {
            throw new OnboardingException("La nacionalidad es obligatoria.", HttpStatus.BAD_REQUEST, 400);
        }
        if (!catNacionalidadRepository.existsByNombreAndActivoTrue(nacionalidad)) {
            throw new OnboardingException("La nacionalidad '" + nacionalidad + "' no es válida. Debe ser exactamente uno de los nombres activos del catálogo cat_nacionalidad (consulte GET /api/v1/cat/nacionalidades).", HttpStatus.NOT_FOUND, 404);
        }
    }

    /** Recorta espacios y convierte cadenas vacías en null (ej. segundo nombre o número interior ""). */
    private static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

}

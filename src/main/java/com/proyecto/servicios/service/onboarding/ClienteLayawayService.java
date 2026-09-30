package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.config.onboarding.PasswordEncoderUtil;
import com.proyecto.servicios.dto.onboarding.*;
import com.proyecto.servicios.entity.onboarding.*;
import com.proyecto.servicios.exception.OnboardingException;
import com.proyecto.servicios.repositorys.onboarding.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClienteLayawayService {

    private final ClienteRepository clienteRepository;
    private final DomicilioRepository domicilioRepository;
    private final CuentaRepository cuentaRepository;
    private final UsuarioLoginRepository usuarioLoginRepository;
    private final PasswordEncoderUtil passwordEncoderUtil;

    @Transactional
    public LayawayClienteResponse procesarOperacion(LayawayClienteRequest request) {
        Integer bandera = request.getBandera();
        if (bandera == null) {
            throw new OnboardingException("La bandera de operación es obligatoria (1 = Insertar, 2 = Actualizar, 3 = Eliminar/Baja Lógica).", HttpStatus.BAD_REQUEST, 400);
        }

        return switch (bandera) {
            case 1 -> registrarCliente(request);
            case 2 -> actualizarCliente(request);
            case 3 -> darDeBajaCliente(request);
            default -> throw new OnboardingException("Bandera de operación no válida: " + bandera + ". Use 1 (Insertar), 2 (Actualizar) o 3 (Eliminar).", HttpStatus.BAD_REQUEST, 400);
        };
    }

    private LayawayClienteResponse registrarCliente(LayawayClienteRequest request) {
        DatosPersonalesDto dp = request.getDatosPersonales();
        DatosContactoDto dc = request.getDatosContacto();
        DomicilioDto dom = request.getDomicilio();
        InformacionLaboralDto infoLab = request.getInformacionLaboral();
        LoginCredencialesDto loginDto = request.getLoginCredenciales();

        if (dp == null || dc == null || dom == null || infoLab == null) {
            throw new OnboardingException("Los datos personales, de contacto, domicilio e información laboral son obligatorios para la creación.", HttpStatus.BAD_REQUEST, 400);
        }

        // 1. Validar mayoría de edad
        if (dp.getFechaNacimiento() == null || Period.between(dp.getFechaNacimiento(), LocalDate.now()).getYears() < 18) {
            throw new OnboardingException("El cliente debe ser mayor de edad (18 años o más).", HttpStatus.BAD_REQUEST, 400);
        }

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
                .calle(dom.getCalle())
                .numeroExterior(dom.getNumeroExterior())
                .numeroInterior(dom.getNumeroInterior())
                .colonia(dom.getColonia())
                .municipio(dom.getMunicipio())
                .estado(dom.getEstado())
                .codigoPostal(dom.getCodigoPostal())
                .pais(dom.getPais() != null ? dom.getPais() : "México")
                .build();
        domicilioRepository.save(domicilioEntity);

        // 4. Crear Cliente
        ClienteEntity clienteEntity = ClienteEntity.builder()
                .nombre(dp.getNombre())
                .segundoNombre(dp.getSegundoNombre())
                .apellidoPaterno(dp.getApellidoPaterno())
                .apellidoMaterno(dp.getApellidoMaterno())
                .fechaNacimiento(dp.getFechaNacimiento())
                .curp(dp.getCurp().trim().toUpperCase())
                .rfc(dp.getRfc().trim().toUpperCase())
                .sexo(dp.getSexo())
                .nacionalidad(dp.getNacionalidad())
                .estadoCivil(dp.getEstadoCivil())
                .correo(dc.getCorreo().trim().toLowerCase())
                .telefonoMovil(dc.getTelefonoMovil())
                .telefonoAlt(dc.getTelefonoAlt())
                .ocupacion(infoLab.getOcupacion())
                .empresa(infoLab.getEmpresa())
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

        // 6. Crear Registro de Login Cifrado
        String rawPassword = (loginDto != null && loginDto.getPassword() != null) ? loginDto.getPassword() : "DefaultPassword123!";
        Long faceId = (loginDto != null && loginDto.getFaceIdBiometrico() != null) ? loginDto.getFaceIdBiometrico() : dp.getDatosBiometricos();

        UsuarioLoginEntity usuarioLogin = UsuarioLoginEntity.builder()
                .username(username)
                .passwordHash(passwordEncoderUtil.encode(rawPassword))
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
        if (request.getClienteId() == null) {
            throw new OnboardingException("El clienteId es obligatorio para la operación ACTUALIZAR (bandera = 2).", HttpStatus.BAD_REQUEST, 400);
        }

        ClienteEntity cliente = clienteRepository.findById(request.getClienteId())
                .orElseThrow(() -> new OnboardingException("Cliente no encontrado con ID: " + request.getClienteId(), HttpStatus.NOT_FOUND, 404));

        if (Boolean.FALSE.equals(cliente.getActivo())) {
            throw new OnboardingException("No se puede actualizar la información de un cliente inactivo.", HttpStatus.FORBIDDEN, 403);
        }

        // Actualizar datos personales (PRESERVANDO INMUTABILIDAD DE CURP Y RFC)
        if (request.getDatosPersonales() != null) {
            DatosPersonalesDto dp = request.getDatosPersonales();
            if (dp.getNombre() != null) cliente.setNombre(dp.getNombre());
            if (dp.getSegundoNombre() != null) cliente.setSegundoNombre(dp.getSegundoNombre());
            if (dp.getApellidoPaterno() != null) cliente.setApellidoPaterno(dp.getApellidoPaterno());
            if (dp.getApellidoMaterno() != null) cliente.setApellidoMaterno(dp.getApellidoMaterno());
            if (dp.getFechaNacimiento() != null) {
                if (Period.between(dp.getFechaNacimiento(), LocalDate.now()).getYears() < 18) {
                    throw new OnboardingException("El cliente debe ser mayor de edad (18 años o más).", HttpStatus.BAD_REQUEST, 400);
                }
                cliente.setFechaNacimiento(dp.getFechaNacimiento());
            }
            if (dp.getSexo() != null) cliente.setSexo(dp.getSexo());
            if (dp.getNacionalidad() != null) cliente.setNacionalidad(dp.getNacionalidad());
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
            if (dc.getTelefonoMovil() != null) cliente.setTelefonoMovil(dc.getTelefonoMovil());
            if (dc.getTelefonoAlt() != null) cliente.setTelefonoAlt(dc.getTelefonoAlt());
        }

        // Actualizar domicilio
        if (request.getDomicilio() != null) {
            DomicilioDto dom = request.getDomicilio();
            DomicilioEntity d = cliente.getDomicilio();
            if (d == null) {
                d = new DomicilioEntity();
                cliente.setDomicilio(d);
            }
            if (dom.getCalle() != null) d.setCalle(dom.getCalle());
            if (dom.getNumeroExterior() != null) d.setNumeroExterior(dom.getNumeroExterior());
            if (dom.getNumeroInterior() != null) d.setNumeroInterior(dom.getNumeroInterior());
            if (dom.getColonia() != null) d.setColonia(dom.getColonia());
            if (dom.getMunicipio() != null) d.setMunicipio(dom.getMunicipio());
            if (dom.getEstado() != null) d.setEstado(dom.getEstado());
            if (dom.getCodigoPostal() != null) d.setCodigoPostal(dom.getCodigoPostal());
            if (dom.getPais() != null) d.setPais(dom.getPais());
        }

        // Actualizar información laboral
        if (request.getInformacionLaboral() != null) {
            InformacionLaboralDto info = request.getInformacionLaboral();
            if (info.getOcupacion() != null) cliente.setOcupacion(info.getOcupacion());
            if (info.getEmpresa() != null) cliente.setEmpresa(info.getEmpresa());
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
        if (request.getClienteId() == null) {
            throw new OnboardingException("El clienteId es obligatorio para la operación ELIMINAR / BAJA LÓGICA (bandera = 3).", HttpStatus.BAD_REQUEST, 400);
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

    private String generarNumeroCuentaUnico() {
        Random random = new Random();
        String numeroCuenta;
        do {
            long number = 300000000000L + (long)(random.nextDouble() * 899999999999L);
            numeroCuenta = String.valueOf(number);
        } while (cuentaRepository.existsByNumeroCuenta(numeroCuenta));
        return numeroCuenta;
    }
}

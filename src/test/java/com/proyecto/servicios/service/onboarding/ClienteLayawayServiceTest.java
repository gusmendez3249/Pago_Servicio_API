package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.config.onboarding.PasswordEncoderUtil;
import com.proyecto.servicios.dto.onboarding.*;
import com.proyecto.servicios.entity.onboarding.*;
import com.proyecto.servicios.exception.OnboardingException;
import com.proyecto.servicios.repositorys.onboarding.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.Spy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteLayawayServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private DomicilioRepository domicilioRepository;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private UsuarioLoginRepository usuarioLoginRepository;

    @Mock
    private PasswordEncoderUtil passwordEncoderUtil;

    @Mock
    private CatNacionalidadRepository catNacionalidadRepository;

    // TransactionTemplate real sobre un gestor de transacciones simulado: ejecuta el callback tal cual
    @Spy
    private TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));

    @Mock
    private SesionService sesionService;

    // Validador real: el cuerpo se valida en el servicio, después de autenticar
    @Spy
    private Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @InjectMocks
    private ClienteLayawayService clienteLayawayService;

    private static final SesionUsuario EJECUTIVO = new SesionUsuario(1L, "admin", "EJECUTIVO", null);
    private static final SesionUsuario CLIENTE = new SesionUsuario(2L, "juan", "CLIENTE", 10L);
    private static final String TOKEN = "Bearer token-de-prueba";

    /** Por omisión las pruebas actúan como ejecutivo autenticado (sin restricción de recurso). */
    private LayawayClienteResponse procesar(LayawayClienteRequest request) {
        return clienteLayawayService.procesarOperacion(request, TOKEN);
    }

    private LayawayClienteRequest requestInsertar;

    @BeforeEach
    void setUp() {
        lenient().when(sesionService.autenticar(any())).thenReturn(EJECUTIVO);
        DatosPersonalesDto dp = DatosPersonalesDto.builder()
                .nombre("Juan")
                .apellidoPaterno("Pérez")
                .apellidoMaterno("Gómez")
                .fechaNacimiento(LocalDate.of(1995, 5, 15))
                .curp("PERJ950515HDFRMN01")
                .rfc("PERJ9505151A2")
                .sexo("MASCULINO")
                .nacionalidad("MEXICANA")
                .estadoCivil("SOLTERO")
                .build();

        DatosContactoDto dc = DatosContactoDto.builder()
                .correo("juan.perez@email.com")
                .telefonoMovil(5512345678L)
                .build();

        DomicilioDto dom = DomicilioDto.builder()
                .calle("Av. Reforma")
                .numeroExterior("100")
                .colonia("Centro")
                .municipio("Cuauhtémoc")
                .estado("CDMX")
                .codigoPostal("06000")
                .pais("México")
                .build();

        InformacionLaboralDto infoLab = InformacionLaboralDto.builder()
                .ocupacion("Desarrollador")
                .empresa("Empresa X")
                .ingresoMensual(new BigDecimal("30000.00"))
                .build();

        LoginCredencialesDto login = LoginCredencialesDto.builder()
                .username("juan_perez")
                .password("Password123!")
                .faceIdBiometrico(987654321L)
                .build();

        requestInsertar = LayawayClienteRequest.builder()
                .bandera(1)
                .datosPersonales(dp)
                .datosContacto(dc)
                .domicilio(dom)
                .informacionLaboral(infoLab)
                .loginCredenciales(login)
                .build();
    }

    @Test
    void procesarOperacion_Bandera1_InsertarCorrectamente() {
        when(catNacionalidadRepository.existsByNombreAndActivoTrue("MEXICANA")).thenReturn(true);
        when(clienteRepository.existsByCurp(any())).thenReturn(false);
        when(clienteRepository.existsByRfc(any())).thenReturn(false);
        when(clienteRepository.existsByCorreo(any())).thenReturn(false);
        when(usuarioLoginRepository.existsByUsername(any())).thenReturn(false);
        when(passwordEncoderUtil.encode(any())).thenReturn("hash_pass");

        when(clienteRepository.save(any(ClienteEntity.class))).thenAnswer(invocation -> {
            ClienteEntity c = invocation.getArgument(0);
            c.setId(10L);
            return c;
        });

        LayawayClienteResponse response = procesar(requestInsertar);

        assertNotNull(response);
        assertEquals("INSERTAR", response.getOperacionRealizada());
        assertEquals("ACTIVA", response.getEstatusCuenta());
        assertEquals(new BigDecimal("1000.00"), response.getSaldoInicial());
        verify(cuentaRepository, times(1)).save(any(CuentaEntity.class));
    }

    @Test
    void procesarOperacion_Bandera3_BajaLogicaExitosamente() {
        ClienteEntity clienteExistente = ClienteEntity.builder()
                .id(5L)
                .nombre("Maria")
                .apellidoPaterno("López")
                .apellidoMaterno("Sánchez")
                .curp("LOPS900101MDFRMN02")
                .rfc("LOPS9001012B3")
                .correo("maria@test.com")
                .activo(true)
                .build();

        CuentaEntity cuenta = CuentaEntity.builder()
                .id(1L)
                .numeroCuenta("300123456789")
                .saldo(new BigDecimal("1500.00"))
                .estatus("ACTIVA")
                .cliente(clienteExistente)
                .build();

        LayawayClienteRequest requestBaja = LayawayClienteRequest.builder()
                .bandera(3)
                .filtros(FiltrosClienteDto.builder().curp("LOPS900101MDFRMN02").build())
                .build();

        when(clienteRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(clienteExistente), PageRequest.of(0, 2), 1));
        when(cuentaRepository.findByClienteId(5L)).thenReturn(Collections.singletonList(cuenta));

        LayawayClienteResponse response = procesar(requestBaja);

        assertNotNull(response);
        assertEquals("ELIMINAR_BAJA_LOGICA", response.getOperacionRealizada());
        assertFalse(response.getActivo());
        assertEquals("INACTIVA", response.getEstatusCuenta());
        verify(clienteRepository, times(1)).save(clienteExistente);
        // El ID interno ya no se expone en ninguna respuesta
        assertFalse(com.fasterxml.jackson.databind.json.JsonMapper.builder().findAndAddModules().build()
                .valueToTree(response).has("clienteId"));
    }

    @Test
    void procesarOperacion_Bandera1_ConClienteIdInvalido_LanzaBadRequest() {
        requestInsertar.setClienteId(10L); // Postura B: bandera 1 solo admite null o 0

        OnboardingException exception = assertThrows(OnboardingException.class, () ->
                procesar(requestInsertar)
        );

        assertEquals(400, exception.getCodigo());
        assertTrue(exception.getMessage().contains("clienteId debe ser nulo o 0"));
    }

    @Test
    void procesarOperacion_Bandera2y3_SinLlaves_LanzaBadRequestSinTocarNada() {
        for (int bandera : new int[]{2, 3}) {
            LayawayClienteRequest[] invalidas = {
                    LayawayClienteRequest.builder().bandera(bandera).build(),
                    LayawayClienteRequest.builder().bandera(bandera).filtros(new FiltrosClienteDto()).build(),
            };
            for (LayawayClienteRequest req : invalidas) {
                OnboardingException ex = assertThrows(OnboardingException.class, () -> procesar(req));
                assertEquals(400, ex.getCodigo());
                assertTrue(ex.getMessage().contains("'filtros'"), ex.getMessage());
            }
        }
        verify(clienteRepository, never()).findAll(any(Specification.class), any(Pageable.class));
        verify(clienteRepository, never()).save(any());
    }

    @Test
    void procesarOperacion_Bandera2y3_ConClienteId_SeRechazaPorqueYaNoSeUsa() {
        for (int bandera : new int[]{2, 3}) {
            LayawayClienteRequest req = LayawayClienteRequest.builder().bandera(bandera).clienteId(5L)
                    .filtros(FiltrosClienteDto.builder().curp("LOPS900101MDFRMN02").build()).build();
            OnboardingException ex = assertThrows(OnboardingException.class, () -> procesar(req));
            assertEquals(400, ex.getCodigo());
            assertTrue(ex.getMessage().contains("clienteId ya no se utiliza"));
        }
    }

    @Test
    void procesarOperacion_Bandera2y3_LlaveIncompleta_ExigeValorCompleto() {
        FiltrosClienteDto[] incompletos = {
                FiltrosClienteDto.builder().curp("LOPS90").build(),
                FiltrosClienteDto.builder().rfc("LOPS").build(),
                FiltrosClienteDto.builder().numeroCuenta("3001").build(),
        };
        for (int bandera : new int[]{2, 3}) {
            for (FiltrosClienteDto f : incompletos) {
                LayawayClienteRequest req = LayawayClienteRequest.builder().bandera(bandera).filtros(f).build();
                OnboardingException ex = assertThrows(OnboardingException.class, () -> procesar(req));
                assertEquals(400, ex.getCodigo());
                assertTrue(ex.getMessage().contains("COMPLETO"));
            }
        }
        verify(clienteRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void procesarOperacion_Bandera2y3_LlaveSinCoincidencia_Lanza404() {
        when(clienteRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 2), 0));
        for (int bandera : new int[]{2, 3}) {
            LayawayClienteRequest req = LayawayClienteRequest.builder().bandera(bandera)
                    .filtros(FiltrosClienteDto.builder().curp("ZZZZ900101MDFRMN02").build()).build();
            OnboardingException ex = assertThrows(OnboardingException.class, () -> procesar(req));
            assertEquals(404, ex.getCodigo());
        }
        verify(clienteRepository, never()).save(any());
    }

    @Test
    void procesarOperacion_Bandera1_ConFiltros_LanzaBadRequest() {
        requestInsertar.setFiltros(FiltrosClienteDto.builder().curp("PERJ950515HDFRMN01").build());
        OnboardingException ex = assertThrows(OnboardingException.class, () -> procesar(requestInsertar));
        assertEquals(400, ex.getCodigo());
        assertTrue(ex.getMessage().contains("filtros"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void procesarOperacion_Bandera2_CambiarContrasena_GuardaNuevoHashYCierraSesion() {
        ClienteEntity existente = ClienteEntity.builder().id(7L).curp("PERJ950515HDFRMN01").rfc("PERJ9505151A2")
                .nombre("Juan").apellidoPaterno("Perez").apellidoMaterno("Gomez").activo(true).build();
        UsuarioLoginEntity usuario = UsuarioLoginEntity.builder().id(3L).username("juan_perez")
                .passwordHash("hash_viejo").isLoggedIn(true).cliente(existente).build();
        when(clienteRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existente), PageRequest.of(0, 2), 1));
        when(usuarioLoginRepository.findByClienteId(7L)).thenReturn(Optional.of(usuario));
        when(cuentaRepository.findByClienteId(7L)).thenReturn(List.of());
        when(passwordEncoderUtil.encode("NuevaClave#2026")).thenReturn("hash_nuevo");

        procesar(LayawayClienteRequest.builder().bandera(2)
                .filtros(FiltrosClienteDto.builder().curp("PERJ950515HDFRMN01").build())
                .loginCredenciales(LoginCredencialesDto.builder().username("juan_perez").password("NuevaClave#2026").build())
                .build());

        assertEquals("hash_nuevo", usuario.getPasswordHash());
        assertFalse(usuario.getIsLoggedIn());
        verify(usuarioLoginRepository).save(usuario);
    }

    @Test
    @SuppressWarnings("unchecked")
    void procesarOperacion_Bandera2_CambiarUsuario_LanzaBadRequest() {
        ClienteEntity existente = ClienteEntity.builder().id(7L).curp("PERJ950515HDFRMN01").activo(true).build();
        UsuarioLoginEntity usuario = UsuarioLoginEntity.builder().id(3L).username("juan_perez").cliente(existente).build();
        when(clienteRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existente), PageRequest.of(0, 2), 1));
        when(usuarioLoginRepository.findByClienteId(7L)).thenReturn(Optional.of(usuario));

        OnboardingException ex = assertThrows(OnboardingException.class, () -> procesar(
                LayawayClienteRequest.builder().bandera(2)
                        .filtros(FiltrosClienteDto.builder().curp("PERJ950515HDFRMN01").build())
                        .loginCredenciales(LoginCredencialesDto.builder().username("otro_usuario").build()).build()));

        assertEquals(400, ex.getCodigo());
        assertTrue(ex.getMessage().contains("usuario no puede modificarse"));
    }

    @Test
    void procesarOperacion_Bandera1_SinPassword_NoAsignaPasswordPorDefecto() {
        requestInsertar.getLoginCredenciales().setPassword(null);
        when(catNacionalidadRepository.existsByNombreAndActivoTrue("MEXICANA")).thenReturn(true);
        when(clienteRepository.save(any(ClienteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        procesar(requestInsertar);

        verify(passwordEncoderUtil).encode(null);
        verify(passwordEncoderUtil, never()).encode("DefaultPassword123!");
    }

    @Test
    void procesarOperacion_Bandera1_NacionalidadConClaveEnVezDeNombre_Lanza404() {
        // Catálogo estricto: la clave "MEX" tiene formato válido pero no es el nombre "MEXICANA" -> no existe (404)
        requestInsertar.getDatosPersonales().setNacionalidad("MEX");
        when(catNacionalidadRepository.existsByNombreAndActivoTrue("MEX")).thenReturn(false);

        OnboardingException exception = assertThrows(OnboardingException.class, () ->
                procesar(requestInsertar)
        );

        assertEquals(404, exception.getCodigo());
        verify(clienteRepository, never()).save(any());
    }

    @Test
    void procesarOperacion_Bandera1_NacionalidadEnMinusculas_SeRechazaPorValidacion() {
        for (String variante : new String[]{"Mexicana", "mexicana"}) {
            requestInsertar.getDatosPersonales().setNacionalidad(variante);
            com.proyecto.servicios.exception.ValidacionException ex = assertThrows(
                    com.proyecto.servicios.exception.ValidacionException.class, () -> procesar(requestInsertar), variante);
            assertTrue(ex.getErrores().containsKey("datosPersonales.nacionalidad"), variante);
        }
        verify(clienteRepository, never()).save(any());
    }

    @Test
    void procesarOperacion_Bandera1_NumeroCuentaSiempreDe12Digitos() {
        when(catNacionalidadRepository.existsByNombreAndActivoTrue("MEXICANA")).thenReturn(true);
        when(clienteRepository.save(any(ClienteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        for (int i = 0; i < 200; i++) {
            LayawayClienteResponse response = procesar(requestInsertar);
            assertTrue(response.getNumeroCuenta().matches("^\\d{12}$"), response.getNumeroCuenta());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void procesarOperacion_Bandera2_CambiarCurp_LanzaBadRequest() {
        ClienteEntity existente = ClienteEntity.builder()
                .id(7L).curp("OTRA950515HDFRMN09").rfc("PERJ9505151A2").activo(true).build();
        when(clienteRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existente), PageRequest.of(0, 2), 1));

        LayawayClienteRequest requestActualizar = LayawayClienteRequest.builder()
                .bandera(2)
                .filtros(FiltrosClienteDto.builder().curp("OTRA950515HDFRMN09").build())
                .datosPersonales(requestInsertar.getDatosPersonales())
                .build();

        OnboardingException exception = assertThrows(OnboardingException.class, () ->
                procesar(requestActualizar)
        );

        assertEquals(400, exception.getCodigo());
        assertTrue(exception.getMessage().contains("CURP no puede modificarse"));
        verify(clienteRepository, never()).save(any());
    }

    @Test
    void procesarOperacion_Bandera1_NacionalidadFueraDeCatalogoOInactiva_Lanza404SinGuardar() {
        // existsByNombreAndActivoTrue solo considera registros activos: una nacionalidad inexistente
        // o dada de baja en cat_nacionalidad devuelve false
        requestInsertar.getDatosPersonales().setNacionalidad("MARCIANA");
        when(catNacionalidadRepository.existsByNombreAndActivoTrue("MARCIANA")).thenReturn(false);

        OnboardingException exception = assertThrows(OnboardingException.class, () ->
                procesar(requestInsertar)
        );

        assertEquals(404, exception.getCodigo());
        verify(clienteRepository, never()).save(any());
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    void procesarOperacion_Bandera1_ValoresDeCatalogoSeGuardanExactos() {
        requestInsertar.getDatosPersonales().setSexo("FEMENINO");
        requestInsertar.getDatosPersonales().setEstadoCivil("UNION LIBRE");
        when(catNacionalidadRepository.existsByNombreAndActivoTrue("MEXICANA")).thenReturn(true);
        when(clienteRepository.save(any(ClienteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        procesar(requestInsertar);

        verify(clienteRepository).save(argThat(c -> "FEMENINO".equals(c.getSexo())
                && "UNION LIBRE".equals(c.getEstadoCivil()) && "MEXICANA".equals(c.getNacionalidad())));
    }

    // ---------- Bandera 4: consulta ----------

    private ClienteEntity clienteConsulta(Long id) {
        return ClienteEntity.builder().id(id).nombre("Ana").apellidoPaterno("Paz").apellidoMaterno("Ruiz")
                .curp("PARA950515HDFRMN01").rfc("PARA9505151A2").correo("ana@test.com").telefonoMovil("5512345678")
                .activo(true).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void procesarOperacion_Bandera4_SinFiltros_DevuelveTodosLosClientes() {
        ClienteEntity c1 = clienteConsulta(1L);
        ClienteEntity c2 = clienteConsulta(2L);
        CuentaEntity cuenta = CuentaEntity.builder().id(9L).numeroCuenta("300123456789")
                .saldo(new BigDecimal("1000.00")).estatus("ACTIVA").cliente(c1).build();
        when(clienteRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(c1, c2), PageRequest.of(0, 1000), 2));
        when(cuentaRepository.findByClienteIdIn(any())).thenReturn(List.of(cuenta));

        // Con "filtros" ausente y con "filtros": {} el resultado es el mismo: todos los clientes
        for (FiltrosClienteDto filtros : new FiltrosClienteDto[]{null, new FiltrosClienteDto()}) {
            LayawayClienteResponse r = procesar(
                    LayawayClienteRequest.builder().bandera(4).filtros(filtros).build());

            assertEquals("CONSULTAR", r.getOperacionRealizada());
            assertEquals(2, r.getClientes().size());
            assertEquals(2L, r.getTotalCoincidencias());
            assertFalse(r.getResultadosTruncados());
            assertEquals("300123456789", r.getClientes().get(0).getCuentas().get(0).getNumeroCuenta());
            assertTrue(r.getClientes().get(1).getCuentas().isEmpty());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void procesarOperacion_Bandera4_ConFiltros_ConsultaConEspecificacion() {
        when(clienteRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(clienteConsulta(1L)), PageRequest.of(0, 1000), 1));
        when(cuentaRepository.findByClienteIdIn(any())).thenReturn(List.of());

        LayawayClienteResponse r = procesar(LayawayClienteRequest.builder().bandera(4)
                .filtros(FiltrosClienteDto.builder().curp("PARA95").rfc("PARA").numeroCuenta("3001").build())
                .build());

        assertEquals(1, r.getClientes().size());
        verify(clienteRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void procesarOperacion_Bandera4_MasCoincidenciasQueElTope_IndicaTruncado() {
        when(clienteRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(clienteConsulta(1L)), PageRequest.of(0, 1), 5000));
        when(cuentaRepository.findByClienteIdIn(any())).thenReturn(List.of());

        LayawayClienteResponse r = procesar(LayawayClienteRequest.builder().bandera(4).build());

        assertEquals(5000L, r.getTotalCoincidencias());
        assertTrue(r.getResultadosTruncados());
    }

    @Test
    @SuppressWarnings("unchecked")
    void procesarOperacion_Bandera4_ConOtrasSeccionesOClienteId_LanzaBadRequestSinConsultar() {
        LayawayClienteRequest[] invalidas = {
                LayawayClienteRequest.builder().bandera(4).clienteId(5L).build(),
                LayawayClienteRequest.builder().bandera(4).datosPersonales(requestInsertar.getDatosPersonales()).build(),
                LayawayClienteRequest.builder().bandera(4).datosContacto(requestInsertar.getDatosContacto()).build(),
                LayawayClienteRequest.builder().bandera(4).loginCredenciales(requestInsertar.getLoginCredenciales()).build(),
        };
        for (LayawayClienteRequest req : invalidas) {
            OnboardingException ex = assertThrows(OnboardingException.class, () -> procesar(req));
            assertEquals(400, ex.getCodigo());
            assertTrue(ex.getMessage().contains("'filtros'"));
        }
        verifyNoInteractions(cuentaRepository);
        verify(clienteRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    // ---------- Autenticación y autorización a nivel de recurso ----------

    @Test
    @SuppressWarnings("unchecked")
    void banderas2_3_4_SinToken_Lanzan401SinTocarLosDatos() {
        when(sesionService.autenticar(isNull()))
                .thenThrow(new OnboardingException("Se requiere autenticación", HttpStatus.UNAUTHORIZED, 401));
        FiltrosClienteDto llaves = FiltrosClienteDto.builder().curp("PARA950515HDFRMN01").build();

        for (int bandera : new int[]{2, 3, 4}) {
            LayawayClienteRequest req = LayawayClienteRequest.builder().bandera(bandera).filtros(llaves).build();
            OnboardingException ex = assertThrows(OnboardingException.class, () -> clienteLayawayService.procesarOperacion(req, null));
            assertEquals(401, ex.getCodigo(), "bandera " + bandera);
        }
        verify(clienteRepository, never()).findAll(any(Specification.class), any(Pageable.class));
        verify(clienteRepository, never()).save(any());
        verifyNoInteractions(passwordEncoderUtil);
    }

    @Test
    void bandera1_Registro_NoRequiereToken() {
        when(catNacionalidadRepository.existsByNombreAndActivoTrue("MEXICANA")).thenReturn(true);
        when(clienteRepository.save(any(ClienteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LayawayClienteResponse r = clienteLayawayService.procesarOperacion(requestInsertar, null);

        assertEquals("INSERTAR", r.getOperacionRealizada());
        verifyNoInteractions(sesionService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void bandera3_Baja_UnClienteNoPuedeDarDeBaja_Lanza403() {
        when(sesionService.autenticar(any())).thenReturn(CLIENTE);

        OnboardingException ex = assertThrows(OnboardingException.class, () -> procesar(LayawayClienteRequest.builder().bandera(3)
                .filtros(FiltrosClienteDto.builder().curp("PARA950515HDFRMN01").build()).build()));

        assertEquals(403, ex.getCodigo());
        verify(clienteRepository, never()).findAll(any(Specification.class), any(Pageable.class));
        verify(clienteRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void banderas2y4_ClienteSinClienteAsociado_Lanza403() {
        when(sesionService.autenticar(any())).thenReturn(new SesionUsuario(3L, "huerfano", "CLIENTE", null));
        for (int bandera : new int[]{2, 4}) {
            LayawayClienteRequest req = LayawayClienteRequest.builder().bandera(bandera)
                    .filtros(FiltrosClienteDto.builder().curp("PARA950515HDFRMN01").build()).build();
            OnboardingException ex = assertThrows(OnboardingException.class, () -> procesar(req));
            assertEquals(403, ex.getCodigo(), "bandera " + bandera);
        }
        verify(clienteRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void bandera4_ClienteSinFiltros_ConsultaConLaRestriccionDeSuPropioRegistro() {
        when(sesionService.autenticar(any())).thenReturn(CLIENTE);
        ClienteEntity propio = clienteConsulta(10L);
        when(clienteRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(propio), PageRequest.of(0, 1000), 1));
        when(cuentaRepository.findByClienteIdIn(any())).thenReturn(List.of());

        LayawayClienteResponse r = procesar(LayawayClienteRequest.builder().bandera(4).build());

        assertEquals(1, r.getClientes().size());
        // la restricción de propietario es una condición más de la consulta: la especificación se ejecuta
        // en la base de datos, donde se comprueba con datos reales (ver pruebas de integración contra PostgreSQL)
        verify(clienteRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    // ---------- Registro inexistente ----------

    @Test
    @SuppressWarnings("unchecked")
    void bandera4_ConFiltrosSinCoincidencias_Lanza404Descriptivo() {
        when(clienteRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1000), 0));

        OnboardingException ex = assertThrows(OnboardingException.class, () -> procesar(LayawayClienteRequest.builder().bandera(4)
                .filtros(FiltrosClienteDto.builder().curp("ZZZZ").build()).build()));

        assertEquals(404, ex.getCodigo());
        assertTrue(ex.getMessage().contains("No se encontró"));
        verifyNoInteractions(cuentaRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void bandera4_SinFiltrosYSinClientes_Devuelve200ConListaVacia() {
        when(clienteRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1000), 0));

        LayawayClienteResponse r = procesar(LayawayClienteRequest.builder().bandera(4).build());

        assertTrue(r.getClientes().isEmpty());
        assertEquals(0L, r.getTotalCoincidencias());
    }

    // ---------- Orden: autenticación -> permiso -> validación ----------

    private static LayawayClienteRequest cuerpoInvalido(int bandera) {
        // nombre de 2 letras y sexo en minúsculas: incumple las reglas de validación
        DatosPersonalesDto dp = DatosPersonalesDto.builder().nombre("Li").sexo("masculino").build();
        return LayawayClienteRequest.builder().bandera(bandera)
                .filtros(FiltrosClienteDto.builder().curp("PARA950515HDFRMN01").build())
                .datosPersonales(dp).build();
    }

    @Test
    void sinToken_ConCuerpoInvalido_Responde401YNoElDetalleDeValidacion() {
        when(sesionService.autenticar(isNull()))
                .thenThrow(new OnboardingException("Se requiere autenticación", HttpStatus.UNAUTHORIZED, 401));

        for (int bandera : new int[]{2, 3, 4}) {
            OnboardingException ex = assertThrows(OnboardingException.class,
                    () -> clienteLayawayService.procesarOperacion(cuerpoInvalido(bandera), null));
            assertEquals(401, ex.getCodigo(), "bandera " + bandera);
        }
    }

    @Test
    void conToken_ConCuerpoInvalido_Responde400ConDetalleDeCadaCampo() {
        com.proyecto.servicios.exception.ValidacionException ex = assertThrows(
                com.proyecto.servicios.exception.ValidacionException.class, () -> procesar(cuerpoInvalido(2)));

        assertTrue(ex.getErrores().containsKey("datosPersonales.nombre"));
        assertTrue(ex.getErrores().containsKey("datosPersonales.sexo"));
        verify(clienteRepository, never()).save(any());
    }

    @Test
    void clienteQueIntentaBaja_ConCuerpoInvalido_Responde403YNoElDetalleDeValidacion() {
        when(sesionService.autenticar(any())).thenReturn(CLIENTE);

        OnboardingException ex = assertThrows(OnboardingException.class, () -> procesar(cuerpoInvalido(3)));

        assertEquals(403, ex.getCodigo());
    }

    @Test
    void bandera1_RegistroConCuerpoInvalido_SigueRespondiendo400() {
        com.proyecto.servicios.exception.ValidacionException ex = assertThrows(
                com.proyecto.servicios.exception.ValidacionException.class,
                () -> clienteLayawayService.procesarOperacion(
                        LayawayClienteRequest.builder().bandera(1).datosPersonales(DatosPersonalesDto.builder().nombre("Li").build()).build(), null));

        assertFalse(ex.getErrores().isEmpty());
        verifyNoInteractions(sesionService);
    }
}

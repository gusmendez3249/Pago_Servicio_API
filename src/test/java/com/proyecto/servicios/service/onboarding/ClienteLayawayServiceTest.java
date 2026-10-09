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
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
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

    @InjectMocks
    private ClienteLayawayService clienteLayawayService;

    private LayawayClienteRequest requestInsertar;

    @BeforeEach
    void setUp() {
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

        LayawayClienteResponse response = clienteLayawayService.procesarOperacion(requestInsertar);

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
                .clienteId(5L)
                .build();

        when(clienteRepository.findById(5L)).thenReturn(Optional.of(clienteExistente));
        when(cuentaRepository.findByClienteId(5L)).thenReturn(Collections.singletonList(cuenta));

        LayawayClienteResponse response = clienteLayawayService.procesarOperacion(requestBaja);

        assertNotNull(response);
        assertEquals("ELIMINAR_BAJA_LOGICA", response.getOperacionRealizada());
        assertFalse(response.getActivo());
        assertEquals("INACTIVA", response.getEstatusCuenta());
        verify(clienteRepository, times(1)).save(clienteExistente);
    }

    @Test
    void procesarOperacion_Bandera1_ConClienteIdInvalido_LanzaBadRequest() {
        requestInsertar.setClienteId(10L); // Postura B: bandera 1 solo admite null o 0

        OnboardingException exception = assertThrows(OnboardingException.class, () ->
                clienteLayawayService.procesarOperacion(requestInsertar)
        );

        assertEquals(400, exception.getCodigo());
        assertTrue(exception.getMessage().contains("clienteId debe ser nulo o 0"));
    }

    @Test
    void procesarOperacion_Bandera2_ConClienteIdInvalido_LanzaBadRequest() {
        LayawayClienteRequest requestActualizar = LayawayClienteRequest.builder()
                .bandera(2)
                .clienteId(-1L) // ID menor o igual a 0 no permitido
                .build();

        OnboardingException exception = assertThrows(OnboardingException.class, () ->
                clienteLayawayService.procesarOperacion(requestActualizar)
        );

        assertEquals(400, exception.getCodigo());
        assertTrue(exception.getMessage().contains("mayor a 0"));
    }

    @Test
    void procesarOperacion_Bandera3_ConClienteIdInvalido_LanzaBadRequest() {
        LayawayClienteRequest requestBaja = LayawayClienteRequest.builder()
                .bandera(3)
                .clienteId(0L) // ID 0 no permitido en baja
                .build();

        OnboardingException exception = assertThrows(OnboardingException.class, () ->
                clienteLayawayService.procesarOperacion(requestBaja)
        );

        assertEquals(400, exception.getCodigo());
        assertTrue(exception.getMessage().contains("mayor a 0"));
    }

    @Test
    void procesarOperacion_Bandera1_SinPassword_NoAsignaPasswordPorDefecto() {
        requestInsertar.getLoginCredenciales().setPassword(null);
        when(catNacionalidadRepository.existsByNombreAndActivoTrue("MEXICANA")).thenReturn(true);
        when(clienteRepository.save(any(ClienteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        clienteLayawayService.procesarOperacion(requestInsertar);

        verify(passwordEncoderUtil).encode(null);
        verify(passwordEncoderUtil, never()).encode("DefaultPassword123!");
    }

    @Test
    void procesarOperacion_Bandera1_NacionalidadEnMinusculasOClave_Lanza404() {
        // Catálogo estricto: solo el nombre exacto "MEXICANA"; "mex"/"MEX"/"Mexicana" no existen como nombre
        for (String variante : new String[]{"MEX", "Mexicana", "mexicana"}) {
            requestInsertar.getDatosPersonales().setNacionalidad(variante);
            when(catNacionalidadRepository.existsByNombreAndActivoTrue(variante)).thenReturn(false);

            OnboardingException exception = assertThrows(OnboardingException.class, () ->
                    clienteLayawayService.procesarOperacion(requestInsertar)
            );
            assertEquals(404, exception.getCodigo(), variante);
        }
        verify(clienteRepository, never()).save(any());
    }

    @Test
    void procesarOperacion_Bandera1_NumeroCuentaSiempreDe12Digitos() {
        when(catNacionalidadRepository.existsByNombreAndActivoTrue("MEXICANA")).thenReturn(true);
        when(clienteRepository.save(any(ClienteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        for (int i = 0; i < 200; i++) {
            LayawayClienteResponse response = clienteLayawayService.procesarOperacion(requestInsertar);
            assertTrue(response.getNumeroCuenta().matches("^\\d{12}$"), response.getNumeroCuenta());
        }
    }

    @Test
    void procesarOperacion_Bandera2_CambiarCurp_LanzaBadRequest() {
        ClienteEntity existente = ClienteEntity.builder()
                .id(7L).curp("OTRA950515HDFRMN09").rfc("PERJ9505151A2").activo(true).build();
        when(clienteRepository.findById(7L)).thenReturn(Optional.of(existente));

        LayawayClienteRequest requestActualizar = LayawayClienteRequest.builder()
                .bandera(2)
                .clienteId(7L)
                .datosPersonales(requestInsertar.getDatosPersonales())
                .build();

        OnboardingException exception = assertThrows(OnboardingException.class, () ->
                clienteLayawayService.procesarOperacion(requestActualizar)
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
                clienteLayawayService.procesarOperacion(requestInsertar)
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

        clienteLayawayService.procesarOperacion(requestInsertar);

        verify(clienteRepository).save(argThat(c -> "FEMENINO".equals(c.getSexo())
                && "UNION LIBRE".equals(c.getEstadoCivil()) && "MEXICANA".equals(c.getNacionalidad())));
    }

}

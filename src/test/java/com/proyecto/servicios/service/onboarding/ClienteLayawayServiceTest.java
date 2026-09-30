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
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
                .telefonoMovil("5512345678")
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
}

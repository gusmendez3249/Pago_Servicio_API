package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.config.onboarding.PasswordEncoderUtil;
import com.proyecto.servicios.dto.onboarding.*;
import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import com.proyecto.servicios.entity.onboarding.CuentaEntity;
import com.proyecto.servicios.entity.onboarding.DomicilioEntity;
import com.proyecto.servicios.entity.onboarding.UsuarioLoginEntity;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.exception.RegistroDuplicadoException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.repositorys.onboarding.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Bandera 2 (actualización): cada sección del cliente se actualiza solo con los campos que llegan. */
@ExtendWith(MockitoExtension.class)
class ClienteLayawayServiceActualizacionTest {

    private static final String TOKEN = "Bearer token-de-prueba";
    private static final String CURP = "PERJ950515HDFRMN01";
    private static final String RFC = "PERJ9505151A2";

    @Mock private ClienteRepository clienteRepository;
    @Mock private DomicilioRepository domicilioRepository;
    @Mock private CuentaRepository cuentaRepository;
    @Mock private UsuarioLoginRepository usuarioLoginRepository;
    @Mock private PasswordEncoderUtil passwordEncoderUtil;
    @Mock private CatNacionalidadRepository catNacionalidadRepository;
    @Mock private SesionService sesionService;
    @Spy private TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));

    @InjectMocks private ClienteLayawayService service;

    private ClienteEntity cliente;
    private UsuarioLoginEntity usuario;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(sesionService.autenticar(TOKEN)).thenReturn(new SesionUsuario(1L, "admin", SesionUsuario.ROL_EJECUTIVO, null));
        cliente = ClienteEntity.builder().id(7L).curp(CURP).rfc(RFC).nombre("Juan").apellidoPaterno("Perez")
                .apellidoMaterno("Gomez").correo("juan@correo.com").activo(true).build();
        usuario = UsuarioLoginEntity.builder().id(3L).username("juan_perez").isLoggedIn(true)
                .tokenHash("hash-token").tokenExpira(LocalDateTime.now().plusMinutes(5)).build();
        when(clienteRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cliente), PageRequest.of(0, 2), 1));
    }

    private LayawayClienteResponse actualizar(LayawayClienteRequest.LayawayClienteRequestBuilder request) {
        return service.procesarOperacion(request.bandera(2)
                .filtros(FiltrosClienteDto.builder().curp(CURP).build()).build(), TOKEN);
    }

    @Test
    void actualizaTodasLasSeccionesYCierraLaSesionAlCambiarContrasena() {
        when(catNacionalidadRepository.existsByNombreAndActivoTrue("CANADIENSE")).thenReturn(true);
        when(clienteRepository.existsByCorreo("nuevo@correo.com")).thenReturn(false);
        when(usuarioLoginRepository.findByClienteId(7L)).thenReturn(Optional.of(usuario));
        when(passwordEncoderUtil.encode("NuevaClave#1")).thenReturn("hash-nuevo");
        when(cuentaRepository.findByClienteId(7L)).thenReturn(List.of(CuentaEntity.builder()
                .numeroCuenta("695429041188").saldo(new BigDecimal("500.00")).estatus("ACTIVA").build()));

        LayawayClienteResponse response = actualizar(LayawayClienteRequest.builder()
                .datosPersonales(DatosPersonalesDto.builder().curp(CURP.toLowerCase()).rfc(RFC)
                        .nombre("  Juan  ").segundoNombre("Carlos").apellidoPaterno("Perez").apellidoMaterno("Lopez")
                        .fechaNacimiento(LocalDate.of(1995, 5, 15)).sexo("MASCULINO").nacionalidad("CANADIENSE")
                        .estadoCivil("CASADO").datosBiometricos(123L).build())
                .datosContacto(DatosContactoDto.builder().correo(" Nuevo@Correo.com ")
                        .telefonoMovil(5512345678L).telefonoAlt(4181234567L).build())
                .domicilio(DomicilioDto.builder().calle("Reforma").numeroExterior("10").numeroInterior("2B")
                        .colonia("Centro").municipio("Cuauhtemoc").estado("CDMX").codigoPostal("06000").pais("Mexico").build())
                .informacionLaboral(InformacionLaboralDto.builder().ocupacion("Docente").empresa("UTNG")
                        .ingresoMensual(new BigDecimal("25000.00")).build())
                .loginCredenciales(LoginCredencialesDto.builder().username("juan_perez").password("NuevaClave#1")
                        .faceIdBiometrico(555L).build()));

        assertEquals("Carlos", cliente.getSegundoNombre());
        assertEquals("Lopez", cliente.getApellidoMaterno());
        assertEquals("CANADIENSE", cliente.getNacionalidad());
        assertEquals("CASADO", cliente.getEstadoCivil());
        assertEquals("nuevo@correo.com", cliente.getCorreo());
        assertEquals("4181234567", cliente.getTelefonoAlt());
        DomicilioEntity domicilio = cliente.getDomicilio();
        assertNotNull(domicilio, "si el cliente no tenía domicilio se crea uno");
        assertEquals("2B", domicilio.getNumeroInterior());
        assertEquals("06000", domicilio.getCodigoPostal());
        assertEquals("UTNG", cliente.getEmpresa());
        assertEquals(new BigDecimal("25000.00"), cliente.getIngresoMensual());
        assertEquals(CURP, cliente.getCurp(), "la CURP nunca cambia");

        assertEquals("hash-nuevo", usuario.getPasswordHash());
        assertFalse(usuario.getIsLoggedIn(), "cambiar la contraseña cierra la sesión activa");
        assertNull(usuario.getTokenHash());
        assertEquals(555L, usuario.getFaceIdBiometrico());
        verify(clienteRepository).save(cliente);
        assertNotNull(response);
    }

    @Test
    void mismoCorreoConOtrasMayusculasNoSeConsideraCambio() {
        when(cuentaRepository.findByClienteId(7L)).thenReturn(List.of());

        actualizar(LayawayClienteRequest.builder()
                .datosContacto(DatosContactoDto.builder().correo("JUAN@correo.com").build()));

        verify(clienteRepository, never()).existsByCorreo(anyString());
        assertEquals("juan@correo.com", cliente.getCorreo());
    }

    @Test
    void correoNuevoYaRegistradoPorOtroClienteSeRechaza() {
        when(clienteRepository.existsByCorreo("otro@correo.com")).thenReturn(true);
        LayawayClienteRequest.LayawayClienteRequestBuilder request = LayawayClienteRequest.builder()
                .datosContacto(DatosContactoDto.builder().correo("otro@correo.com").build());

        assertThrows(RegistroDuplicadoException.class, () -> actualizar(request));
        verify(clienteRepository, never()).save(any());
    }

    @Test
    void elRfcNoPuedeModificarse() {
        LayawayClienteRequest.LayawayClienteRequestBuilder request = LayawayClienteRequest.builder()
                .datosPersonales(DatosPersonalesDto.builder().rfc("XAXX010101000").build());

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () -> actualizar(request));
        assertTrue(ex.getMessage().contains("RFC"));
    }

    @Test
    void credencialesSinUsuarioRegistradoDevuelvenNoEncontrado() {
        when(usuarioLoginRepository.findByClienteId(7L)).thenReturn(Optional.empty());
        LayawayClienteRequest.LayawayClienteRequestBuilder request = LayawayClienteRequest.builder()
                .loginCredenciales(LoginCredencialesDto.builder().faceIdBiometrico(1L).build());

        assertThrows(RecursoNoEncontradoException.class, () -> actualizar(request));
    }

    @Test
    void domicilioExistenteSeActualizaSinReemplazarlo() {
        DomicilioEntity existente = new DomicilioEntity();
        existente.setCalle("Vieja");
        cliente.setDomicilio(existente);
        when(cuentaRepository.findByClienteId(7L)).thenReturn(List.of());

        actualizar(LayawayClienteRequest.builder().domicilio(DomicilioDto.builder().calle("Nueva").build()));

        assertSame(existente, cliente.getDomicilio());
        assertEquals("Nueva", existente.getCalle());
    }
}

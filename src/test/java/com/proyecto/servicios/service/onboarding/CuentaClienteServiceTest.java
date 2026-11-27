package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.CuentaClienteResponse;
import com.proyecto.servicios.dto.onboarding.DetalleErrorPago;
import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import com.proyecto.servicios.entity.onboarding.CuentaEntity;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.exception.ClienteInactivoException;
import com.proyecto.servicios.repositorys.onboarding.ClienteRepository;
import com.proyecto.servicios.repositorys.onboarding.CuentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** POST /cuentas/mias: solo las cuentas ACTIVAS del titular; ejecutivo 403 NO_TITULAR; cliente inactivo 403 CLIENTE_INACTIVO. */
@ExtendWith(MockitoExtension.class)
class CuentaClienteServiceTest {

    private static final String AUTH = "Bearer token";

    @Mock private SesionService sesionService;
    @Mock private CuentaRepository cuentaRepository;
    @Mock private ClienteRepository clienteRepository;

    private CuentaClienteService service;

    @BeforeEach
    void setUp() {
        service = new CuentaClienteService(sesionService, cuentaRepository, clienteRepository);
    }

    @Test
    void devuelveSoloCuentasActivasConNumeroEnmascaradoYSaldo() {
        when(sesionService.autenticar(AUTH)).thenReturn(new SesionUsuario(7L, "cliente1", SesionUsuario.ROL_CLIENTE, 10L));
        when(clienteRepository.findById(10L)).thenReturn(Optional.of(ClienteEntity.builder().id(10L).activo(true).build()));
        when(cuentaRepository.findByClienteIdAndEstatusOrderByIdAsc(10L, "ACTIVA")).thenReturn(List.of(
                CuentaEntity.builder().id(1L).numeroCuenta("695429041188").saldo(new BigDecimal("1000.00")).estatus("ACTIVA").build()));

        List<CuentaClienteResponse> cuentas = service.listarMias(AUTH);

        assertEquals(1, cuentas.size());
        assertEquals("695429041188", cuentas.get(0).getNumCuenta());
        assertEquals("********1188", cuentas.get(0).getNumCuentaEnmascarada());
        assertEquals(new BigDecimal("1000.00"), cuentas.get(0).getSaldo());
        assertEquals("ACTIVA", cuentas.get(0).getEstatus());
        verify(cuentaRepository, never()).findByClienteId(any());
    }

    @Test
    void sinCuentasActivas_ListaVacia() {
        when(sesionService.autenticar(AUTH)).thenReturn(new SesionUsuario(7L, "cliente1", SesionUsuario.ROL_CLIENTE, 10L));
        when(clienteRepository.findById(10L)).thenReturn(Optional.of(ClienteEntity.builder().id(10L).activo(true).build()));
        when(cuentaRepository.findByClienteIdAndEstatusOrderByIdAsc(10L, "ACTIVA")).thenReturn(List.of());

        assertTrue(service.listarMias(AUTH).isEmpty());
    }

    @Test
    void ejecutivo_403NoTitular() {
        when(sesionService.autenticar(AUTH)).thenReturn(new SesionUsuario(1L, "ejec", SesionUsuario.ROL_EJECUTIVO, null));

        AccesoDenegadoException ex = assertThrows(AccesoDenegadoException.class, () -> service.listarMias(AUTH));

        assertEquals(403, ex.getCodigo());
        assertEquals(MotivoPago.NO_TITULAR, ((DetalleErrorPago) ex.getDetalle()).getMotivo());
        verifyNoInteractions(cuentaRepository);
    }

    @Test
    void clienteInactivo_403ClienteInactivo() {
        when(sesionService.autenticar(AUTH)).thenReturn(new SesionUsuario(7L, "cliente1", SesionUsuario.ROL_CLIENTE, 10L));
        when(clienteRepository.findById(10L)).thenReturn(Optional.of(ClienteEntity.builder().id(10L).activo(false).build()));

        ClienteInactivoException ex = assertThrows(ClienteInactivoException.class, () -> service.listarMias(AUTH));

        assertEquals(MotivoPago.CLIENTE_INACTIVO, ((DetalleErrorPago) ex.getDetalle()).getMotivo());
        verifyNoInteractions(cuentaRepository);
    }
}

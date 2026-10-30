package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.config.onboarding.PasswordEncoderUtil;
import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.exception.CuentaBloqueadaException;
import com.proyecto.servicios.repositorys.onboarding.ClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** PIN 2FA: hash PBKDF2, migración de PIN en claro y bloqueo tras intentos fallidos. */
@ExtendWith(MockitoExtension.class)
class PinClienteServiceTest {

    private static final PasswordEncoderUtil ENCODER = new PasswordEncoderUtil();

    @Mock private ClienteRepository clienteRepository;

    private PinClienteService service;
    private ClienteEntity cliente;

    @BeforeEach
    void setUp() {
        service = new PinClienteService(clienteRepository, ENCODER,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
        cliente = ClienteEntity.builder().id(10L).activo(true).pin(ENCODER.encode("1234")).build();
        when(clienteRepository.findByIdParaActualizar(10L)).thenReturn(Optional.of(cliente));
    }

    @Test
    void pinCorrecto_ReiniciaIntentos() {
        cliente.setPinIntentosFallidos((short) 2);

        assertDoesNotThrow(() -> service.verificar(10L, "1234"));
        assertEquals((short) 0, cliente.getPinIntentosFallidos());
    }

    @Test
    void pinHeredadoEnClaro_SeAceptaYSeReemplazaPorSuHash() {
        cliente.setPin("1234");

        service.verificar(10L, "1234");

        assertTrue(cliente.getPin().startsWith("pbkdf2$"));
        assertTrue(ENCODER.matches("1234", cliente.getPin()));
    }

    @Test
    void pinIncorrecto_Responde403YCuentaElIntento() {
        assertThrows(AccesoDenegadoException.class, () -> service.verificar(10L, "9999"));
        assertEquals((short) 1, cliente.getPinIntentosFallidos());
        assertNull(cliente.getPinBloqueadoHasta());
    }

    @Test
    void tercerIntentoFallido_BloqueaElPin() {
        assertThrows(AccesoDenegadoException.class, () -> service.verificar(10L, "0000"));
        assertThrows(AccesoDenegadoException.class, () -> service.verificar(10L, "0000"));
        assertThrows(CuentaBloqueadaException.class, () -> service.verificar(10L, "0000"));

        assertNotNull(cliente.getPinBloqueadoHasta());
        assertTrue(cliente.getPinBloqueadoHasta().isAfter(LocalDateTime.now().plusMinutes(PinClienteService.MINUTOS_BLOQUEO - 1)));
    }

    @Test
    void pinBloqueado_RechazaInclusoElPinCorrecto() {
        cliente.setPinBloqueadoHasta(LocalDateTime.now().plusMinutes(5));

        assertThrows(CuentaBloqueadaException.class, () -> service.verificar(10L, "1234"));
    }

    @Test
    void bloqueoVencido_PermiteDeNuevo() {
        cliente.setPinBloqueadoHasta(LocalDateTime.now().minusMinutes(1));

        assertDoesNotThrow(() -> service.verificar(10L, "1234"));
        assertNull(cliente.getPinBloqueadoHasta());
    }

    @Test
    void clienteSinPin_Responde403() {
        cliente.setPin(null);

        assertThrows(AccesoDenegadoException.class, () -> service.verificar(10L, "1234"));
    }
}

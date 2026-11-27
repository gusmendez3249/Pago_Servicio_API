package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.config.onboarding.PasswordEncoderUtil;
import com.proyecto.servicios.dto.onboarding.DetalleErrorPago;
import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.exception.CuentaBloqueadaException;
import com.proyecto.servicios.repositorys.onboarding.ClienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

/**
 * Verificación del PIN (2FA) que autoriza cada pago. El PIN se guarda con PBKDF2; tras {@value #MAX_INTENTOS}
 * intentos fallidos se bloquea {@value #MINUTOS_BLOQUEO} minutos para impedir adivinarlo por fuerza bruta.
 * Nunca se registra el PIN recibido ni el almacenado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PinClienteService {

    public static final int MAX_INTENTOS = 3;
    public static final int MINUTOS_BLOQUEO = 15;

    private static final String PREFIJO_HASH = "pbkdf2$";

    private enum Resultado { CORRECTO, INCORRECTO, BLOQUEADO, SIN_PIN }

    /** Resultado de evaluar el PIN; en INCORRECTO trae los intentos que quedan antes del bloqueo (nulo si no aplica). */
    private record Evaluacion(Resultado resultado, Integer intentosRestantes) {
        static Evaluacion de(Resultado resultado) {
            return new Evaluacion(resultado, null);
        }
    }

    private final ClienteRepository clienteRepository;
    private final PasswordEncoderUtil passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    /**
     * @throws AccesoDenegadoException  403 si el PIN es incorrecto o el cliente no tiene PIN
     * @throws CuentaBloqueadaException 423 si el PIN está bloqueado por intentos fallidos
     */
    public void verificar(Long clienteId, String pin) {
        // La transacción devuelve el resultado y se lanza fuera de ella: así el contador de intentos sí se guarda
        Evaluacion evaluacion = transactionTemplate.execute(status -> evaluar(clienteId, pin));
        if (evaluacion == null) {
            evaluacion = Evaluacion.de(Resultado.INCORRECTO);
        }
        switch (evaluacion.resultado()) {
            case CORRECTO -> {
                // continúa el pago
            }
            case BLOQUEADO -> throw new CuentaBloqueadaException(
                    "El PIN de seguridad está bloqueado por exceso de intentos fallidos. Intente de nuevo en "
                            + MINUTOS_BLOQUEO + " minutos.",
                    DetalleErrorPago.builder().motivo(MotivoPago.PIN_BLOQUEADO).minutosBloqueo(MINUTOS_BLOQUEO).build());
            case SIN_PIN -> throw new AccesoDenegadoException(
                    "El cliente no tiene un PIN de seguridad configurado para autorizar pagos.", MotivoPago.SIN_PIN);
            default -> throw new AccesoDenegadoException("El PIN de seguridad (2FA) es incorrecto.",
                    DetalleErrorPago.builder().motivo(MotivoPago.PIN_INCORRECTO)
                            .intentosRestantes(evaluacion.intentosRestantes()).build());
        }
    }

    private Evaluacion evaluar(Long clienteId, String pin) {
        ClienteEntity cliente = clienteRepository.findByIdParaActualizar(clienteId).orElse(null);
        if (cliente == null) {
            return Evaluacion.de(Resultado.INCORRECTO);
        }
        LocalDateTime ahora = LocalDateTime.now();
        if (cliente.getPinBloqueadoHasta() != null && cliente.getPinBloqueadoHasta().isAfter(ahora)) {
            return Evaluacion.de(Resultado.BLOQUEADO);
        }

        String almacenado = cliente.getPin();
        if (almacenado == null || almacenado.isBlank()) {
            passwordEncoder.simularVerificacion(pin); // mismo tiempo de respuesta que un PIN incorrecto
            return Evaluacion.de(Resultado.SIN_PIN);
        }

        boolean esHash = almacenado.startsWith(PREFIJO_HASH);
        boolean coincide = esHash
                ? passwordEncoder.matches(pin, almacenado)
                : MessageDigest.isEqual(almacenado.getBytes(StandardCharsets.UTF_8), pin.getBytes(StandardCharsets.UTF_8));

        if (coincide) {
            cliente.setPinIntentosFallidos((short) 0);
            cliente.setPinBloqueadoHasta(null);
            if (!esHash || passwordEncoder.requiereRecifrado(almacenado)) {
                // PIN heredado en texto claro: se reemplaza por su hash en el primer uso correcto
                cliente.setPin(passwordEncoder.encode(pin));
            }
            return Evaluacion.de(Resultado.CORRECTO);
        }

        int intentos = (cliente.getPinIntentosFallidos() == null ? 0 : cliente.getPinIntentosFallidos()) + 1;
        if (intentos >= MAX_INTENTOS) {
            cliente.setPinIntentosFallidos((short) 0);
            cliente.setPinBloqueadoHasta(ahora.plusMinutes(MINUTOS_BLOQUEO));
            log.warn("PIN bloqueado por intentos fallidos. clienteId={}", clienteId);
            return Evaluacion.de(Resultado.BLOQUEADO);
        }
        cliente.setPinIntentosFallidos((short) intentos);
        return new Evaluacion(Resultado.INCORRECTO, MAX_INTENTOS - intentos);
    }
}

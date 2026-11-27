package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.model.gestopago.ResultadoSendTx;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Protege al servidor de un Gesto Pago lento o caído. Se consulta ANTES de reservar saldo, así que un rechazo aquí
 * nunca toca el dinero del cliente.
 * <ul>
 *   <li><b>Bulkhead:</b> como máximo {@code gestopago.proteccion.max-concurrentes} pagos esperan al proveedor a la vez
 *       (cada uno puede tardar hasta 65 s). El resto recibe 503 de inmediato en lugar de ocupar hilos de Tomcat.</li>
 *   <li><b>Circuit breaker:</b> tras {@code fallos-para-abrir} desenlaces consecutivos sin respuesta (no enviada o
 *       indeterminada), se dejan de enviar pagos durante {@code segundos-abierto}; luego pasa uno de prueba.</li>
 * </ul>
 */
@Slf4j
@Component
public class ProteccionGestoPago {

    private final Semaphore cupos;
    private final int fallosParaAbrir;
    private final long abiertoMs;
    private final LongSupplier reloj;
    private final AtomicInteger fallosSeguidos = new AtomicInteger();
    private volatile long abiertoHasta;

    @Autowired
    public ProteccionGestoPago(@Value("${gestopago.proteccion.max-concurrentes:50}") int maxConcurrentes,
                               @Value("${gestopago.proteccion.fallos-para-abrir:5}") int fallosParaAbrir,
                               @Value("${gestopago.proteccion.segundos-abierto:30}") long segundosAbierto) {
        this(maxConcurrentes, fallosParaAbrir, segundosAbierto, System::currentTimeMillis);
    }

    ProteccionGestoPago(int maxConcurrentes, int fallosParaAbrir, long segundosAbierto, LongSupplier reloj) {
        this.cupos = new Semaphore(maxConcurrentes);
        this.fallosParaAbrir = fallosParaAbrir;
        this.abiertoMs = segundosAbierto * 1000;
        this.reloj = reloj;
    }

    /** Sin límites (pruebas y usos fuera de Spring). */
    public static ProteccionGestoPago sinLimites() {
        return new ProteccionGestoPago(Integer.MAX_VALUE, Integer.MAX_VALUE, 0, System::currentTimeMillis);
    }

    /** @return true si el pago puede ir al proveedor; si es true, el llamador DEBE llamar a {@link #salir}. */
    public boolean entrar() {
        if (reloj.getAsLong() < abiertoHasta) {
            return false;
        }
        return cupos.tryAcquire();
    }

    /**
     * Libera el cupo y actualiza el circuit breaker con el desenlace. Nulo = el pago no llegó a enviarse (saldo
     * insuficiente, llave duplicada...): no dice nada del proveedor y no cuenta como falla.
     */
    public void salir(ResultadoSendTx resultado) {
        cupos.release();
        if (resultado == null) {
            return;
        }
        boolean sinRespuesta = resultado.desenlace() == ResultadoSendTx.Desenlace.NO_ENVIADA
                || resultado.desenlace() == ResultadoSendTx.Desenlace.INDETERMINADA;
        if (!sinRespuesta) {
            fallosSeguidos.set(0);
            return;
        }
        if (fallosSeguidos.incrementAndGet() >= fallosParaAbrir) {
            fallosSeguidos.set(0);
            abiertoHasta = reloj.getAsLong() + abiertoMs;
            log.error("Gesto Pago sin respuesta {} veces seguidas: se pausan los pagos {} s (circuit breaker abierto)",
                    fallosParaAbrir, abiertoMs / 1000);
        }
    }
}

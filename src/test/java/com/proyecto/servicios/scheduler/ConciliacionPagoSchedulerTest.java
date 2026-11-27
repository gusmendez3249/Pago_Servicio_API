package com.proyecto.servicios.scheduler;

import com.proyecto.servicios.service.onboarding.ConciliacionPagoService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

/** La tarea programada concilia y nunca deja escapar un error (el siguiente ciclo lo reintenta). */
class ConciliacionPagoSchedulerTest {

    private final ConciliacionPagoService servicio = mock(ConciliacionPagoService.class);

    @Test
    void ejecutaLaConciliacion() {
        new ConciliacionPagoScheduler(servicio).ejecutarConciliacion();
        verify(servicio).conciliar();
    }

    @Test
    void unErrorNoDetieneLaTarea() {
        doThrow(new IllegalStateException("Gesto Pago caído")).when(servicio).conciliar();
        assertDoesNotThrow(() -> new ConciliacionPagoScheduler(servicio).ejecutarConciliacion());
    }
}

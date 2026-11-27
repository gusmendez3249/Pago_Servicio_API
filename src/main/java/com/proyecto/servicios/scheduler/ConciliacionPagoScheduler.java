package com.proyecto.servicios.scheduler;

import com.proyecto.servicios.service.onboarding.ConciliacionPagoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConciliacionPagoScheduler {

    private final ConciliacionPagoService conciliacionPagoService;

    public ConciliacionPagoScheduler(ConciliacionPagoService conciliacionPagoService) {
        this.conciliacionPagoService = conciliacionPagoService;
    }

    @Scheduled(fixedDelayString = "${gestopago.conciliacion.intervalo-ms:300000}",
            initialDelayString = "${gestopago.conciliacion.intervalo-ms:300000}")
    public void ejecutarConciliacion() {
        try {
            conciliacionPagoService.conciliar();
        } catch (RuntimeException exception) {
            log.error("Falló la conciliación de pagos: {}", exception.getClass().getSimpleName());
        }
    }
}

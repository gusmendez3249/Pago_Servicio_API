package com.proyecto.servicios.service.onboarding;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Respaldo de comprobantes en PostgreSQL cuando Redis no responde. */
class RespaldoComprobantesPostgresTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RespaldoComprobantesPostgres respaldo = new RespaldoComprobantesPostgres(jdbc);

    @Test
    void guardaConVencimientoEnElFuturo() {
        LocalDateTime antes = LocalDateTime.now();
        respaldo.guardar("val-1", "{json}", Duration.ofMinutes(15));

        verify(jdbc).update(startsWith("INSERT INTO tb_comprobante_referencia"), eq("val-1"), eq("{json}"),
                argThat((Timestamp t) -> !t.toLocalDateTime().isBefore(antes.plusMinutes(15))));
    }

    @Test
    void consumirBorraYDevuelveUnaSolaVez() {
        when(jdbc.queryForList(contains("DELETE FROM tb_comprobante_referencia"), eq(String.class), eq("val-1"), any()))
                .thenReturn(List.of("{json}"), List.of());

        assertEquals("{json}", respaldo.consumir("val-1"));
        assertNull(respaldo.consumir("val-1"), "un comprobante no se puede usar dos veces");
    }

    @Test
    void limpiaLosVencidos() {
        when(jdbc.update(contains("expira <="), any(Object[].class))).thenReturn(3, 0);
        respaldo.limpiarVencidos();
        respaldo.limpiarVencidos();
        verify(jdbc, times(2)).update(contains("expira <="), any(Object[].class));
    }
}

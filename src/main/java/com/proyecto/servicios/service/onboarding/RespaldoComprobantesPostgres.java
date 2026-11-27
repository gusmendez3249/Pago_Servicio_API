package com.proyecto.servicios.service.onboarding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Comprobantes en {@code tb_comprobante_referencia}. El consumo es un {@code DELETE ... RETURNING}: atómico, de modo que
 * un comprobante solo se puede usar una vez aunque lleguen dos pagos simultáneos.
 */
@Slf4j
@Component
public class RespaldoComprobantesPostgres implements RespaldoComprobantes {

    private final JdbcTemplate jdbc;

    @Autowired
    public RespaldoComprobantesPostgres(@Qualifier("sfDatasource") DataSource dataSource) {
        this(new JdbcTemplate(dataSource));
    }

    RespaldoComprobantesPostgres(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(String idValidacion, String valor, Duration vigencia) {
        jdbc.update("INSERT INTO tb_comprobante_referencia (id_validacion, valor, expira) VALUES (?, ?, ?)",
                idValidacion, valor, Timestamp.valueOf(LocalDateTime.now().plus(vigencia)));
    }

    @Override
    public String consumir(String idValidacion) {
        List<String> valores = jdbc.queryForList(
                "DELETE FROM tb_comprobante_referencia WHERE id_validacion = ? AND expira > ? RETURNING valor",
                String.class, idValidacion, Timestamp.valueOf(LocalDateTime.now()));
        return valores.isEmpty() ? null : valores.get(0);
    }

    /** Limpia los comprobantes vencidos cada 10 minutos. */
    @Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    public void limpiarVencidos() {
        int borrados = jdbc.update("DELETE FROM tb_comprobante_referencia WHERE expira <= ?",
                Timestamp.valueOf(LocalDateTime.now()));
        if (borrados > 0) {
            log.info("Comprobantes de referencia vencidos eliminados: {}", borrados);
        }
    }
}

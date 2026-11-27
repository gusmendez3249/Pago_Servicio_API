package com.proyecto.servicios.config;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Las migraciones se aplican con la tabla de historial configurada (H2 en memoria, sin scripts). */
class FlywayConfigTest {

    @Test
    void migraConLaConfiguracionIndicada() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:flyway_config_test;DB_CLOSE_DELAY=-1");

        FlywayConfig config = new FlywayConfig(new String[]{"classpath:db/sin-migraciones"}, "historial_test", "PUBLIC");
        Flyway flyway = config.flyway(dataSource);

        assertEquals("historial_test", flyway.getConfiguration().getTable());
        assertTrue(flyway.getConfiguration().isOutOfOrder());
        assertTrue(flyway.getConfiguration().isBaselineOnMigrate());
        assertEquals(0, flyway.info().pending().length);
    }
}

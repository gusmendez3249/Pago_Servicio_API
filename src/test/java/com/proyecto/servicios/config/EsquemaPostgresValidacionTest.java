package com.proyecto.servicios.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprueba contra un PostgreSQL REAL que las migraciones de Flyway dejan el esquema exactamente como lo esperan las
 * entidades (hibernate.hbm2ddl.auto=validate) y que los datos biométricos quedan solo como hash.
 * Solo corre si se define DB_VALIDACION_URL (use una base desechable: las migraciones se aplican sobre ella), p. ej.
 * DB_VALIDACION_URL=jdbc:postgresql://localhost:5432/pago_validacion DB_VALIDACION_USER=postgres DB_VALIDACION_PASSWORD=...
 */
@EnabledIfEnvironmentVariable(named = "DB_VALIDACION_URL", matches = "jdbc:postgresql:.+")
class EsquemaPostgresValidacionTest {

    @Test
    void migracionesYEntidadesCoinciden_YNoQuedanBiometricosEnClaro() throws Exception {
        try (HikariDataSource ds = new HikariDataSource()) {
            ds.setJdbcUrl(System.getenv("DB_VALIDACION_URL"));
            ds.setUsername(System.getenv().getOrDefault("DB_VALIDACION_USER", "postgres"));
            ds.setPassword(System.getenv("DB_VALIDACION_PASSWORD"));

            new FlywayConfig(new String[]{"classpath:db/migration"}, "flyway_schema_history", "public").flyway(ds);

            LocalContainerEntityManagerFactoryBean fabrica = new LocalContainerEntityManagerFactoryBean();
            fabrica.setDataSource(ds);
            fabrica.setPackagesToScan("com.proyecto.servicios.entity.sf", "com.proyecto.servicios.entity.gestopago",
                    "com.proyecto.servicios.entity.productos", "com.proyecto.servicios.entity.onboarding");
            fabrica.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            fabrica.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate",
                    "hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect"));
            assertDoesNotThrow(fabrica::afterPropertiesSet, "el esquema migrado no coincide con las entidades");
            EntityManagerFactory emf = fabrica.getObject();
            assertNotNull(emf);
            emf.close();

            try (Connection c = ds.getConnection();
                 ResultSet rs = c.createStatement().executeQuery(
                         "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' "
                                 + "AND column_name IN ('face_id_biometrico', 'datos_biometricos')");
                 ResultSet hashes = c.createStatement().executeQuery(
                         "SELECT count(*) FROM tb_usuario_login WHERE face_id_hash IS NOT NULL "
                                 + "AND face_id_hash NOT LIKE 'pbkdf2$%'")) {
                rs.next();
                assertEquals(0, rs.getInt(1), "las columnas biométricas en claro deben eliminarse");
                hashes.next();
                assertEquals(0, hashes.getInt(1), "todo Face ID guardado debe ser un hash PBKDF2");
            }
        }
    }
}

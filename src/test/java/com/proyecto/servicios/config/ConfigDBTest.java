package com.proyecto.servicios.config;

import com.zaxxer.hikari.HikariConfig;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Configuración del pool de conexiones y de JPA sin conectarse a PostgreSQL. */
class ConfigDBTest {

    private static MockEnvironment entornoBase() {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/pagos")
                .withProperty("spring.datasource.username", "app")
                .withProperty("spring.datasource.password", "secreto");
    }

    @Test
    void poolUsaValoresPorDefecto() {
        HikariConfig config = new ConfigDB(entornoBase()).hikariConfig();

        assertEquals("jdbc:postgresql://localhost:5432/pagos", config.getJdbcUrl());
        assertEquals("app", config.getUsername());
        assertEquals("org.postgresql.Driver", config.getDriverClassName());
        assertEquals(20, config.getMaximumPoolSize());
        assertEquals(5, config.getMinimumIdle());
        assertEquals(3000L, config.getConnectionTimeout());
        assertEquals("sfDatasource", config.getPoolName());
    }

    @Test
    void poolRespetaLosAjustesDeCarga() {
        MockEnvironment entorno = entornoBase()
                .withProperty("app.datasource.max-pool-size", "40")
                .withProperty("app.datasource.min-idle", "10")
                .withProperty("app.datasource.connection-timeout-ms", "1500");

        HikariConfig config = new ConfigDB(entorno).hikariConfig();

        assertEquals(40, config.getMaximumPoolSize());
        assertEquals(10, config.getMinimumIdle());
        assertEquals(1500L, config.getConnectionTimeout());
    }

    @Test
    void sinUrlDeBaseDeDatosFallaAlArrancar() {
        ConfigDB configDB = new ConfigDB(new MockEnvironment());

        assertThrows(IllegalStateException.class, configDB::hikariConfig);
    }

    @Test
    void entityManagerUsaElDataSourceYDialectoDePostgres() {
        DataSource dataSource = mock(DataSource.class);

        LocalContainerEntityManagerFactoryBean emf = new ConfigDB(entornoBase()).sfEntityManagerFactory(dataSource);

        assertSame(dataSource, emf.getDataSource());
        assertEquals("sfDatasource", emf.getPersistenceUnitName());
        assertEquals("org.hibernate.dialect.PostgreSQLDialect", emf.getJpaPropertyMap().get("hibernate.dialect"));
    }

    @Test
    void transactionManagerEnvuelveElEntityManagerFactory() {
        EntityManagerFactory emf = mock(EntityManagerFactory.class);

        PlatformTransactionManager tm = new ConfigDB(entornoBase()).sfTransactionManager(emf);

        assertSame(emf, ((JpaTransactionManager) tm).getEntityManagerFactory());
    }
}

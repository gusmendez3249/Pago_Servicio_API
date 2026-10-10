package com.proyecto.servicios.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = {
                "com.proyecto.servicios.repositorys.sf",
                "com.proyecto.servicios.repositorys.gestopago",
                "com.proyecto.servicios.repositorys.productos",
                "com.proyecto.servicios.repositorys.onboarding"
        },
        transactionManagerRef = "sfTransactionManager",
        entityManagerFactoryRef = "sfEntityManagerFactory"
)
public class ConfigDB {

    private final Environment env;

    public ConfigDB(Environment env) {
        this.env = env;
    }

    @Bean(name = "sfDatasource")
    public DataSource sfDatasource() {
        String url =
                env.getRequiredProperty("spring.datasource.url");

        String username =
                env.getRequiredProperty("spring.datasource.username");

        String password =
                env.getRequiredProperty("spring.datasource.password");

        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");

        // Configurables desde application.properties para ajustar ante pruebas de carga (JMeter)
        config.setMaximumPoolSize(env.getProperty("app.datasource.max-pool-size", Integer.class, 20));
        config.setMaxLifetime(1800000);
        config.setConnectionTimeout(env.getProperty("app.datasource.connection-timeout-ms", Long.class, 3000L));
        config.setValidationTimeout(5000);
        config.setMinimumIdle(env.getProperty("app.datasource.min-idle", Integer.class, 5));
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("sfDatasource");

        return new HikariDataSource(config);
    }

    @Bean(name = "sfEntityManagerFactory")
    @DependsOn("flyway")
    public LocalContainerEntityManagerFactoryBean sfEntityManagerFactory(
            @Qualifier("sfDatasource")
            DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean entityManager =
                new LocalContainerEntityManagerFactoryBean();

        entityManager.setDataSource(dataSource);

        entityManager.setPackagesToScan(
                "com.proyecto.servicios.entity.sf",
                "com.proyecto.servicios.entity.gestopago",
                "com.proyecto.servicios.entity.productos",
                "com.proyecto.servicios.entity.onboarding"
        );

        entityManager.setPersistenceUnitName("sfDatasource");

        entityManager.setJpaVendorAdapter(
                new HibernateJpaVendorAdapter()
        );

        Map<String, Object> properties = new HashMap<>();

        properties.put(
                "hibernate.hbm2ddl.auto",
                "update"
        );

        properties.put(
                "hibernate.show_sql",
                false
        );

        properties.put(
                "hibernate.dialect",
                "org.hibernate.dialect.PostgreSQLDialect"
        );

        properties.put(
                "jakarta.persistence.query.timeout",
                600000
        );

        entityManager.setJpaPropertyMap(properties);

        return entityManager;
    }

    @Bean(name = "sfTransactionManager")
    public PlatformTransactionManager sfTransactionManager(
            @Qualifier("sfEntityManagerFactory")
            EntityManagerFactory entityManagerFactory) {

        return new JpaTransactionManager(entityManagerFactory);
    }
}
package com.proyecto.servicios;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@SpringBootApplication
@EnableFeignClients(
        basePackages = "com.proyecto.servicios.client"
)
@EnableScheduling

@Slf4j
public class App implements CommandLineRunner {

    private final ApplicationContext context;

    public App(ApplicationContext context) {
        this.context = context;
    }

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Override
    public void run(String... args) {
        // displayInfo(context.getBean(BuildProperties.class));
    }

    private static void displayInfo(
            BuildProperties buildProperties) {

        DateTimeFormatter formatter =
                DateTimeFormatter
                        .ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault());

        String out = formatter.format(
                buildProperties.getTime()
        );

        log.info(
                "Nombre artefacto: {}\nVersión: {}\n" +
                        "Fecha Compilación: {}\nArtefacto: {}\n" +
                        "Grupo: {}",
                buildProperties.getName(),
                buildProperties.getVersion(),
                out,
                buildProperties.getArtifact(),
                buildProperties.getGroup()
        );
    }
}
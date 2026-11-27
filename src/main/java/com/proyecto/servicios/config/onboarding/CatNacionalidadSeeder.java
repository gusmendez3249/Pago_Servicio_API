package com.proyecto.servicios.config.onboarding;

import com.proyecto.servicios.entity.onboarding.CatNacionalidadEntity;
import com.proyecto.servicios.repositorys.onboarding.CatNacionalidadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatNacionalidadSeeder {

    private final CatNacionalidadRepository catNacionalidadRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void seedNacionalidades() {
        if (catNacionalidadRepository.count() == 0) {
            log.info("Poblando catálogo inicial de nacionalidades en cat_nacionalidad...");
            List<CatNacionalidadEntity> lista = List.of(
                    CatNacionalidadEntity.builder().clave("MEX").nombre("MEXICANA").activo(true).build(),
                    CatNacionalidadEntity.builder().clave("USA").nombre("ESTADOUNIDENSE").activo(true).build(),
                    CatNacionalidadEntity.builder().clave("COL").nombre("COLOMBIANA").activo(true).build(),
                    CatNacionalidadEntity.builder().clave("ARG").nombre("ARGENTINA").activo(true).build(),
                    CatNacionalidadEntity.builder().clave("ESP").nombre("ESPAÑOLA").activo(true).build(),
                    CatNacionalidadEntity.builder().clave("CAN").nombre("CANADIENSE").activo(true).build(),
                    CatNacionalidadEntity.builder().clave("CUB").nombre("CUBANA").activo(true).build(),
                    CatNacionalidadEntity.builder().clave("VEN").nombre("VENEZOLANA").activo(true).build(),
                    CatNacionalidadEntity.builder().clave("PER").nombre("PERUANA").activo(true).build(),
                    CatNacionalidadEntity.builder().clave("GTM").nombre("GUATEMALTECA").activo(true).build(),
                    CatNacionalidadEntity.builder().clave("EXT").nombre("EXTRANJERA").activo(true).build()
            );
            catNacionalidadRepository.saveAll(lista);
            log.info("Catálogo de nacionalidades poblado exitosamente. Total = {}", lista.size());
        }
    }
}

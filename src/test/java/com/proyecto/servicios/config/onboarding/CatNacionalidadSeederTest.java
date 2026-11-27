package com.proyecto.servicios.config.onboarding;

import com.proyecto.servicios.entity.onboarding.CatNacionalidadEntity;
import com.proyecto.servicios.repositorys.onboarding.CatNacionalidadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Carga inicial del catálogo de nacionalidades. */
@ExtendWith(MockitoExtension.class)
class CatNacionalidadSeederTest {

    @Mock private CatNacionalidadRepository repository;
    @InjectMocks private CatNacionalidadSeeder seeder;

    @Test
    @SuppressWarnings("unchecked")
    void catalogoVacioSePoblaConNacionalidadesActivas() {
        when(repository.count()).thenReturn(0L);

        seeder.seedNacionalidades();

        ArgumentCaptor<List<CatNacionalidadEntity>> guardadas = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(guardadas.capture());
        assertEquals(11, guardadas.getValue().size());
        assertTrue(guardadas.getValue().stream().allMatch(CatNacionalidadEntity::getActivo));
        assertTrue(guardadas.getValue().stream().anyMatch(n -> "MEX".equals(n.getClave())));
    }

    @Test
    void catalogoConDatosNoSeModifica() {
        when(repository.count()).thenReturn(11L);

        seeder.seedNacionalidades();

        verify(repository, never()).saveAll(any());
    }
}

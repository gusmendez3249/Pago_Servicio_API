package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.CatNacionalidadResponse;
import com.proyecto.servicios.entity.onboarding.CatNacionalidadEntity;
import com.proyecto.servicios.repositorys.onboarding.CatNacionalidadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Catálogo de nacionalidades activas expuesto como DTO. */
@ExtendWith(MockitoExtension.class)
class CatNacionalidadServiceTest {

    @Mock private CatNacionalidadRepository repository;
    @InjectMocks private CatNacionalidadService service;

    @Test
    void convierteLasNacionalidadesActivasADto() {
        when(repository.findByActivoTrue()).thenReturn(List.of(
                CatNacionalidadEntity.builder().id(1L).clave("MEX").nombre("MEXICANA").activo(true).build()));

        List<CatNacionalidadResponse> activas = service.obtenerActivas();

        assertEquals(1, activas.size());
        CatNacionalidadResponse mex = activas.get(0);
        assertEquals(1L, mex.getId());
        assertEquals("MEX", mex.getClave());
        assertEquals("MEXICANA", mex.getNombre());
        assertTrue(mex.getActivo());
    }
}

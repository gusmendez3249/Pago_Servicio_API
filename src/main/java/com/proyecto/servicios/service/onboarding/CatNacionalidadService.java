package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.CatNacionalidadResponse;
import com.proyecto.servicios.repositorys.onboarding.CatNacionalidadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CatNacionalidadService {

    private final CatNacionalidadRepository catNacionalidadRepository;

    /** Nacionalidades activas convertidas a DTO de respuesta. */
    public List<CatNacionalidadResponse> obtenerActivas() {
        return catNacionalidadRepository.findByActivoTrue().stream()
                .map(n -> CatNacionalidadResponse.builder()
                        .id(n.getId())
                        .clave(n.getClave())
                        .nombre(n.getNombre())
                        .activo(n.getActivo())
                        .build())
                .toList();
    }
}

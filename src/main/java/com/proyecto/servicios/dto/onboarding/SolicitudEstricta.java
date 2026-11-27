package com.proyecto.servicios.dto.onboarding;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.proyecto.servicios.validation.SinCamposDesconocidos;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Base de las solicitudes que no admiten campos extra: en lugar de ignorarlos en silencio (comportamiento por
 * defecto de Jackson), se recolectan y {@link SinCamposDesconocidos} responde 400 indicando cuáles sobran.
 */
@SinCamposDesconocidos
public abstract class SolicitudEstricta {

    private static final int MAX_REPORTADOS = 10;

    @JsonIgnore
    private final Set<String> camposDesconocidos = new TreeSet<>();

    @JsonAnySetter
    public void registrarCampoDesconocido(String nombre, Object valor) {
        if (camposDesconocidos.size() < MAX_REPORTADOS) {
            camposDesconocidos.add(nombre);
        }
    }

    @JsonIgnore
    public Set<String> camposDesconocidos() {
        return Collections.unmodifiableSet(camposDesconocidos);
    }
}

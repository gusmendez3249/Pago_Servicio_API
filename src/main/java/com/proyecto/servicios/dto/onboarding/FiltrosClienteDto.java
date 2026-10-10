package com.proyecto.servicios.dto.onboarding;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.proyecto.servicios.validation.TextoEstrictoDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Filtros de la consulta de clientes (bandera 4). Solo se admiten campos llave que NO cambian
 * (no se pueden modificar después del registro). Todos son opcionales; sin ninguno se devuelven
 * todos los clientes. Un campo desconocido (p. ej. "nombre") se rechaza con 400 para que un error de
 * escritura no devuelva toda la tabla sin avisar.
 *
 * Nota: Spring Boot desactiva FAIL_ON_UNKNOWN_PROPERTIES y @JsonIgnoreProperties(ignoreUnknown = false)
 * no lo puede sobrescribir, por eso los campos desconocidos se registran con @JsonAnySetter y se
 * rechazan en la validación.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiltrosClienteDto {

    @Schema(description = "ID del cliente (coincidencia exacta)", example = "1")
    @Positive(message = "El filtro 'clienteId' debe ser un número entero mayor a 0")
    private Long clienteId;

    @Schema(description = "CURP completa o parte de ella (contiene, sin distinguir mayúsculas). Entre comillas y sin espacios", example = "MERG95")
    @JsonDeserialize(using = TextoEstrictoDeserializer.class)
    @Pattern(regexp = "^[A-Za-z0-9]{1,18}$",
            message = "El filtro 'curp' debe ir entre comillas, no estar en blanco y tener de 1 a 18 letras o números")
    private String curp;

    @Schema(description = "RFC completo o parte de él (contiene, sin distinguir mayúsculas). Entre comillas y sin espacios", example = "MERG9505")
    @JsonDeserialize(using = TextoEstrictoDeserializer.class)
    @Pattern(regexp = "^[A-Za-z0-9Ññ&]{1,13}$",
            message = "El filtro 'rfc' debe ir entre comillas, no estar en blanco y tener de 1 a 13 letras o números")
    private String rfc;

    @Schema(description = "Número de cuenta completo o parte de él (contiene). Entre comillas, solo dígitos", example = "3651")
    @JsonDeserialize(using = TextoEstrictoDeserializer.class)
    @Pattern(regexp = "^[0-9]{1,13}$",
            message = "El filtro 'numeroCuenta' debe ir entre comillas, no estar en blanco y tener de 1 a 13 dígitos")
    private String numeroCuenta;

    @JsonIgnore
    @Schema(hidden = true)
    @Builder.Default
    private Set<String> camposDesconocidos = new LinkedHashSet<>();

    @JsonAnySetter
    public void registrarCampoDesconocido(String nombre, Object valor) {
        camposDesconocidos.add(nombre != null && nombre.length() > 40 ? nombre.substring(0, 40) + "…" : nombre);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "El objeto 'filtros' solo admite los campos llave: clienteId, curp, rfc y numeroCuenta")
    public boolean isSinCamposDesconocidos() {
        return camposDesconocidos.isEmpty();
    }

    /** true si no se envió ningún filtro. */
    public boolean estaVacio() {
        return clienteId == null && curp == null && rfc == null && numeroCuenta == null;
    }
}

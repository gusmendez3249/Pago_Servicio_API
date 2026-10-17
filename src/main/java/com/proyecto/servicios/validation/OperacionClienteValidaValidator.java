package com.proyecto.servicios.validation;

import com.proyecto.servicios.dto.onboarding.FiltrosClienteDto;
import com.proyecto.servicios.dto.onboarding.LayawayClienteRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class OperacionClienteValidaValidator implements ConstraintValidator<OperacionClienteValida, LayawayClienteRequest> {

    @Override
    public boolean isValid(LayawayClienteRequest request, ConstraintValidatorContext context) {
        Integer bandera = request == null ? null : request.getBandera();
        if (bandera == null || bandera < 1 || bandera > 4) {
            return true; // la bandera ausente o fuera de rango ya la reportan @NotNull / @Min / @Max
        }
        Errores errores = new Errores(context);
        String operacion = switch (bandera) {
            case 1 -> "INSERTAR (bandera = 1)";
            case 2 -> "ACTUALIZAR (bandera = 2)";
            case 3 -> "ELIMINAR / BAJA LÓGICA (bandera = 3)";
            default -> "CONSULTAR (bandera = 4)";
        };

        // El ID interno nunca se usa: se rechaza en lugar de ignorarlo (en una consulta devolvería todos los clientes)
        Long clienteId = request.getClienteId();
        if (clienteId != null && !(bandera == 1 && clienteId == 0)) {
            errores.agregar("clienteId", bandera == 1
                    ? "Para la operación INSERTAR (bandera = 1), el clienteId debe ser nulo o 0. No se permite enviar un ID preexistente o arbitrario."
                    : "El clienteId ya no se utiliza. Para la operación " + operacion + " identifique al cliente con el objeto 'filtros' (curp, rfc o numeroCuenta).");
        }

        switch (bandera) {
            case 1 -> {
                if (request.getFiltros() != null) {
                    errores.agregar("filtros", "Para la operación INSERTAR (bandera = 1) no se usa el objeto 'filtros'.");
                }
                obligatoria(errores, request.getDatosPersonales(), "datosPersonales", "Los datos personales son obligatorios para el registro (bandera = 1).");
                obligatoria(errores, request.getDatosContacto(), "datosContacto", "Los datos de contacto son obligatorios para el registro (bandera = 1).");
                obligatoria(errores, request.getDomicilio(), "domicilio", "El domicilio es obligatorio para el registro (bandera = 1).");
                obligatoria(errores, request.getInformacionLaboral(), "informacionLaboral", "La información laboral es obligatoria para el registro (bandera = 1).");
            }
            case 2, 3 -> validarLlaves(errores, request.getFiltros(), operacion);
            default -> {
                // Consulta: solo 'filtros'. Ignorar otra sección devolvería todos los clientes como si no hubiera filtro
                String mensaje = "Para la consulta (bandera = 4) envíe únicamente el objeto 'filtros' (curp, rfc, numeroCuenta) o ninguno para obtener todos los clientes.";
                prohibida(errores, request.getDatosPersonales(), "datosPersonales", mensaje);
                prohibida(errores, request.getDatosContacto(), "datosContacto", mensaje);
                prohibida(errores, request.getDomicilio(), "domicilio", mensaje);
                prohibida(errores, request.getInformacionLaboral(), "informacionLaboral", mensaje);
                prohibida(errores, request.getLoginCredenciales(), "loginCredenciales", mensaje);
            }
        }
        return errores.ninguno();
    }

    /** Actualizar o dar de baja exige identificar a UN cliente con el valor COMPLETO de al menos una llave. */
    private static void validarLlaves(Errores errores, FiltrosClienteDto filtros, String operacion) {
        if (filtros == null || filtros.estaVacio()) {
            errores.agregar("filtros", "Para la operación " + operacion + " identifique al cliente con el objeto 'filtros' (curp, rfc o numeroCuenta).");
            return;
        }
        if ((filtros.getCurp() != null && filtros.getCurp().length() != 18)
                || (filtros.getRfc() != null && filtros.getRfc().length() < 12)
                || (filtros.getNumeroCuenta() != null && filtros.getNumeroCuenta().length() < 12)) {
            errores.agregar("filtros", "Para la operación " + operacion + " use el valor COMPLETO de la llave: curp de 18 caracteres, rfc de 12 o 13 y numeroCuenta de 12 dígitos.");
        }
    }

    private static void obligatoria(Errores errores, Object seccion, String campo, String mensaje) {
        if (seccion == null) {
            errores.agregar(campo, mensaje);
        }
    }

    private static void prohibida(Errores errores, Object seccion, String campo, String mensaje) {
        if (seccion != null) {
            errores.agregar(campo, mensaje);
        }
    }

    /** Acumula los incumplimientos, cada uno asociado al campo que lo provoca. */
    private static final class Errores {
        private final ConstraintValidatorContext context;
        private boolean hay;

        Errores(ConstraintValidatorContext context) {
            this.context = context;
        }

        void agregar(String campo, String mensaje) {
            if (!hay) {
                context.disableDefaultConstraintViolation();
                hay = true;
            }
            context.buildConstraintViolationWithTemplate(mensaje).addPropertyNode(campo).addConstraintViolation();
        }

        boolean ninguno() {
            return !hay;
        }
    }
}

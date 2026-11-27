package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reemplaza al BasicErrorController de Spring Boot: los errores que no pasan por GlobalExceptionHandler
 * (p. ej. los del contenedor) responden con el mismo formato GenericResponse, sin timestamp, ruta ni traza,
 * y una visita directa a /error responde 404 en lugar de {"status":999}.
 */
@Hidden
@RestController
public class ErrorApiController implements ErrorController {

    @RequestMapping(value = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericResponse<Void>> error(HttpServletRequest request) {
        Object codigo = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = codigo instanceof Integer valor ? HttpStatus.resolve(valor) : null;
        if (status == null || !status.isError()) {
            status = HttpStatus.NOT_FOUND;
        }
        return ResponseEntity.status(status).body(GenericResponse.error(status.value(), mensaje(status)));
    }

    static String mensaje(HttpStatus status) {
        if (status.is5xxServerError()) {
            return "Ocurrió un error interno. Intente más tarde.";
        }
        return status == HttpStatus.NOT_FOUND
                ? "El recurso solicitado no existe."
                : "No fue posible procesar la petición.";
    }
}

package com.proyecto.servicios.exception;

import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.model.GenericResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OnboardingException.class)
    public ResponseEntity<GenericResponse<Void>> handleOnboardingException(
            OnboardingException exception) {

        return ResponseEntity
                .status(exception.getStatus())
                .body(GenericResponse.error(
                        exception.getCodigo(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GenericResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception) {

        String errores = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(GenericResponse.error(
                        400,
                        "Error de validación: " + errores
                ));
    }

    @ExceptionHandler(IntegrationException.class)
    public ResponseEntity<GenericResponse<Void>> handleIntegration(
            IntegrationException exception) {

        ErrorCode errorCode = exception.getErrorCode();
        HttpStatus status = obtenerStatus(errorCode);

        return ResponseEntity
                .status(status)
                .body(GenericResponse.error(
                        errorCode.getCode(),
                        errorCode.getMessage()
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<GenericResponse<Void>> handleIllegalArgument(
            IllegalArgumentException exception) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(GenericResponse.error(
                        400,
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse<Void>> handleGeneral(
            Exception exception) {

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(GenericResponse.error(
                        ErrorCode.INTERNAL_ERROR.getCode(),
                        ErrorCode.INTERNAL_ERROR.getMessage() + ": " + exception.getMessage()
                ));
    }

    private HttpStatus obtenerStatus(ErrorCode errorCode) {
        if (errorCode == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return switch (errorCode) {
            case AUTHENTICATION_ERROR -> HttpStatus.UNAUTHORIZED;
            case TIMEOUT_ERROR -> HttpStatus.GATEWAY_TIMEOUT;
            case HTTP_ERROR, COMMUNICATION_ERROR, INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case CONFIGURATION_ERROR, INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case NO_DATA -> HttpStatus.NOT_FOUND;
            case SUCCESS -> HttpStatus.OK;
        };
    }
}
package com.proyecto.servicios.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.proyecto.servicios.config.RequestSizeLimitFilter;
import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.model.GenericResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
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
    public ResponseEntity<GenericResponse> dtoValidation(MethodArgumentNotValidException errors) {
        GenericResponse genericResponse = new GenericResponse();
        Map<String, String> listError = new HashMap<>();
        for (FieldError fieldError : errors.getFieldErrors()) {
            listError.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        genericResponse.setCodigo(1);
        genericResponse.setMensaje("Datos no validos" + listError);
        return new ResponseEntity<>(genericResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<GenericResponse<Void>> handleConstraintViolation(
            ConstraintViolationException exception) {

        String detalle = exception.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));

        return build(HttpStatus.BAD_REQUEST, 400, "Datos no validos: " + detalle);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GenericResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception) {

        // Spring envuelve los IOException de lectura ("I/O error while reading input message")
        if (exception.getCause() instanceof RequestSizeLimitFilter.BodyTooLargeException tooLarge) {
            return handleBodyTooLarge(tooLarge);
        }

        String mensajeError = "Error de formato o tipo de dato en la petición JSON. Verifique que los datos sean correctos.";
        Throwable cause = exception.getCause();

        // MismatchedInputException cubre InvalidFormatException (ej. "abc" -> Integer) y también
        // las coerciones rechazadas (ej. "1" -> Integer o 1.5 -> Integer) al estar desactivadas en application.properties.
        if (cause instanceof MismatchedInputException mie) {
            String fieldName = mie.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));

            Class<?> targetType = mie.getTargetType();
            if (targetType != null && targetType.equals(java.time.LocalDate.class)) {
                mensajeError = "Error de formato de fecha: La fecha de nacimiento debe enviarse estrictamente en formato año-mes-día (yyyy-MM-dd), por ejemplo: 1995-05-15.";
            } else if (targetType != null && (targetType.equals(Integer.class) || targetType.equals(int.class) || targetType.equals(Long.class) || targetType.equals(long.class))) {
                if (fieldName.toLowerCase().contains("telefono")) {
                    mensajeError = "Error de tipo de dato JSON: Los números de teléfono deben enviarse como números enteros numéricos sin comillas (ejemplo: 4181234567) y NO como cadenas de texto (String).";
                } else {
                    mensajeError = "Error de tipo de dato JSON: El campo '" + (fieldName.isEmpty() ? "bandera" : fieldName) + "' debe ser un número entero numérico (ej. 1, 2 o 3) y NO una cadena de texto (String) ni un decimal.";
                }
            } else if (!fieldName.isEmpty()) {
                mensajeError = "Error de tipo de dato en el campo '" + fieldName + "': Se esperaba el tipo " + (targetType != null ? targetType.getSimpleName() : "válido") + ".";
            }
        } else if (exception.getMessage() != null && exception.getMessage().contains("LocalDate")) {
            mensajeError = "Error de formato de fecha: La fecha de nacimiento debe enviarse estrictamente en formato año-mes-día (yyyy-MM-dd), por ejemplo: 1995-05-15.";
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(GenericResponse.error(
                        400,
                        mensajeError
                ));
    }

    // ---------- Errores de la petición HTTP (antes caían en el 500 genérico) ----------

    /** Cuerpo enviado con Transfer-Encoding: chunked que supera el límite (ver RequestSizeLimitFilter). */
    @ExceptionHandler(RequestSizeLimitFilter.BodyTooLargeException.class)
    public ResponseEntity<GenericResponse<Void>> handleBodyTooLarge(
            RequestSizeLimitFilter.BodyTooLargeException exception) {

        return build(HttpStatus.PAYLOAD_TOO_LARGE, 413, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<GenericResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {

        String tipo = exception.getRequiredType() != null ? exception.getRequiredType().getSimpleName() : "válido";
        return build(HttpStatus.BAD_REQUEST, 400,
                "El parámetro '" + exception.getName() + "' tiene un valor inválido. Se esperaba un valor de tipo " + tipo + ".");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<GenericResponse<Void>> handleMissingParam(
            MissingServletRequestParameterException exception) {

        return build(HttpStatus.BAD_REQUEST, 400,
                "Falta el parámetro obligatorio '" + exception.getParameterName() + "'.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<GenericResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception) {

        HttpHeaders headers = new HttpHeaders();
        if (exception.getSupportedHttpMethods() != null) {
            headers.setAllow(exception.getSupportedHttpMethods());
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(headers)
                .body(GenericResponse.error(405,
                        "Método HTTP '" + exception.getMethod() + "' no permitido para este endpoint."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<GenericResponse<Void>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception) {

        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, 415,
                "Content-Type no soportado. Envíe la petición con 'Content-Type: application/json'.");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<GenericResponse<Void>> handleMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException exception) {

        return build(HttpStatus.NOT_ACCEPTABLE, 406,
                "El encabezado 'Accept' no es compatible. Este servicio responde en 'application/json'.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<GenericResponse<Void>> handleNoResource(
            NoResourceFoundException exception) {

        return build(HttpStatus.NOT_FOUND, 404,
                "El recurso '/" + exception.getResourcePath() + "' no existe.");
    }

    // ---------- Errores de base de datos ----------

    /**
     * Cubre las condiciones de carrera: dos peticiones simultáneas pasan el "existsBy..." y la
     * segunda revienta en el constraint UNIQUE de PostgreSQL. Se distingue por SQLState.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<GenericResponse<Void>> handleDataIntegrity(
            DataIntegrityViolationException exception) {

        Throwable root = NestedExceptionUtils.getMostSpecificCause(exception);
        String sqlState = root instanceof SQLException sqlEx ? sqlEx.getSQLState() : null;
        String detalle = root.getMessage() != null ? root.getMessage().toLowerCase() : "";

        if ("23505".equals(sqlState)) {
            return build(HttpStatus.CONFLICT, 409, mensajeDuplicado(detalle));
        }
        if ("22001".equals(sqlState)) {
            return build(HttpStatus.BAD_REQUEST, 400,
                    "Uno de los campos excede la longitud máxima permitida.");
        }
        if ("23502".equals(sqlState)) {
            return build(HttpStatus.BAD_REQUEST, 400,
                    "Falta un campo obligatorio en la petición.");
        }

        log.warn("Violación de integridad de datos no clasificada (SQLState={}): {}", sqlState, root.getMessage());
        return build(HttpStatus.CONFLICT, 409,
                "La operación viola una restricción de integridad de datos.");
    }

    /**
     * Pool de conexiones agotado o BD no disponible bajo carga: 503 + Retry-After
     * para que el cliente (JMeter) sepa que es saturación temporal y no un bug.
     */
    @ExceptionHandler({
            CannotCreateTransactionException.class,
            CannotGetJdbcConnectionException.class,
            TransientDataAccessResourceException.class,
            QueryTimeoutException.class
    })
    public ResponseEntity<GenericResponse<Void>> handleDatabaseUnavailable(Exception exception) {
        log.warn("Base de datos saturada o no disponible: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "2")
                .body(GenericResponse.error(503,
                        "El servicio está temporalmente saturado. Intente nuevamente en unos segundos."));
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

        // Se registra el detalle en el log, pero NO se expone al cliente (evita filtrar SQL, clases, rutas, etc.)
        log.error("Error no controlado", exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(GenericResponse.error(
                        ErrorCode.INTERNAL_ERROR.getCode(),
                        ErrorCode.INTERNAL_ERROR.getMessage()
                ));
    }

    private ResponseEntity<GenericResponse<Void>> build(HttpStatus status, int codigo, String mensaje) {
        return ResponseEntity.status(status).body(GenericResponse.error(codigo, mensaje));
    }

    private String mensajeDuplicado(String detalle) {
        if (detalle.contains("curp")) {
            return "Ya existe un cliente registrado con esa CURP.";
        }
        if (detalle.contains("rfc")) {
            return "Ya existe un cliente registrado con ese RFC.";
        }
        if (detalle.contains("correo")) {
            return "Ya existe un cliente registrado con ese correo.";
        }
        if (detalle.contains("username")) {
            return "El nombre de usuario ya se encuentra en uso.";
        }
        return "El registro ya existe.";
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
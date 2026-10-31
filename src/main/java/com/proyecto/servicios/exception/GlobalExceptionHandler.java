package com.proyecto.servicios.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.proyecto.servicios.config.RequestSizeLimitFilter;
import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.model.GenericResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
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
import org.springframework.validation.ObjectError;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Código de la respuesta cuando falla la validación de un DTO (como en el ValidadorCampos de referencia). */
    public static final int CODIGO_VALIDACION = 1;

    // ---------- Validación de los DTO (@Valid + Bean Validation) ----------

    /**
     * En caso de que alguna validación de un DTO no se cumpla se lanza MethodArgumentNotValidException:
     * se responde 400 con {@code codigo: 1}, el mensaje "Datos no validos{campo=motivo, ...}" y en {@code data}
     * la lista de campos inválidos con su mensaje.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GenericResponse<List<Map<String, String>>>> dtoValidation(MethodArgumentNotValidException errors) {
        Map<String, String> campos = new TreeMap<>();
        for (FieldError fieldError : errors.getFieldErrors()) {
            campos.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        for (ObjectError error : errors.getGlobalErrors()) {
            campos.put(error.getObjectName(), error.getDefaultMessage());
        }
        return respuestaValidacion(campos);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<GenericResponse<List<Map<String, String>>>> handleConstraintViolation(
            ConstraintViolationException exception) {

        Map<String, String> campos = new TreeMap<>();
        exception.getConstraintViolations().forEach(v -> campos.put(v.getPropertyPath().toString(), v.getMessage()));
        return respuestaValidacion(campos);
    }

    private ResponseEntity<GenericResponse<List<Map<String, String>>>> respuestaValidacion(Map<String, String> campos) {
        List<Map<String, String>> detalle = new ArrayList<>();
        campos.forEach((campo, mensaje) -> detalle.add(Map.of("campo", campo, "mensaje", String.valueOf(mensaje))));
        return ResponseEntity.badRequest().body(GenericResponse.error(CODIGO_VALIDACION, "Datos no validos" + campos, detalle));
    }

    // ---------- Excepciones de negocio (el código coincide con el estado HTTP) ----------

    @ExceptionHandler(ClienteNoEncontradoException.class)
    public ResponseEntity<GenericResponse<Void>> handleClienteNoEncontrado(ClienteNoEncontradoException exception) {
        return negocio(exception);
    }

    @ExceptionHandler(NacionalidadNoValidaException.class)
    public ResponseEntity<GenericResponse<Void>> handleNacionalidadNoValida(NacionalidadNoValidaException exception) {
        return negocio(exception);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<GenericResponse<Void>> handleRecursoNoEncontrado(RecursoNoEncontradoException exception) {
        return negocio(exception);
    }

    @ExceptionHandler(RegistroDuplicadoException.class)
    public ResponseEntity<GenericResponse<Void>> handleRegistroDuplicado(RegistroDuplicadoException exception) {
        return negocio(exception);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<GenericResponse<Void>> handleReglaNegocio(ReglaNegocioException exception) {
        return negocio(exception);
    }

    /** RFC 9110: toda respuesta 401 debe indicar el esquema de autenticación esperado. */
    @ExceptionHandler(NoAutenticadoException.class)
    public ResponseEntity<GenericResponse<Void>> handleNoAutenticado(NoAutenticadoException exception) {
        return ResponseEntity.status(exception.getStatus())
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"onboarding\"")
                .body(GenericResponse.error(exception.getCodigo(), exception.getMessage()));
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<GenericResponse<Void>> handleAccesoDenegado(AccesoDenegadoException exception) {
        return negocio(exception);
    }

    @ExceptionHandler(ClienteInactivoException.class)
    public ResponseEntity<GenericResponse<Void>> handleClienteInactivo(ClienteInactivoException exception) {
        return negocio(exception);
    }

    @ExceptionHandler(CuentaBloqueadaException.class)
    public ResponseEntity<GenericResponse<Void>> handleCuentaBloqueada(CuentaBloqueadaException exception) {
        return negocio(exception);
    }

    /** Cualquier otra excepción de negocio que se agregue en el futuro. */
    @ExceptionHandler(OnboardingException.class)
    public ResponseEntity<GenericResponse<Void>> handleOnboardingException(OnboardingException exception) {
        return negocio(exception);
    }

    private ResponseEntity<GenericResponse<Void>> negocio(OnboardingException exception) {
        return build(exception.getStatus(), exception.getCodigo(), exception.getMessage());
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
            if (targetType != null && targetType.equals(String.class)) {
                mensajeError = "El campo '" + fieldName + "' debe enviarse como texto entre comillas (por ejemplo \"valor\"), no como número, booleano, arreglo ni objeto.";
            } else if (targetType != null && targetType.equals(java.time.LocalDate.class)) {
                mensajeError = "Error de formato de fecha: La fecha de nacimiento debe enviarse estrictamente en formato año-mes-día (yyyy-MM-dd), por ejemplo: 1995-05-15.";
            } else if (targetType != null && (targetType.equals(Integer.class) || targetType.equals(int.class) || targetType.equals(Long.class) || targetType.equals(long.class))) {
                if (fieldName.toLowerCase().contains("telefono")) {
                    mensajeError = "Error de tipo de dato JSON: Los números de teléfono deben enviarse como números enteros numéricos sin comillas (ejemplo: 4181234567) y NO como cadenas de texto (String).";
                } else {
                    mensajeError = "Error de tipo de dato JSON: El campo '" + (fieldName.isEmpty() ? "bandera" : fieldName) + "' debe ser un número entero numérico (ej. 1, 2, 3 o 4) y NO una cadena de texto (String) ni un decimal.";
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

    /**
     * Cualquier otro fallo de acceso a datos durante una consulta o una operación (los casos de BD saturada o
     * duplicados tienen su propio manejador). Se registra el detalle y se responde un mensaje descriptivo sin
     * exponer SQL ni nombres internos.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<GenericResponse<Void>> handleDataAccess(DataAccessException exception) {
        log.error("Error de acceso a datos", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, 500,
                "Ocurrió un error al acceder a la información. Intente nuevamente; si el problema continúa, contacte al administrador.");
    }

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

    /** El mensaje original puede contener detalles internos: solo se registra en el log. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<GenericResponse<Void>> handleIllegalArgument(
            IllegalArgumentException exception) {

        log.warn("Argumento no válido: {}", exception.getMessage());
        return build(HttpStatus.BAD_REQUEST, 400,
                "La petición contiene un valor que no se puede procesar. Revise los datos enviados e intente nuevamente.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse<Void>> handleGeneral(
            Exception exception) {

        // Se registra el detalle en el log, pero NO se expone al cliente (evita filtrar SQL, clases, rutas, etc.)
        log.error("Error no controlado", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, 500,
                "Ocurrió un error inesperado al procesar la solicitud. Intente nuevamente; si el problema continúa, contacte al administrador.");
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
            // Falla de las credenciales del servidor ante GestoPago, no de quien llama: no es un 401 del cliente
            case AUTHENTICATION_ERROR, HTTP_ERROR, COMMUNICATION_ERROR, INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case TIMEOUT_ERROR -> HttpStatus.GATEWAY_TIMEOUT;
            case CONFIGURATION_ERROR, INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case NO_DATA -> HttpStatus.NOT_FOUND;
            case SUCCESS -> HttpStatus.OK;
        };
    }
}
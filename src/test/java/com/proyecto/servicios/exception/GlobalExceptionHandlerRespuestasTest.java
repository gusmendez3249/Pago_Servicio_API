package com.proyecto.servicios.exception;

import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.model.GenericResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Cada error responde su código y un mensaje sin SQL, clases ni datos personales. */
class GlobalExceptionHandlerRespuestasTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static void sinDetallesInternos(ResponseEntity<? extends GenericResponse<?>> r) {
        String mensaje = r.getBody().getMensaje();
        assertFalse(mensaje.matches("(?s).*(Exception|java\\.|SQL|BigDecimal|String\\)|Key \\().*"), mensaje);
    }

    @Test
    void tipoDeParametroIncorrectoNoNombraTiposDeJava() {
        var e = new MethodArgumentTypeMismatchException("abc", Long.class, "pagina", (MethodParameter) null, null);
        var r = handler.handleTypeMismatch(e);
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("El parámetro 'pagina' tiene un valor no válido.", r.getBody().getMensaje());
        sinDetallesInternos(r);
    }

    @Test
    void erroresDeProtocoloHttp() {
        assertEquals(400, handler.handleMissingParam(new MissingServletRequestParameterException("q", "String"))
                .getStatusCode().value());

        var metodo = handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("DELETE", List.of("POST")));
        assertEquals(405, metodo.getStatusCode().value());
        assertEquals(Set.of(HttpMethod.POST), metodo.getHeaders().getAllow());
        assertEquals(405, handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PUT"))
                .getStatusCode().value());

        assertEquals(415, handler.handleMediaTypeNotSupported(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)))
                .getStatusCode().value());
        assertEquals(406, handler.handleMediaTypeNotAcceptable(new HttpMediaTypeNotAcceptableException("xml"))
                .getStatusCode().value());
        var noExiste = handler.handleNoResource(new NoResourceFoundException(HttpMethod.GET, "x"));
        assertEquals(404, noExiste.getStatusCode().value());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "'duplicate key value violates unique constraint \"uk_curp\" Key (curp)=(X)', Ya existe un cliente registrado con esa CURP.",
            "'Key (rfc)=(X) already exists', Ya existe un cliente registrado con ese RFC.",
            "'Key (correo)=(x@x.com) already exists', Ya existe un cliente registrado con ese correo.",
            "'Key (username)=(ana) already exists', El nombre de usuario ya se encuentra en uso.",
            "'Key (otro)=(1) already exists', El registro ya existe."
    })
    void duplicadosRespondenConflictoSinElValor(String detalle, String esperado) {
        var r = handler.handleDataIntegrity(new DataIntegrityViolationException("x", new SQLException(detalle, "23505")));
        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals(esperado, r.getBody().getMensaje());
        sinDetallesInternos(r);
    }

    @ParameterizedTest
    @CsvSource({"22001, 400", "23502, 400", "23503, 409"})
    void otrasViolacionesDeIntegridad(String sqlState, int estado) {
        var r = handler.handleDataIntegrity(new DataIntegrityViolationException("x", new SQLException("Key (curp)=(X)", sqlState)));
        assertEquals(estado, r.getStatusCode().value());
        sinDetallesInternos(r);
    }

    @Test
    void baseSaturadaResponde503ConRetryAfter() {
        var r = handler.handleDatabaseUnavailable(new QueryTimeoutException("lenta"));
        assertEquals(503, r.getStatusCode().value());
        assertEquals("2", r.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void erroresDeIntegracionConGestoPago(ErrorCode codigo) {
        var r = handler.handleIntegration(new IntegrationException(codigo));
        HttpStatus esperado = switch (codigo) {
            case AUTHENTICATION_ERROR, HTTP_ERROR, COMMUNICATION_ERROR, INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case TIMEOUT_ERROR -> HttpStatus.GATEWAY_TIMEOUT;
            case CONFIGURATION_ERROR, INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case NO_DATA -> HttpStatus.NOT_FOUND;
            case SUCCESS -> HttpStatus.OK;
        };
        assertEquals(esperado, r.getStatusCode());
        assertEquals(codigo.getCode(), r.getBody().getCodigo());
    }

    @Test
    void integracionSinCodigoResponde500() {
        assertEquals(500, handler.handleIntegration(new IntegrationException((ErrorCode) null)).getStatusCode().value());
    }
}

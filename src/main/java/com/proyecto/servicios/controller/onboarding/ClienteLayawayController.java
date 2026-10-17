package com.proyecto.servicios.controller.onboarding;

import com.proyecto.servicios.dto.onboarding.LayawayClienteRequest;
import com.proyecto.servicios.dto.onboarding.LayawayClienteResponse;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.service.onboarding.ClienteLayawayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/layaway")
@RequiredArgsConstructor
@Tag(name = "Layaway Clientes", description = "Endpoint unificado para gestión de clientes mediante banderas de operación")
public class ClienteLayawayController {

    private final ClienteLayawayService clienteLayawayService;

    @PostMapping(value = "/cliente", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Operación unificada de clientes (Bandera 1 = Insertar, 2 = Actualizar, 3 = Eliminar/Baja Lógica, 4 = Consultar)",
            description = """
                    **Bandera 1 – Registrar** (público, no requiere token): crea el cliente, su domicilio, su cuenta bancaria automática y sus credenciales.

                    **Bandera 2 – Actualizar** (requiere token): modifica datos personales, de contacto, domicilio, información laboral y contraseña. \
                    Se identifica al cliente con `filtros` (valor **completo** de `curp`, `rfc` o `numeroCuenta`); nunca por ID. CURP y RFC no se pueden modificar.

                    **Bandera 3 – Baja lógica** (requiere token de **ejecutivo**): desactiva al cliente y sus cuentas sin borrar información. Se identifica con `filtros`.

                    **Bandera 4 – Consultar** (requiere token): use solo `filtros` (`curp`, `rfc`, `numeroCuenta`; los textos van entre comillas y no pueden estar en blanco). \
                    Sin filtros devuelve todos los clientes (hasta 1000).

                    **Autorización a nivel de recurso:** con rol `CLIENTE` solo se puede consultar o actualizar el **propio** registro (cualquier otra llave responde 404, \
                    sin revelar si existe); con rol `EJECUTIVO` se puede operar sobre cualquier cliente. El token se obtiene en `POST /api/v1/auth/login`.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Operación realizada con éxito (banderas 2, 3 o 4)", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "201", description = "Cliente registrado exitosamente (bandera 1)", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "400", description = """
                    Bad Request. Escenarios: datos inválidos según las anotaciones de Bean Validation del DTO (`codigo: 1`, mensaje `Datos no validos{campo=motivo}` y el detalle por campo en `data`; incluye la bandera ausente o fuera de 1–4, las secciones obligatorias del registro y las llaves de las banderas 2 y 3); los demás casos responden `codigo: 400`: JSON mal formado o tipo incorrecto (p. ej. bandera como texto o un filtro sin comillas); \
                    filtro en blanco, con caracteres no permitidos o que no es una llave; llave incompleta o ausente en las banderas 2 y 3; \
                    `clienteId` enviado (ya no se utiliza); intento de modificar CURP, RFC o usuario; contraseña que incumple la política.""",
                    content = @Content(schema = @Schema(implementation = GenericResponse.class),
                            examples = @ExampleObject(name = "Validación", value = "{\"codigo\":1,\"mensaje\":\"Datos no validos{datosPersonales.sexo=El sexo debe ser exactamente uno de los valores del catálogo: MASCULINO, FEMENINO u OTRO}\",\"data\":[{\"campo\":\"datosPersonales.sexo\",\"mensaje\":\"El sexo debe ser exactamente uno de los valores del catálogo: MASCULINO, FEMENINO u OTRO\"}]}"))),
            @ApiResponse(responseCode = "401", description = "Unauthorized (banderas 2, 3 y 4). Falta el encabezado Authorization, el esquema no es Bearer, el token es inválido o la sesión expiró (5 minutos de inactividad). La respuesta incluye `WWW-Authenticate: Bearer`.",
                    content = @Content(schema = @Schema(implementation = GenericResponse.class),
                            examples = @ExampleObject(value = "{\"codigo\":401,\"mensaje\":\"Se requiere autenticación: envíe el encabezado 'Authorization: Bearer <token>' obtenido en POST /api/v1/auth/login.\",\"data\":null}"))),
            @ApiResponse(responseCode = "403", description = "Forbidden. Un usuario con rol CLIENTE intenta dar de baja (solo ejecutivos); se intenta actualizar un cliente dado de baja; o el cliente asociado al token está inactivo.",
                    content = @Content(schema = @Schema(implementation = GenericResponse.class),
                            examples = @ExampleObject(value = "{\"codigo\":403,\"mensaje\":\"Solo un ejecutivo puede dar de baja clientes.\",\"data\":null}"))),
            @ApiResponse(responseCode = "404", description = "Not Found. Ningún cliente coincide con las llaves o filtros indicados **entre los registros a los que se tiene acceso** (incluye el caso de un cliente que consulta el registro de otro), o la nacionalidad no existe en el catálogo (bandera 1).",
                    content = @Content(schema = @Schema(implementation = GenericResponse.class),
                            examples = @ExampleObject(value = "{\"codigo\":404,\"mensaje\":\"No se encontró ningún cliente con los filtros indicados entre los registros a los que tiene acceso.\",\"data\":null}"))),
            @ApiResponse(responseCode = "409", description = "Conflict. CURP, RFC, correo o usuario ya registrados (bandera 1), o el nuevo correo ya pertenece a otro cliente (bandera 2). También cuando dos altas simultáneas chocan por la misma llave.",
                    content = @Content(schema = @Schema(implementation = GenericResponse.class),
                            examples = @ExampleObject(value = "{\"codigo\":409,\"mensaje\":\"Ya existe un cliente registrado con la CURP: MERG950515HGTNDS09\",\"data\":null}"))),
            @ApiResponse(responseCode = "413", description = "Payload Too Large: el cuerpo supera 64 KB (con o sin Content-Length)", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported Media Type: envíe `Content-Type: application/json`", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error: fallo inesperado o error al acceder a los datos. El detalle solo se registra en el log del servidor.", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "503", description = "Service Unavailable: base de datos saturada o no disponible; incluye el encabezado `Retry-After`.", content = @Content(schema = @Schema(implementation = GenericResponse.class)))
    })
    public ResponseEntity<GenericResponse<LayawayClienteResponse>> procesarCliente(
            @Valid @RequestBody LayawayClienteRequest request,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        LayawayClienteResponse response = clienteLayawayService.procesarOperacion(request, authorization);
        HttpStatus status = (request.getBandera() != null && request.getBandera() == 1) ? HttpStatus.CREATED : HttpStatus.OK;
        String mensaje = switch (request.getBandera()) {
            case 1 -> "Cliente registrado correctamente";
            case 2 -> "Cliente actualizado correctamente";
            case 4 -> "Consulta realizada correctamente";
            default -> "Cliente dado de baja correctamente";
        };

        return ResponseEntity.status(status).body(GenericResponse.success(mensaje, response));
    }
}

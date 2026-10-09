package com.proyecto.servicios.controller.onboarding;

import com.proyecto.servicios.dto.onboarding.LoginRequest;
import com.proyecto.servicios.dto.onboarding.LoginResponse;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.service.onboarding.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Endpoint de inicio de sesión general con soporte para contraseña y Face ID biométrico")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(
            summary = "Inicio de sesión de usuario (Bandera 1 = Contraseña, Bandera 2 = Biométrico Face ID)",
            description = "Permite autenticar usuarios mediante usuario/contraseña o mediante Face ID biométrico (Long). El nombre de usuario es siempre obligatorio."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Autenticación exitosa. Retorna el token de sesión y estado is_loged", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request: Error en el formato del JSON, tipo de datos inválido o bandera desconocida", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized: Usuario inexistente, contraseña incorrecta o Face ID no coincide (mismo mensaje en todos los casos para no revelar qué usuarios existen)", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden: Credenciales correctas pero el cliente se encuentra inactivo o fue dado de baja", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "423", description = "Locked: Cuenta bloqueada 15 minutos tras 5 intentos fallidos consecutivos", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error: Error interno del servidor durante la autenticación", content = @Content(schema = @Schema(implementation = GenericResponse.class)))
    })
    public ResponseEntity<GenericResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(GenericResponse.success("Inicio de sesión exitoso", response));
    }
}



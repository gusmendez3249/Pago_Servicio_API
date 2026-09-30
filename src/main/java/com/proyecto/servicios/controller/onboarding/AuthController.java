package com.proyecto.servicios.controller.onboarding;

import com.proyecto.servicios.dto.onboarding.LoginRequest;
import com.proyecto.servicios.dto.onboarding.LoginResponse;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.service.onboarding.AuthService;
import io.swagger.v3.oas.annotations.Operation;
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
    public ResponseEntity<GenericResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(GenericResponse.success(response));
    }
}

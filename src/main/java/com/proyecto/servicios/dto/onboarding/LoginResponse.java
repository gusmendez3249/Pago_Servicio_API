package com.proyecto.servicios.dto.onboarding;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String username;
    private String nombreCliente;
    private Boolean isLoggedIn;
    private LocalDateTime ultimaActividad;
    private String mensaje;

    /** Token de sesión: se envía en las peticiones protegidas como "Authorization: Bearer <token>". */
    private String token;
    private String tipoToken;
    /** Minutos de inactividad tras los cuales vence la sesión. */
    private Integer expiraEnMinutos;
    private String rol;
}

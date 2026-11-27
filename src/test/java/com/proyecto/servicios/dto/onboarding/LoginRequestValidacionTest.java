package com.proyecto.servicios.dto.onboarding;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Validaciones de POST /api/v1/auth/login ejecutadas por @Valid (incluye @LoginValido según la bandera). */
class LoginRequestValidacionTest {

    private static Validator validator;

    @BeforeAll
    static void iniciar() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static Set<String> campos(LoginRequest r) {
        return validator.validate(r).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void contrasena_ConBandera1_EsValida() {
        assertTrue(campos(LoginRequest.builder().bandera(1).username("juan_perez").password("Password123!").build()).isEmpty());
    }

    @Test
    void faceId_ConBandera2_EsValido() {
        assertTrue(campos(LoginRequest.builder().bandera(2).username("juan_perez").faceIdBiometrico(987654321L).build()).isEmpty());
    }

    @Test
    void bandera1_SinContrasena_SeReportaEnPassword() {
        for (String vacia : new String[]{null, "", "   "}) {
            Set<ConstraintViolation<LoginRequest>> v = validator.validate(
                    LoginRequest.builder().bandera(1).username("juan_perez").password(vacia).build());
            assertEquals(1, v.size());
            ConstraintViolation<LoginRequest> c = v.iterator().next();
            assertEquals("password", c.getPropertyPath().toString());
            assertTrue(c.getMessage().contains("contraseña es requerida"));
        }
    }

    @Test
    void bandera2_SinFaceId_SeReportaEnFaceIdBiometrico() {
        assertEquals(Set.of("faceIdBiometrico"), campos(LoginRequest.builder().bandera(2).username("juan_perez").password("x").build()));
    }

    @Test
    void banderaAusenteOFueraDeRango_SeReportaEnBandera() {
        assertEquals(Set.of("bandera"), campos(LoginRequest.builder().username("juan_perez").password("Password123!").build()));
        assertEquals(Set.of("bandera"), campos(LoginRequest.builder().bandera(3).username("juan_perez").password("Password123!").build()));
    }

    @Test
    void usuarioAusenteOConFormatoInvalido_SeReportaEnUsername() {
        assertTrue(campos(LoginRequest.builder().bandera(1).password("Password123!").build()).contains("username"));
        assertTrue(campos(LoginRequest.builder().bandera(1).username("a b<script>").password("Password123!").build()).contains("username"));
    }
}

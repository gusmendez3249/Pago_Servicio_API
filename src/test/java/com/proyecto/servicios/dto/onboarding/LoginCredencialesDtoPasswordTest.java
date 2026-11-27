package com.proyecto.servicios.dto.onboarding;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Política de contraseña (registro y edición): mínimo 8 caracteres, con al menos 1 mayúscula,
 * 1 minúscula, 1 número y 1 carácter especial; sin espacios.
 */
class LoginCredencialesDtoPasswordTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private static Set<String> camposInvalidos(String password) {
        LoginCredencialesDto dto = LoginCredencialesDto.builder().username("usuario.prueba").password(password).build();
        return validator.validate(dto).stream().map(ConstraintViolation::getPropertyPath)
                .map(Object::toString).collect(Collectors.toSet());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Password123!", "Abcdef1#", "Zz9_aaaa", "Cl@ve-Segura2026", "N0 espacios"})
    void contrasenaQueCumpleLaPolitica_SeAcepta(String password) {
        // "N0 espacios" lleva un espacio: se comprueba abajo que NO se acepta; aquí se excluye
        if (password.contains(" ")) {
            assertTrue(camposInvalidos(password).contains("password"));
        } else {
            assertTrue(camposInvalidos(password).isEmpty(), password);
        }
    }

    @Test
    void sinContrasena_EsValido_NoSeAsignaNinguna() {
        // Es opcional: sin contraseña, el acceso por contraseña queda deshabilitado
        assertTrue(camposInvalidos(null).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ab1!xyz",            // 7 caracteres (mínimo 8)
            "password123!",       // sin mayúscula
            "PASSWORD123!",       // sin minúscula
            "Password!!!!",       // sin número
            "Password1234",       // sin carácter especial
            "12345678",           // solo números
            "abcdefgh",           // solo minúsculas
            "Password 123!",      // con espacio
            "Pass\tword1!",       // con tabulador
            "Password123!\n",     // con salto de línea
            "",                   // vacía
            "   ",                // en blanco
            "Ññáéíóú123",         // solo letras acentuadas, sin especial ASCII
    })
    void contrasenaQueNoCumple_SeRechaza(String password) {
        assertTrue(camposInvalidos(password).contains("password"), "Debió rechazar: [" + password + "]");
    }

    @Test
    void contrasenaDemasiadoLarga_SeRechaza() {
        String larga = "Aa1!" + "x".repeat(69); // 73 caracteres
        assertTrue(camposInvalidos(larga).contains("password"));
        String limite = "Aa1!" + "x".repeat(68); // 72 caracteres
        assertTrue(camposInvalidos(limite).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(chars = {'!', '"', '#', '$', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.', '/', ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '^', '_', '`', '{', '|', '}', '~'})
    void cadaCaracterEspecialAscii_CuentaComoEspecial(char especial) {
        assertTrue(camposInvalidos("Abcdefg1" + especial).isEmpty(), "Debió aceptar el especial: " + especial);
    }
}

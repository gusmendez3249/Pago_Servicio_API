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

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Valida las reglas de DatosPersonalesDto con el validador real (Hibernate Validator). */
class DatosPersonalesDtoValidationTest {

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

    private static DatosPersonalesDto valido() {
        return DatosPersonalesDto.builder()
                .nombre("Juan")
                .apellidoPaterno("Perez")
                .apellidoMaterno("Gomez")
                .fechaNacimiento(LocalDate.of(1995, 5, 15))
                .curp("PERJ950515HDFRMN01")
                .rfc("PERJ9505151A2")
                .sexo("MASCULINO")
                .nacionalidad("MEXICANA")
                .estadoCivil("SOLTERO")
                .build();
    }

    private static Set<String> camposConError(DatosPersonalesDto dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void dtoValido_SinErrores() {
        assertTrue(camposConError(valido()).isEmpty());
    }

    // ---------- Nombres: mínimo 3 caracteres ----------

    @Test
    void nombreDe2Letras_Rechazado() {
        DatosPersonalesDto dto = valido();
        dto.setNombre("Li");
        assertTrue(camposConError(dto).contains("nombre"));
    }

    @Test
    void nombreDe3Letras_Aceptado() {
        DatosPersonalesDto dto = valido();
        dto.setNombre("Ana");
        dto.setApellidoPaterno("Paz");
        dto.setApellidoMaterno("Ruz");
        assertTrue(camposConError(dto).isEmpty());
    }

    @Test
    void segundoNombreDe2Letras_Rechazado_YVacioPermitido() {
        DatosPersonalesDto dto = valido();
        dto.setSegundoNombre("Jo");
        assertTrue(camposConError(dto).contains("segundoNombre"));
        dto.setSegundoNombre("");
        assertTrue(camposConError(dto).isEmpty());
    }

    // ---------- Catálogo de sexo ----------

    @ParameterizedTest
    @ValueSource(strings = {"MASCULINO", "FEMENINO", "OTRO"})
    void sexoDelCatalogo_Aceptado(String sexo) {
        DatosPersonalesDto dto = valido();
        dto.setSexo(sexo);
        assertFalse(camposConError(dto).contains("sexo"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"masculino", "Masculino", "femenino", "otro", "M", "H", "F", "HOMBRE", "MUJER",
            "MASCULINOS", " MASCULINO", "MASCULINO ", "MASCULINO\n", "MASCULINO|FEMENINO", ""})
    void sexoFueraDelCatalogo_Rechazado(String sexo) {
        DatosPersonalesDto dto = valido();
        dto.setSexo(sexo);
        assertTrue(camposConError(dto).contains("sexo"), "Debió rechazar: [" + sexo + "]");
    }

    // ---------- Catálogo de estado civil ----------

    @ParameterizedTest
    @ValueSource(strings = {"SOLTERO", "CASADO", "DIVORCIADO", "VIUDO", "UNION LIBRE"})
    void estadoCivilDelCatalogo_Aceptado(String estadoCivil) {
        DatosPersonalesDto dto = valido();
        dto.setEstadoCivil(estadoCivil);
        assertFalse(camposConError(dto).contains("estadoCivil"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOLTERA", "CASADA", "DIVORCIADA", "VIUDA", "UNION_LIBRE", "union libre", "soltero",
            "Casado", "COMPLICADO", "SEPARADO", "UNION  LIBRE", "UNIONLIBRE", "CASADO ", "SOLTERO|CASADO", ""})
    void estadoCivilFueraDelCatalogo_Rechazado(String estadoCivil) {
        DatosPersonalesDto dto = valido();
        dto.setEstadoCivil(estadoCivil);
        assertTrue(camposConError(dto).contains("estadoCivil"), "Debió rechazar: [" + estadoCivil + "]");
    }

    // ---------- Nacionalidad: formato (la pertenencia al catálogo se valida en BD, ver ClienteLayawayServiceTest) ----------

    @ParameterizedTest
    @ValueSource(strings = {"mexicana", "Mexicana", "mex", " MEXICANA", "MEXICANA ",
            "MEX'; DROP TABLE tb_cliente;--", "MEX%", "<b>MEX</b>", "MEX\n", ""})
    void nacionalidadConCaracteresInvalidos_Rechazada(String nacionalidad) {
        DatosPersonalesDto dto = valido();
        dto.setNacionalidad(nacionalidad);
        assertTrue(camposConError(dto).contains("nacionalidad"), "Debió rechazar: [" + nacionalidad + "]");
    }
}

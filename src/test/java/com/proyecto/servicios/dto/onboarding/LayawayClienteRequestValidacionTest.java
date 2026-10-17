package com.proyecto.servicios.dto.onboarding;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validaciones del cuerpo de POST /api/v1/layaway/cliente que ejecuta @Valid en el controlador: anotaciones de
 * Bean Validation de cada campo y las reglas que dependen de la bandera (@OperacionClienteValida).
 */
class LayawayClienteRequestValidacionTest {

    private static Validator validator;
    private LayawayClienteRequest alta;

    @BeforeAll
    static void iniciar() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @BeforeEach
    void setUp() {
        alta = LayawayClienteRequest.builder()
                .bandera(1)
                .datosPersonales(DatosPersonalesDto.builder().nombre("Juan").apellidoPaterno("Pérez").apellidoMaterno("Gómez")
                        .fechaNacimiento(LocalDate.of(1995, 5, 15)).curp("PERJ950515HDFRMN01").rfc("PERJ9505151A2")
                        .sexo("MASCULINO").nacionalidad("MEXICANA").estadoCivil("SOLTERO").build())
                .datosContacto(DatosContactoDto.builder().correo("juan.perez@email.com").telefonoMovil(5512345678L).build())
                .domicilio(DomicilioDto.builder().calle("Av. Reforma").numeroExterior("100").colonia("Centro")
                        .municipio("Cuauhtémoc").estado("CDMX").codigoPostal("06000").pais("México").build())
                .informacionLaboral(InformacionLaboralDto.builder().ocupacion("Desarrollador").empresa("Empresa X")
                        .ingresoMensual(new BigDecimal("30000.00")).build())
                .loginCredenciales(LoginCredencialesDto.builder().username("juan_perez").password("Password123!").build())
                .build();
    }

    /** campo → mensaje de cada violación. */
    private static Map<String, String> errores(Object dto) {
        Set<ConstraintViolation<Object>> v = validator.validate(dto);
        Map<String, String> m = new TreeMap<>();
        v.forEach(c -> m.put(c.getPropertyPath().toString(), c.getMessage()));
        return m;
    }

    private static FiltrosClienteDto curp(String valor) {
        return FiltrosClienteDto.builder().curp(valor).build();
    }

    // ---------- Peticiones válidas ----------

    @Test
    void altaCompleta_EsValida() {
        assertTrue(errores(alta).isEmpty(), () -> errores(alta).toString());
    }

    @Test
    void altaConClienteIdCero_EsValida() {
        alta.setClienteId(0L);
        assertTrue(errores(alta).isEmpty());
    }

    @Test
    void actualizarYBajaConLlaveCompleta_SonValidas() {
        for (int bandera : new int[]{2, 3}) {
            assertTrue(errores(LayawayClienteRequest.builder().bandera(bandera).filtros(curp("PERJ950515HDFRMN01")).build()).isEmpty());
            assertTrue(errores(LayawayClienteRequest.builder().bandera(bandera)
                    .filtros(FiltrosClienteDto.builder().numeroCuenta("300123456789").build()).build()).isEmpty());
        }
    }

    @Test
    void consultaSinFiltrosOConFiltroParcial_EsValida() {
        assertTrue(errores(LayawayClienteRequest.builder().bandera(4).build()).isEmpty());
        assertTrue(errores(LayawayClienteRequest.builder().bandera(4).filtros(curp("PERJ95")).build()).isEmpty());
    }

    // ---------- Bandera ----------

    @Test
    void banderaAusente_SeReportaEnBandera() {
        Map<String, String> e = errores(LayawayClienteRequest.builder().build());
        assertEquals(Set.of("bandera"), e.keySet());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5, -1, 99})
    void banderaFueraDeRango_SeReportaEnBandera(int bandera) {
        assertTrue(errores(LayawayClienteRequest.builder().bandera(bandera).build()).containsKey("bandera"));
    }

    // ---------- Bandera 1: registro ----------

    @Test
    void alta_SinSecciones_ReportaCadaSeccionObligatoria() {
        Map<String, String> e = errores(LayawayClienteRequest.builder().bandera(1).build());
        assertEquals(Set.of("datosPersonales", "datosContacto", "domicilio", "informacionLaboral"), e.keySet());
        assertTrue(e.get("domicilio").contains("obligatorio"));
    }

    @Test
    void alta_ConClienteIdDistintoDeCero_SeRechaza() {
        alta.setClienteId(10L);
        Map<String, String> e = errores(alta);
        assertTrue(e.get("clienteId").contains("clienteId debe ser nulo o 0"));
    }

    @Test
    void alta_ConFiltros_SeRechaza() {
        alta.setFiltros(curp("PERJ950515HDFRMN01"));
        assertTrue(errores(alta).get("filtros").contains("no se usa el objeto 'filtros'"));
    }

    @Test
    void alta_MenorDeEdad_SeRechazaConMayorDeEdad() {
        alta.getDatosPersonales().setFechaNacimiento(LocalDate.now().minusYears(17));
        assertTrue(errores(alta).containsKey("datosPersonales.fechaNacimiento"));
    }

    @Test
    void alta_SinFechaDeNacimiento_ReportaQueEsObligatoria() {
        alta.getDatosPersonales().setFechaNacimiento(null);
        assertTrue(errores(alta).get("datosPersonales.fechaNacimiento").contains("obligatoria"));
    }

    @Test
    void alta_NacionalidadEnMinusculas_SeRechaza() {
        for (String variante : new String[]{"Mexicana", "mexicana"}) {
            alta.getDatosPersonales().setNacionalidad(variante);
            assertTrue(errores(alta).containsKey("datosPersonales.nacionalidad"), variante);
        }
    }

    @Test
    void cuerpoInvalido_ReportaCadaCampoConSuMensaje() {
        alta.getDatosPersonales().setNombre("Li");
        alta.getDatosPersonales().setSexo("masculino");
        alta.getLoginCredenciales().setPassword("debil");
        Map<String, String> e = errores(alta);
        assertTrue(e.keySet().containsAll(Set.of("datosPersonales.nombre", "datosPersonales.sexo", "loginCredenciales.password")), e.toString());
    }

    // ---------- Banderas 2 y 3: actualizar y baja ----------

    @Test
    void actualizarYBaja_SinLlaves_SeRechazanEnFiltros() {
        for (int bandera : new int[]{2, 3}) {
            for (FiltrosClienteDto f : new FiltrosClienteDto[]{null, new FiltrosClienteDto()}) {
                Map<String, String> e = errores(LayawayClienteRequest.builder().bandera(bandera).filtros(f).build());
                assertTrue(e.get("filtros").contains("identifique al cliente con el objeto 'filtros'"), e.toString());
            }
        }
    }

    @Test
    void actualizarYBaja_LlaveIncompleta_ExigenElValorCompleto() {
        FiltrosClienteDto[] incompletos = {curp("LOPS90"), FiltrosClienteDto.builder().rfc("LOPS").build(),
                FiltrosClienteDto.builder().numeroCuenta("3001").build()};
        for (int bandera : new int[]{2, 3}) {
            for (FiltrosClienteDto f : incompletos) {
                Map<String, String> e = errores(LayawayClienteRequest.builder().bandera(bandera).filtros(f).build());
                assertTrue(e.get("filtros").contains("COMPLETO"), e.toString());
            }
        }
    }

    @Test
    void actualizarYBaja_ConClienteId_SeRechazaPorqueYaNoSeUsa() {
        for (int bandera : new int[]{2, 3}) {
            Map<String, String> e = errores(LayawayClienteRequest.builder().bandera(bandera).clienteId(5L)
                    .filtros(curp("LOPS900101MDFRMN02")).build());
            assertTrue(e.get("clienteId").contains("clienteId ya no se utiliza"));
        }
    }

    // ---------- Bandera 4: consulta ----------

    @Test
    void consulta_ConOtrasSeccionesOClienteId_SeRechazaEnElCampoEnviado() {
        assertTrue(errores(LayawayClienteRequest.builder().bandera(4).clienteId(5L).build()).containsKey("clienteId"));
        assertTrue(errores(LayawayClienteRequest.builder().bandera(4).datosPersonales(alta.getDatosPersonales()).build())
                .get("datosPersonales").contains("únicamente el objeto 'filtros'"));
        assertTrue(errores(LayawayClienteRequest.builder().bandera(4).datosContacto(alta.getDatosContacto()).build())
                .containsKey("datosContacto"));
        assertTrue(errores(LayawayClienteRequest.builder().bandera(4).loginCredenciales(alta.getLoginCredenciales()).build())
                .containsKey("loginCredenciales"));
    }

    @Test
    void consulta_FiltroQueNoEsLlave_SeRechaza() {
        FiltrosClienteDto f = new FiltrosClienteDto();
        f.registrarCampoDesconocido("nombre", "f");
        assertTrue(errores(LayawayClienteRequest.builder().bandera(4).filtros(f).build()).containsKey("filtros.sinCamposDesconocidos"));
    }
}

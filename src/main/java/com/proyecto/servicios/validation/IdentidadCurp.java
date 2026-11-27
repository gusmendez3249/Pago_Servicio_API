package com.proyecto.servicios.validation;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Reglas de congruencia de identidad a partir de la CURP (persona física):
 * dígito verificador, fecha de nacimiento (posiciones 5-10), sexo (posición 11) y RFC (misma inicial y misma fecha).
 * Todas devuelven {@code null} si se cumplen, o el mensaje del error.
 */
public final class IdentidadCurp {

    private static final String DICCIONARIO = "0123456789ABCDEFGHIJKLMN\u00D1OPQRSTUVWXYZ";
    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd", Locale.ROOT);

    private IdentidadCurp() {
    }

    /** Dígito verificador oficial (RENAPO): suma ponderada de las 17 primeras posiciones. */
    public static boolean digitoVerificadorValido(String curp) {
        if (curp == null || curp.length() != 18) {
            return false;
        }
        int suma = 0;
        for (int i = 0; i < 17; i++) {
            int valor = DICCIONARIO.indexOf(curp.charAt(i));
            if (valor < 0) {
                return false;
            }
            suma += valor * (18 - i);
        }
        int digito = (10 - suma % 10) % 10;
        return curp.charAt(17) == Character.forDigit(digito, 10);
    }

    public static String fechaCongruente(String curp, LocalDate fechaNacimiento) {
        if (curp == null || curp.length() < 10 || fechaNacimiento == null) {
            return null;
        }
        return curp.substring(4, 10).equals(fechaNacimiento.format(YYMMDD)) ? null
                : "La fecha de nacimiento no coincide con la de la CURP (posiciones 5 a 10: " + curp.substring(4, 10) + ").";
    }

    public static String sexoCongruente(String curp, String sexo) {
        if (curp == null || curp.length() < 11 || sexo == null) {
            return null;
        }
        char letra = curp.charAt(10);
        boolean ok = switch (sexo) {
            case "MASCULINO" -> letra == 'H';
            case "FEMENINO" -> letra == 'M';
            default -> letra == 'H' || letra == 'M' || letra == 'X';
        };
        return ok ? null : "El sexo no coincide con el de la CURP (posición 11: " + letra + ").";
    }

    public static String rfcCongruente(String curp, String rfc) {
        if (curp == null || rfc == null || curp.length() < 10 || rfc.length() < 10) {
            return null;
        }
        if (rfc.startsWith("XAXX") || rfc.startsWith("XEXX")) {
            return "No se admite un RFC genérico (XAXX o XEXX): capture el RFC propio de la persona.";
        }
        // Persona física: mismas iniciales y fecha. Solo se exige la primera letra porque las palabras altisonantes se
        // corrigen distinto en CURP (2.ª letra) y en RFC (4.ª letra)
        int inicioFechaRfc = rfc.length() == 13 ? 4 : 3;
        boolean ok = rfc.charAt(0) == curp.charAt(0)
                && rfc.substring(inicioFechaRfc, inicioFechaRfc + 6).equals(curp.substring(4, 10));
        return ok ? null : "El RFC no corresponde a la CURP (inicial y fecha de nacimiento deben coincidir).";
    }
}

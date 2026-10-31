package com.proyecto.servicios.dto.onboarding;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.proyecto.servicios.validation.PagoServicioValido;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Solicitud de pago de servicio. Validación estricta de cada campo:
 * los textos deben venir entre comillas (TextoEstrictoOnboardingConfig), los números sin comillas y sin decimales
 * donde son enteros (JacksonConfig), y cualquier campo no definido aquí se rechaza (SolicitudEstricta).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@PagoServicioValido
public class PagoServicioRequest extends SolicitudEstricta {

    public static final String PATRON_NUM_CUENTA = "^\\d{12}$";
    public static final String PATRON_TELEFONO = "^\\d{10}$";
    public static final String PATRON_REFERENCIA = "^[A-Za-z0-9]{1,100}$";
    public static final String PATRON_PIN = "^\\d{4}$";
    public static final int MAX_ID_CATALOGO = 999_999;

    @Schema(description = "Número de cuenta del cliente: exactamente 12 dígitos", example = "695429041188")
    @NotNull(message = "El número de cuenta es obligatorio")
    @Pattern(regexp = PATRON_NUM_CUENTA, message = "El número de cuenta debe tener exactamente 12 dígitos numéricos")
    private String numCuenta;

    @Schema(description = "Identificador del servicio en GestoPago (entero positivo)", example = "1")
    @NotNull(message = "El identificador del servicio (idSer) es obligatorio")
    @Positive(message = "El identificador del servicio (idSer) debe ser un entero positivo")
    @Max(value = MAX_ID_CATALOGO, message = "El identificador del servicio (idSer) no es válido")
    private Integer idSer;

    @Schema(description = "Identificador del producto en GestoPago (entero positivo)", example = "1")
    @NotNull(message = "El identificador del producto (idProd) es obligatorio")
    @Positive(message = "El identificador del producto (idProd) debe ser un entero positivo")
    @Max(value = MAX_ID_CATALOGO, message = "El identificador del producto (idProd) no es válido")
    private Integer idProd;

    @Schema(description = "Monto a pagar: de 1.00 a 99999.99 con máximo 2 decimales; además debe cumplir el catálogo", example = "100.50")
    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "1.00", message = "El monto mínimo es 1.00")
    @DecimalMax(value = "99999.99", message = "El monto máximo es 99999.99")
    @Digits(integer = 5, fraction = 2, message = "El monto admite hasta 5 enteros y 2 decimales")
    private BigDecimal monto;

    @Schema(description = "false = el pago usa teléfono, true = el pago usa referencia", example = "false")
    @NotNull(message = "La bandera esReferencia es obligatoria (true = referencia, false = teléfono)")
    private Boolean esReferencia;

    @Schema(description = "Teléfono: exactamente 10 dígitos (solo si esReferencia = false)", example = "4181234567")
    @Pattern(regexp = PATRON_TELEFONO, message = "El teléfono debe tener exactamente 10 dígitos numéricos")
    private String telefono;

    @Schema(description = "Referencia: 1 a 100 caracteres alfanuméricos sin espacios (solo si esReferencia = true)", example = "0012345678")
    @Pattern(regexp = PATRON_REFERENCIA, message = "La referencia debe tener de 1 a 100 caracteres alfanuméricos, sin espacios ni símbolos")
    private String reference;

    @Schema(description = "PIN del cliente (segundo factor 2FA): exactamente 4 dígitos", example = "1234")
    @JsonProperty("twoFA")
    @NotNull(message = "El PIN (twoFA) es obligatorio")
    @Pattern(regexp = PATRON_PIN, message = "El PIN (twoFA) debe tener exactamente 4 dígitos")
    @ToString.Exclude
    private String twoFA;
}

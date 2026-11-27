package com.proyecto.servicios.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Cuerpo de {@code POST /api/v1/cuentas/mias}: un objeto vacío {@code {}}; cualquier campo se rechaza. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Schema(description = "Objeto vacío: la cuenta se identifica por la sesión (Authorization)")
public class CuentasMiasRequest extends SolicitudEstricta {
}

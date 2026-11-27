package com.proyecto.servicios.enums;

import java.math.BigDecimal;

public enum SituacionSaldoReferencia {
    ADEUDO,
    SIN_ADEUDO,
    SALDO_A_FAVOR;

    /** saldo mayor a 0 = adeudo; igual a 0 = sin adeudo; menor a 0 = saldo a favor. */
    public static SituacionSaldoReferencia deSaldo(BigDecimal saldo) {
        int signo = saldo.signum();
        if (signo > 0) {
            return ADEUDO;
        }
        return signo == 0 ? SIN_ADEUDO : SALDO_A_FAVOR;
    }
}

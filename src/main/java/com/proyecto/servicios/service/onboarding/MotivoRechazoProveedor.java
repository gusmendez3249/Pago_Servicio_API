package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.enums.MotivoPago;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Traduce el CODIGO con que sendTx rechaza un pago al motivo enumerado que ve el cliente. Los códigos son configurables:
 * {@code gestopago.sendtx.codigos-error-gestopago} (05, 55) -> ERROR_GESTOPAGO (con prioridad),
 * {@code gestopago.sendtx.codigos-referencia-invalida} (vacío por defecto) -> REFERENCIA_INVALIDA y
 * {@code gestopago.sendtx.codigos-sin-saldo-proveedor} (02) -> SERVICIO_NO_DISPONIBLE; cualquier otro -> RECHAZADO_PROVEEDOR.
 * Nunca se usa el TEXTO del proveedor.
 */
@Component
public class MotivoRechazoProveedor {

    private final Set<String> codigosErrorGestoPago;
    private final Set<String> codigosReferenciaInvalida;
    private final Set<String> codigosSinSaldoProveedor;

    /** Sin códigos de error propio de Gesto Pago (pruebas). */
    public MotivoRechazoProveedor(String codigosReferenciaInvalida, String codigosSinSaldoProveedor) {
        this("", codigosReferenciaInvalida, codigosSinSaldoProveedor);
    }

    @Autowired
    public MotivoRechazoProveedor(
            @Value("${gestopago.sendtx.codigos-error-gestopago:05,55}") String codigosErrorGestoPago,
            @Value("${gestopago.sendtx.codigos-referencia-invalida:}") String codigosReferenciaInvalida,
            @Value("${gestopago.sendtx.codigos-sin-saldo-proveedor:02}") String codigosSinSaldoProveedor) {
        this.codigosErrorGestoPago = parsear(codigosErrorGestoPago);
        this.codigosReferenciaInvalida = parsear(codigosReferenciaInvalida);
        this.codigosSinSaldoProveedor = parsear(codigosSinSaldoProveedor);
    }

    /** Los códigos de error propio de Gesto Pago tienen prioridad sobre los demás. */
    public MotivoPago deCodigo(String codigo) {
        if (codigo != null && codigosErrorGestoPago.contains(codigo.trim())) {
            return MotivoPago.ERROR_GESTOPAGO;
        }
        if (codigo != null && codigosReferenciaInvalida.contains(codigo.trim())) {
            return MotivoPago.REFERENCIA_INVALIDA;
        }
        if (codigo != null && codigosSinSaldoProveedor.contains(codigo.trim())) {
            return MotivoPago.SERVICIO_NO_DISPONIBLE;
        }
        return MotivoPago.RECHAZADO_PROVEEDOR;
    }

    private static Set<String> parsear(String lista) {
        return Arrays.stream(lista.split(","))
                .map(String::trim)
                .filter(c -> !c.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}

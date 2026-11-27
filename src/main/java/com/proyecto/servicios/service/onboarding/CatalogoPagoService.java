package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.PagoServicioRequest;
import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.repositorys.productos.ProductoPostgresRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Valida el servicio, el producto y el monto contra el catálogo sincronizado de GestoPago. Los cobros son de contado:
 * el usuario elige QUÉ compra, nunca cambia el total, y el servidor es quien lo fija.
 * - el par idServicio/idProducto debe existir (404 PRODUCTO_NO_ENCONTRADO);
 * - solo se pagan los tipoFront de {@code pagos.tipos-front-pagables} (1 recargas y tarjetas de regalo, 2 pago de servicios);
 *   los demás (consulta de saldo, envíos de dinero...) responden 400 PRODUCTO_NO_PAGABLE;
 * - tipoFront 1: con teléfono, y el monto debe ser exactamente el precio del catálogo (400 MONTO_NO_PERMITIDO);
 * - tipoFront 2 (pago de servicio con referencia): el "precio" del catálogo es la comisión, el monto es el del recibo y debe
 *   estar entre los límites configurados. Si hay adeudo verificado en línea, el comprobante exige el total exacto
 *   (ver ComprobanteReferenciaService).
 */
@Service
public class CatalogoPagoService {

    /** tipoFront de las recargas y tarjetas de regalo (precio fijo, pago con teléfono). */
    static final int TIPO_FRONT_PRECIO_FIJO = 1;
    /** tipoFront de los pagos de servicio con referencia (luz, agua, telefonía fija, etc.). */
    static final int TIPO_FRONT_PAGO_SERVICIO = 2;

    private final ProductoPostgresRepository productoRepository;
    private final BigDecimal montoMinimo;
    private final BigDecimal montoMaximo;
    private final Set<Integer> tiposFrontPagables;

    public CatalogoPagoService(
            ProductoPostgresRepository productoRepository,
            @Value("${gestopago.sendtx.monto-minimo:1.00}") BigDecimal montoMinimo,
            @Value("${gestopago.sendtx.monto-maximo:10000.00}") BigDecimal montoMaximo,
            @Value("${pagos.tipos-front-pagables:1,2}") String tiposFrontPagables) {
        this.productoRepository = productoRepository;
        this.montoMinimo = montoMinimo;
        this.montoMaximo = montoMaximo;
        this.tiposFrontPagables = Arrays.stream(tiposFrontPagables.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .map(Integer::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    public ProductoPostgresEntity validar(PagoServicioRequest request) {
        ProductoPostgresEntity producto = productoRepository
                .findFirstByIdServicioAndIdProducto(request.getIdSer(), request.getIdProd())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El servicio/producto indicado no existe en el catálogo.", MotivoPago.PRODUCTO_NO_ENCONTRADO));

        Integer tipoFront = producto.getTipoFront();
        if (tipoFront == null || !tiposFrontPagables.contains(tipoFront)) {
            throw new ReglaNegocioException("El producto indicado no admite pagos (p. ej. es una consulta de saldo).",
                    MotivoPago.PRODUCTO_NO_PAGABLE);
        }

        BigDecimal monto = request.getMonto();
        if (tipoFront == TIPO_FRONT_PAGO_SERVICIO) {
            if (monto.compareTo(montoMinimo) < 0 || monto.compareTo(montoMaximo) > 0) {
                throw new ReglaNegocioException("El monto debe estar entre " + montoMinimo.toPlainString()
                        + " y " + montoMaximo.toPlainString() + " para este servicio.", MotivoPago.MONTO_NO_PERMITIDO);
            }
            return producto;
        }

        // Precio fijo (tipoFront 1 y cualquier otro tipo habilitado como pagable): teléfono y monto = precio
        BigDecimal precio = producto.getPrecio();
        if (precio == null || precio.signum() <= 0) {
            throw new ReglaNegocioException("El producto indicado no tiene un precio válido y no admite pagos.",
                    MotivoPago.PRODUCTO_NO_PAGABLE);
        }
        if (Boolean.TRUE.equals(request.getEsReferencia())) {
            throw new ReglaNegocioException("Este producto se paga con un número de teléfono, no con una referencia.",
                    MotivoPago.PRODUCTO_NO_PAGABLE);
        }
        if (precio.compareTo(monto) != 0) {
            throw new ReglaNegocioException("El monto no corresponde al precio del producto en el catálogo ("
                    + precio.setScale(2, RoundingMode.HALF_UP).toPlainString() + ").", MotivoPago.MONTO_NO_PERMITIDO);
        }
        return producto;
    }
}

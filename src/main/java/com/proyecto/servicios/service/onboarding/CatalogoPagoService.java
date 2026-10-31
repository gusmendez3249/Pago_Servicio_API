package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.PagoServicioRequest;
import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.repositorys.productos.ProductoPostgresRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Valida el servicio, el producto y el monto contra el catálogo sincronizado de GestoPago:
 * - el par idServicio/idProducto debe existir;
 * - pago de servicio (tipoFront 2): el monto es abierto (el "precio" del catálogo es la comisión) y debe estar
 *   dentro de los límites configurados;
 * - cualquier otro producto con precio (recargas, paquetes): el monto debe ser exactamente ese precio;
 * - productos sin precio que no son pago de servicio (p. ej. tipoFront 4, consulta de saldo) no se pueden pagar.
 */
@Service
public class CatalogoPagoService {

    /** tipoFront de los pagos de servicio con referencia (luz, agua, telefonía fija, etc.). */
    static final int TIPO_FRONT_PAGO_SERVICIO = 2;

    private final ProductoPostgresRepository productoRepository;
    private final BigDecimal montoMinimo;
    private final BigDecimal montoMaximo;

    public CatalogoPagoService(
            ProductoPostgresRepository productoRepository,
            @Value("${gestopago.sendtx.monto-minimo:1.00}") BigDecimal montoMinimo,
            @Value("${gestopago.sendtx.monto-maximo:10000.00}") BigDecimal montoMaximo) {
        this.productoRepository = productoRepository;
        this.montoMinimo = montoMinimo;
        this.montoMaximo = montoMaximo;
    }

    public ProductoPostgresEntity validar(PagoServicioRequest request) {
        ProductoPostgresEntity producto = productoRepository
                .findFirstByIdServicioAndIdProducto(request.getIdSer(), request.getIdProd())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El servicio/producto indicado no existe en el catálogo."));

        BigDecimal monto = request.getMonto();
        BigDecimal precio = producto.getPrecio();
        if (Integer.valueOf(TIPO_FRONT_PAGO_SERVICIO).equals(producto.getTipoFront())) {
            if (monto.compareTo(montoMinimo) < 0 || monto.compareTo(montoMaximo) > 0) {
                throw new ReglaNegocioException("El monto debe estar entre " + montoMinimo.toPlainString()
                        + " y " + montoMaximo.toPlainString() + " para este servicio.");
            }
        } else if (precio != null && precio.signum() > 0) {
            if (precio.compareTo(monto) != 0) {
                throw new ReglaNegocioException("El monto no corresponde al precio del producto en el catálogo ("
                        + precio.setScale(2, RoundingMode.HALF_UP).toPlainString() + ").");
            }
        } else {
            throw new ReglaNegocioException("El producto indicado no admite pagos (p. ej. es una consulta de saldo).");
        }
        return producto;
    }
}

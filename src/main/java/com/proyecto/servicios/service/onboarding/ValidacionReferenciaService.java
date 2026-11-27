package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.ValidarReferenciaRequest;
import com.proyecto.servicios.dto.onboarding.ValidarReferenciaResponse;
import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import com.proyecto.servicios.enums.EstadoValidacionReferencia;
import com.proyecto.servicios.enums.MotivoValidacionReferencia;
import com.proyecto.servicios.enums.SituacionSaldoReferencia;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.model.gestopago.ResultadoVerifyReference;
import com.proyecto.servicios.model.gestopago.VerifyReferencePayload;
import com.proyecto.servicios.repositorys.productos.ProductoPostgresRepository;
import com.proyecto.servicios.enums.VerificacionReferencia;
import com.proyecto.servicios.service.VerifyReferenceService;

import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.proyecto.servicios.exception.ProveedorNoDisponibleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Valida una referencia antes de pagar. Según el producto elegido:
 * - Producto de PAGO con producto de CONSULTA hermano (mismo idServicio): se consulta verifyReference con el producto de
 *   consulta (EN_LINEA). Solo se puede pagar con adeudo (saldo mayor a 0 y no superior al monto máximo) y el comprobante fija el monto a pagar.
 * - Producto de PAGO sin hermano de consulta: GestoPago no ofrece verificación; solo se comprobó el formato
 *   (SOLO_FORMATO) y el comprobante no fija monto (lo captura el usuario de su recibo).
 * - Producto de CONSULTA: se consulta con ese mismo producto; nunca se paga.
 * - Cualquier otro tipoFront: 400.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidacionReferenciaService {

    private final SesionService sesionService;
    private final ProductoPostgresRepository productoRepository;
    private final VerifyReferenceService verifyReferenceService;
    private final ComprobanteReferenciaService comprobanteService;

    /**
     * Si la consulta en línea del proveedor no está disponible (falla, tarda o responde un código que no es
     * 01 ni 02) se permite continuar solo con el formato, igual que en los servicios sin consulta: el usuario
     * captura el importe de su recibo y GestoPago valida la referencia al cobrar. Con {@code false} el pago
     * queda bloqueado hasta que la consulta responda.
     */
    @Value("${pagos.referencia.permitir-sin-consulta:true}")
    private boolean permitirSinConsulta = true;

    /**
     * Monto máximo por pago (el mismo que valida CatalogoPagoService). Un adeudo mayor no se puede pagar de contado
     * y la validación responde puedePagar = false sin emitir comprobante.
     */
    @Value("${gestopago.sendtx.monto-maximo:10000.00}")
    private BigDecimal montoMaximo = new BigDecimal("10000.00");

    public ValidarReferenciaResponse validar(String authorization, ValidarReferenciaRequest request) {
        SesionUsuario sesion = sesionService.autenticar(authorization);
        if (sesion.esEjecutivo() || sesion.clienteId() == null) {
            throw new AccesoDenegadoException("Solo el titular de la cuenta puede validar referencias.");
        }
        ProductoPostgresEntity elegido = productoRepository
                .findFirstByIdServicioAndIdProducto(request.getIdSer(), request.getIdProd())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El servicio/producto indicado no existe en el catálogo."));

        ValidarReferenciaResponse.ValidarReferenciaResponseBuilder respuesta = ValidarReferenciaResponse.builder()
                .idSer(request.getIdSer())
                .idProd(request.getIdProd())
                .referencia(request.getReferencia())
                .servicio(elegido.getServicio())
                .producto(elegido.getProducto());

        Integer tipoFront = elegido.getTipoFront();
        if (comprobanteService.esDeConsulta(tipoFront)) {
            return consultar(request, request.getIdProd(), respuesta, false, sesion);
        }
        if (!comprobanteService.esDePago(tipoFront)) {
            throw new ReglaNegocioException("El producto indicado no admite la verificación de referencia.");
        }
        Optional<ProductoPostgresEntity> consulta = productoRepository
                .findFirstByIdServicioAndTipoFrontInOrderByIdProductoAsc(
                        request.getIdSer(), comprobanteService.tiposFrontConsulta());
        if (consulta.isPresent()) {
            try {
                return consultar(request, consulta.get().getIdProducto(), respuesta, true, sesion);
            } catch (ProveedorNoDisponibleException exception) {
                if (!permitirSinConsulta) {
                    throw exception;
                }
                log.warn("Consulta de referencia no disponible: se continúa solo con formato. idServicio={}",
                        request.getIdSer());
            }
        }
        // Sin consulta del proveedor (no existe o no respondió): solo formato (ya validado en el DTO) y monto libre
        String idValidacion = comprobanteService.emitir(sesion.usuarioId(), request.getIdSer(), request.getIdProd(),
                request.getReferencia(), null);
        return respuesta.estado(EstadoValidacionReferencia.VALIDA)
                .verificacion(VerificacionReferencia.SOLO_FORMATO)
                .puedePagar(true)
                .idValidacion(idValidacion)
                .vigenciaSegundos((int) comprobanteService.vigenciaSegundos())
                .build();
    }

    /** Consulta verifyReference con el producto de consulta indicado (puede ser el elegido o su hermano). */
    private ValidarReferenciaResponse consultar(ValidarReferenciaRequest request, Integer idProductoConsulta,
                                                ValidarReferenciaResponse.ValidarReferenciaResponseBuilder respuesta,
                                                boolean elegidoEsDePago, SesionUsuario sesion) {
        ResultadoVerifyReference resultado = verifyReferenceService.verificar(
                new VerifyReferencePayload(idProductoConsulta, request.getIdSer(), request.getReferencia()));
        respuesta.verificacion(VerificacionReferencia.EN_LINEA);

        if (!resultado.valida()) {
            return respuesta.estado(EstadoValidacionReferencia.INVALIDA)
                    .motivo(MotivoValidacionReferencia.REFERENCIA_INVALIDA)
                    .puedePagar(false)
                    .build();
        }
        BigDecimal saldo = resultado.saldo();
        respuesta.estado(EstadoValidacionReferencia.VALIDA)
                .situacion(SituacionSaldoReferencia.deSaldo(saldo))
                .saldo(saldo);

        // Los cobros son de contado: solo se paga cuando hay adeudo y por el total
        boolean puedePagar = elegidoEsDePago && saldo.signum() > 0 && saldo.compareTo(montoMaximo) <= 0;
        respuesta.puedePagar(puedePagar);
        if (puedePagar) {
            respuesta.montoAPagar(saldo)
                    .idValidacion(comprobanteService.emitir(sesion.usuarioId(), request.getIdSer(),
                            request.getIdProd(), request.getReferencia(), saldo))
                    .vigenciaSegundos((int) comprobanteService.vigenciaSegundos());
        }
        return respuesta.build();
    }
}

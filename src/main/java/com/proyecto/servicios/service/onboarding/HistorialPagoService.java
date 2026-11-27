package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.FiltrosHistorialDto;
import com.proyecto.servicios.dto.onboarding.HistorialPagosRequest;
import com.proyecto.servicios.dto.onboarding.HistorialPagosResponse;
import com.proyecto.servicios.dto.onboarding.PagoServicioResponse;
import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.mapper.PagoServicioMapper;
import com.proyecto.servicios.repositorys.onboarding.TransaccionPagoRepository;
import com.proyecto.servicios.repositorys.productos.ProductoPostgresRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Historial paginado de pagos del usuario autenticado. El usuario sale SIEMPRE de la sesión (nunca del cuerpo), por
 * lo que solo se devuelven operaciones propias. La lista nunca lleva PIN ni saldo; los nombres de servicio/producto de
 * las operaciones antiguas (sin nombre guardado) se resuelven con UNA consulta al catálogo por página.
 */
@Service
@RequiredArgsConstructor
public class HistorialPagoService {

    private final SesionService sesionService;
    private final TransaccionPagoRepository transaccionPagoRepository;
    private final ProductoPostgresRepository productoRepository;
    private final PagoServicioMapper mapper;

    public HistorialPagosResponse listar(String authorization, HistorialPagosRequest request) {
        SesionUsuario sesion = sesionService.autenticar(authorization);
        if (sesion.esEjecutivo() || sesion.clienteId() == null) {
            throw new AccesoDenegadoException("Solo el titular de la cuenta puede consultar su historial de pagos.",
                    MotivoPago.NO_TITULAR);
        }
        int pagina = request.paginaEfectiva();
        int tamanio = request.tamanioEfectivo();
        Sort orden = Sort.by(Sort.Order.desc("fechaTransaccion"), Sort.Order.desc("id"));

        Page<TransaccionPagoEntity> resultado = transaccionPagoRepository.findAll(
                especificacion(sesion.usuarioId(), request.filtrosEfectivos()), PageRequest.of(pagina, tamanio, orden));

        Map<String, ProductoPostgresEntity> catalogo = catalogoParaAntiguas(resultado.getContent());
        List<PagoServicioResponse> elementos = resultado.getContent().stream().map(tx -> {
            ProductoPostgresEntity producto = catalogo.get(clave(tx.getIdServicio(), tx.getIdProducto()));
            return mapper.aElementoHistorial(tx, tx.getCuenta().getNumeroCuenta(),
                    producto == null ? null : producto.getServicio(), producto == null ? null : producto.getProducto());
        }).toList();

        return HistorialPagosResponse.builder()
                .elementos(elementos)
                .pagina(pagina)
                .tamanio(tamanio)
                .total(resultado.getTotalElements())
                .hayMas(resultado.hasNext())
                .build();
    }

    /**
     * Las fechas de las operaciones se guardan en la zona {@code gestopago.sendtx.zone} y los límites del filtro son
     * días completos en esa misma zona: no hace falta convertir. {@code fechaHasta} se toma inclusiva (< día siguiente).
     */
    static Specification<TransaccionPagoEntity> especificacion(Long usuarioId, FiltrosHistorialDto filtros) {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                // La cuenta se necesita para enmascarar el número (la relación es LAZY); no aplica a la consulta de conteo
                root.fetch("cuenta");
            }
            List<Predicate> condiciones = new ArrayList<>();
            condiciones.add(cb.equal(root.get("usuarioId"), usuarioId));
            if (filtros.getEstatus() != null) {
                condiciones.add(cb.equal(root.get("estatus"), filtros.getEstatus()));
            }
            if (filtros.getFechaDesde() != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("fechaTransaccion"),
                        filtros.getFechaDesde().atStartOfDay()));
            }
            if (filtros.getFechaHasta() != null) {
                condiciones.add(cb.lessThan(root.<LocalDateTime>get("fechaTransaccion"),
                        filtros.getFechaHasta().plusDays(1).atStartOfDay()));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
    }

    /** Una sola consulta para todas las filas de la página que no guardaron el nombre; vacío si no hace falta. */
    private Map<String, ProductoPostgresEntity> catalogoParaAntiguas(List<TransaccionPagoEntity> transacciones) {
        Set<Integer> servicios = new HashSet<>();
        for (TransaccionPagoEntity tx : transacciones) {
            if ((tx.getNombreServicio() == null || tx.getNombreProducto() == null) && tx.getIdServicio() != null) {
                servicios.add(tx.getIdServicio());
            }
        }
        Map<String, ProductoPostgresEntity> catalogo = new HashMap<>();
        if (servicios.isEmpty()) {
            return catalogo;
        }
        for (ProductoPostgresEntity p : productoRepository.findByIdServicioIn(servicios)) {
            catalogo.putIfAbsent(clave(p.getIdServicio(), p.getIdProducto()), p);
        }
        return catalogo;
    }

    private static String clave(Integer idServicio, Integer idProducto) {
        return Objects.toString(idServicio) + ":" + Objects.toString(idProducto);
    }
}

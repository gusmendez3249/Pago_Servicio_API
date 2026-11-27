package com.proyecto.servicios.service.onboarding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.dto.onboarding.PagoServicioRequest;
import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.enums.OrigenMonto;
import com.proyecto.servicios.exception.ProveedorNoDisponibleException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.exception.ValidacionReferenciaRequeridaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Candado previo al pago por referencia: el servidor solo cobra una referencia que él mismo verificó.
 *
 * Al validar con CODIGO 01 se emite un comprobante (UUID v4 con SecureRandom) guardado en Redis bajo
 * {@code pago:refval:{idValidacion}} con TTL corto y ligado a usuario, servicio, producto y referencia.
 * Al pagar se consume con {@code GETDEL} (atómico: un solo uso). Si Redis no responde se falla cerrado (503).
 */
@Slf4j
@Service
public class ComprobanteReferenciaService {

    static final String PREFIJO_CLAVE = "pago:refval:";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    static final String MENSAJE_REQUERIDA = "Debe validar la referencia antes de pagar: la verificación no existe, "
            + "expiró, ya se utilizó o no corresponde a este pago. Valide la referencia de nuevo.";
    static final String MENSAJE_ALMACEN = "No fue posible comprobar la verificación de la referencia en este momento. "
            + "Inténtelo más tarde.";

    /** Valor guardado en Redis (sin datos personales más allá de la propia referencia). */
    public record Comprobante(Long usuarioId, Integer idSer, Integer idProd, String referencia, BigDecimal montoAPagar) { }

    private final StringRedisTemplate redis;
    private final RespaldoComprobantes respaldo;
    private final boolean habilitado;
    private final long ttlSegundos;
    private final Set<Integer> tiposFrontPago;
    private final Set<Integer> tiposFrontConsulta;

    /** Sin almacén de respaldo (pruebas): si Redis falla, 503. */
    public ComprobanteReferenciaService(StringRedisTemplate redis, boolean habilitado, long ttlSegundos,
                                        String tiposFrontPago, String tiposFrontConsulta) {
        this(redis, RespaldoComprobantes.ninguno(), habilitado, ttlSegundos, tiposFrontPago, tiposFrontConsulta);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ComprobanteReferenciaService(
            StringRedisTemplate redis,
            RespaldoComprobantes respaldo,
            @Value("${pagos.referencia.candado.habilitado:true}") boolean habilitado,
            @Value("${pagos.referencia.validacion-ttl-segundos:300}") long ttlSegundos,
            @Value("${pagos.referencia.tipos-front-pago:2}") String tiposFrontPago,
            @Value("${gestopago.verifyref.tipos-front-consulta:4}") String tiposFrontConsulta) {
        this.redis = redis;
        this.respaldo = respaldo;
        this.habilitado = habilitado;
        this.ttlSegundos = ttlSegundos;
        this.tiposFrontPago = parsear(tiposFrontPago);
        this.tiposFrontConsulta = parsear(tiposFrontConsulta);
    }

    private static Set<Integer> parsear(String lista) {
        return Arrays.stream(lista.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .map(Integer::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    public long vigenciaSegundos() {
        return ttlSegundos;
    }

    /** true si los productos de ese tipoFront se pagan con referencia y exigen comprobante. */
    public boolean esDePago(Integer tipoFront) {
        return tipoFront != null && tiposFrontPago.contains(tipoFront);
    }

    /** true si son productos de consulta de saldo (se verifican con verifyReference y no se pagan). */
    public boolean esDeConsulta(Integer tipoFront) {
        return tipoFront != null && tiposFrontConsulta.contains(tipoFront);
    }

    public Set<Integer> tiposFrontConsulta() {
        return tiposFrontConsulta;
    }

    /**
     * Emite el comprobante de una referencia VÁLIDA. Redis no disponible = 503: sin comprobante no hay pago.
     * @param montoAPagar total exigido al pagar (nulo = el usuario captura el monto de su recibo)
     */
    public String emitir(Long usuarioId, Integer idSer, Integer idProd, String referencia, BigDecimal montoAPagar) {
        String idValidacion = nuevoUuidV4();
        try {
            String valor = JSON.writeValueAsString(new Comprobante(usuarioId, idSer, idProd, referencia, montoAPagar));
            guardar(idValidacion, valor);
        } catch (JsonProcessingException exception) {
            log.error("No se pudo serializar el comprobante de validación");
            throw new ProveedorNoDisponibleException(MENSAJE_ALMACEN, HttpStatus.INTERNAL_SERVER_ERROR,
                    MotivoPago.PROVEEDOR_NO_DISPONIBLE);
        }
        return idValidacion;
    }

    /**
     * Verifica y consume el comprobante antes de reservar saldo.
     * - Producto de pago (tipoFront en pagos.referencia.tipos-front-pago) pagado con teléfono: 400.
     * - Pago por referencia: comprobante existente, vigente, sin usar y del mismo usuario/servicio/producto/referencia;
     *   si no, 428. El comprobante se consume aunque el pago termine rechazado: hay que validar de nuevo.
     * - Si el comprobante trae montoAPagar, el monto del pago debe ser exactamente ese (400; el comprobante ya quedó consumido).
     * - Pagos por teléfono de otros tipoFront (recargas de precio fijo): exentos.
     * Con el candado deshabilitado solo se aplica la primera regla (el monto queda CAPTURADO).
     *
     * @return de dónde sale el monto: PRECIO_FIJO (pago con teléfono), ADEUDO (comprobante con montoAPagar) o CAPTURADO
     *         (referencia sin consulta en línea: el usuario capturó el importe de su recibo)
     */
    public OrigenMonto consumir(SesionUsuario sesion, PagoServicioRequest request, ProductoPostgresEntity producto) {
        boolean porReferencia = Boolean.TRUE.equals(request.getEsReferencia());
        if (esDePago(producto.getTipoFront()) && !porReferencia) {
            throw new ReglaNegocioException("Este servicio solo se puede pagar con una referencia previamente validada.",
                    MotivoPago.PRODUCTO_NO_PAGABLE);
        }
        if (!porReferencia) {
            return OrigenMonto.PRECIO_FIJO;
        }
        if (!habilitado) {
            return OrigenMonto.CAPTURADO;
        }
        String idValidacion = request.getIdValidacion();
        if (idValidacion == null) {
            throw new ValidacionReferenciaRequeridaException(MENSAJE_REQUERIDA, MotivoPago.VALIDACION_REQUERIDA);
        }
        Comprobante comprobante = leer(tomar(idValidacion.toLowerCase(Locale.ROOT)));
        if (comprobante == null
                || !Objects.equals(comprobante.usuarioId(), sesion.usuarioId())
                || !Objects.equals(comprobante.idSer(), request.getIdSer())
                || !Objects.equals(comprobante.idProd(), request.getIdProd())
                || !Objects.equals(comprobante.referencia(), request.getReference())) {
            log.warn("Pago por referencia sin comprobante válido. usuarioId={} idServicio={} idProducto={}",
                    sesion.usuarioId(), request.getIdSer(), request.getIdProd());
            throw new ValidacionReferenciaRequeridaException(MENSAJE_REQUERIDA, MotivoPago.VALIDACION_REQUERIDA);
        }
        if (comprobante.montoAPagar() != null && request.getMonto().compareTo(comprobante.montoAPagar()) != 0) {
            throw new ReglaNegocioException("El monto debe ser el total del adeudo verificado: "
                    + comprobante.montoAPagar().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + ".",
                    MotivoPago.MONTO_NO_PERMITIDO);
        }
        return comprobante.montoAPagar() != null ? OrigenMonto.ADEUDO : OrigenMonto.CAPTURADO;
    }

    /** Redis primero; si no responde, el respaldo en PostgreSQL. Solo si ambos fallan: 503. */
    private void guardar(String idValidacion, String valor) {
        Duration vigencia = Duration.ofSeconds(ttlSegundos);
        try {
            redis.opsForValue().set(PREFIJO_CLAVE + idValidacion, valor, vigencia);
            return;
        } catch (DataAccessException exception) {
            log.warn("Redis no guardó el comprobante; se usa el respaldo. causa={}", exception.getClass().getSimpleName());
        }
        try {
            respaldo.guardar(idValidacion, valor, vigencia);
        } catch (DataAccessException exception) {
            log.error("No se pudo guardar el comprobante de validación. causa={}", exception.getClass().getSimpleName());
            throw almacenNoDisponible();
        }
    }

    /**
     * Consume el comprobante una sola vez. Puede estar en Redis o, si se emitió con Redis caído, en el respaldo: si Redis
     * no lo tiene (o no responde) se busca en el respaldo. Solo si ninguno de los dos responde: 503.
     */
    private String tomar(String idValidacion) {
        boolean redisDisponible = true;
        try {
            String valor = redis.opsForValue().getAndDelete(PREFIJO_CLAVE + idValidacion);
            if (valor != null) {
                return valor;
            }
        } catch (DataAccessException exception) {
            redisDisponible = false;
            log.warn("Redis no respondió al consumir el comprobante; se usa el respaldo. causa={}",
                    exception.getClass().getSimpleName());
        }
        try {
            return respaldo.consumir(idValidacion);
        } catch (DataAccessException exception) {
            if (redisDisponible) {
                return null; // Redis respondió que no existe: no hace falta el respaldo para negar el pago
            }
            log.error("No se pudo consultar el comprobante de validación. causa={}", exception.getClass().getSimpleName());
            throw almacenNoDisponible();
        }
    }

    private static Comprobante leer(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return JSON.readValue(valor, Comprobante.class);
        } catch (JsonProcessingException | RuntimeException exception) {
            return null; // ilegible: se trata como inexistente (bloquea el pago)
        }
    }

    private static ProveedorNoDisponibleException almacenNoDisponible() {
        return new ProveedorNoDisponibleException(MENSAJE_ALMACEN, HttpStatus.SERVICE_UNAVAILABLE, MotivoPago.PROVEEDOR_NO_DISPONIBLE);
    }

    /** UUID versión 4 (variante RFC 4122) a partir de SecureRandom. */
    static String nuevoUuidV4() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x40);
        bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);
        String hex = HexFormat.of().formatHex(bytes);
        return new UUID(Long.parseUnsignedLong(hex.substring(0, 16), 16),
                Long.parseUnsignedLong(hex.substring(16), 16)).toString();
    }
}

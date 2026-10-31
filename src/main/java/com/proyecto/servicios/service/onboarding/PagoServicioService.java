package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.PagoServicioRequest;
import com.proyecto.servicios.dto.onboarding.PagoServicioResponse;
import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import com.proyecto.servicios.entity.onboarding.CuentaEntity;
import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.exception.ClienteInactivoException;
import com.proyecto.servicios.exception.ClienteNoEncontradoException;
import com.proyecto.servicios.exception.OperacionDuplicadaException;
import com.proyecto.servicios.exception.PagoRechazadoException;
import com.proyecto.servicios.exception.ProveedorNoDisponibleException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.model.gestopago.ResultadoSendTx;
import com.proyecto.servicios.model.gestopago.SendTxPayload;
import com.proyecto.servicios.repositorys.onboarding.CuentaRepository;
import com.proyecto.servicios.service.SendTxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Orquesta el pago de un servicio con GestoPago (sendTx):
 *
 * 1. Sesión válida (Bearer) de un usuario CLIENTE; los ejecutivos no pagan en nombre de clientes.
 * 2. Idempotency-Key obligatoria: si ya existe, se devuelve el resultado original sin volver a cobrar.
 * 3. La cuenta debe pertenecer al cliente de la sesión, y cliente y cuenta deben estar activos.
 * 4. PIN 2FA con bloqueo por intentos fallidos.
 * 5. Servicio, producto y monto validados contra el catálogo.
 * 6. Reserva del saldo + registro PENDIENTE en una sola transacción (UPDATE condicionado: sin doble gasto).
 * 7. Envío a GestoPago FUERA de la transacción de BD.
 * 8. Según el desenlace: CONFIRMADA (200), RECHAZADA con reintegro (422 / 502), o INDETERMINADA con el monto
 *    retenido (202) para que la conciliación determine el resultado real. Un timeout nunca se toma como "no procesado".
 */
@Slf4j
@Service
public class PagoServicioService {

    private static final String TELEFONO_TIPO_FRONT = "1111111111";
    private static final String TELEFONO_TIPO_SERVICIO = "#1111111111";
    private static final String ALFABETO_UPC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int MAX_INTENTOS_UPC = 5;
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");
    private static final DateTimeFormatter FORMATO_HORA_LOCAL = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    static final String MENSAJE_INDETERMINADA = "El pago quedó en verificación con el proveedor. El monto permanece "
            + "retenido hasta confirmar el resultado; no repita el pago con otra Idempotency-Key. Para consultar el "
            + "estado reenvíe la misma solicitud con la misma Idempotency-Key.";

    private final SesionService sesionService;
    private final CuentaRepository cuentaRepository;
    private final TransaccionPagoService transaccionPagoService;
    private final PinClienteService pinClienteService;
    private final CatalogoPagoService catalogoPagoService;
    private final SendTxService sendTxService;
    private final String unidad;
    private final ZoneId zona;

    public PagoServicioService(
            SesionService sesionService,
            CuentaRepository cuentaRepository,
            TransaccionPagoService transaccionPagoService,
            PinClienteService pinClienteService,
            CatalogoPagoService catalogoPagoService,
            SendTxService sendTxService,
            @Value("${gestopago.sendtx.unidad:}") String unidad,
            @Value("${gestopago.sendtx.zone:America/Mexico_City}") String zona) {

        this.sesionService = sesionService;
        this.cuentaRepository = cuentaRepository;
        this.transaccionPagoService = transaccionPagoService;
        this.pinClienteService = pinClienteService;
        this.catalogoPagoService = catalogoPagoService;
        this.sendTxService = sendTxService;
        this.unidad = unidad == null || unidad.isBlank() ? null : unidad.trim();
        this.zona = ZoneId.of(zona);
    }

    public PagoServicioResponse procesarPago(String authorization, String idempotencyKey, PagoServicioRequest request) {
        // El cuerpo ya llega validado por @Valid (anotaciones del DTO, @PagoServicioValido y @SinCamposDesconocidos)
        SesionUsuario sesion = sesionService.autenticar(authorization);
        if (sesion.esEjecutivo() || sesion.clienteId() == null) {
            throw new AccesoDenegadoException("Solo el titular de la cuenta puede realizar pagos de servicios.");
        }
        String llave = normalizarLlave(idempotencyKey);
        String huella = huella(request);

        Optional<TransaccionPagoEntity> previa = transaccionPagoService.buscarPorLlave(llave);
        if (previa.isPresent()) {
            return repetir(previa.get(), sesion, huella);
        }

        // Una cuenta ajena responde igual que una inexistente: no se revela qué cuentas existen
        CuentaEntity cuenta = cuentaRepository.findConClienteByNumeroCuenta(request.getNumCuenta())
                .filter(c -> Objects.equals(c.getCliente().getId(), sesion.clienteId()))
                .orElseThrow(() -> new ClienteNoEncontradoException("No se encontró la cuenta indicada entre las cuentas del cliente."));
        validarClienteActivo(cuenta);
        pinClienteService.verificar(cuenta.getCliente().getId(), request.getTwoFA());
        ProductoPostgresEntity producto = catalogoPagoService.validar(request);

        String upc = generarUpcDisponible();
        SendTxPayload payload = armarPayload(request, producto, upc);
        TransaccionPagoEntity transaccion = reservar(cuenta.getId(), sesion, llave, huella, request, upc);
        log.info("Saldo reservado, se envía sendTx. upc={} idServicio={} idProducto={}",
                upc, request.getIdSer(), request.getIdProd());

        ResultadoSendTx resultado;
        try {
            resultado = sendTxService.enviar(payload);
        } catch (RuntimeException exception) {
            // Falla no prevista: no se puede asegurar que el proveedor no la recibió
            log.error("Falla inesperada durante sendTx: INDETERMINADA. upc={} causa={}", upc, exception.getClass().getSimpleName());
            resultado = ResultadoSendTx.indeterminada(null, null, ErrorCode.INTERNAL_ERROR);
        }
        return aplicarResultado(transaccion, cuenta, resultado);
    }

    // ---------------------------------------------------------------- desenlace

    private PagoServicioResponse aplicarResultado(TransaccionPagoEntity tx, CuentaEntity cuenta, ResultadoSendTx resultado) {
        switch (resultado.desenlace()) {
            case APROBADA -> {
                if (!transaccionPagoService.confirmar(tx.getId(), resultado.codigo(), resultado.texto())) {
                    log.error("Pago aprobado por GestoPago pero la operación ya estaba resuelta. upc={}", tx.getUpc());
                }
                log.info("Pago confirmado. upc={}", tx.getUpc());
                return respuesta(tx, cuenta.getNumeroCuenta(), cuenta.getId(), EstatusTransaccionPago.CONFIRMADA,
                        resultado.codigo(), resultado.texto() != null ? resultado.texto() : "Pago realizado correctamente.");
            }
            case RECHAZADA -> {
                transaccionPagoService.rechazarYReintegrar(tx.getId(), cuenta.getId(), tx.getMonto(),
                        resultado.codigo(), resultado.texto());
                throw new PagoRechazadoException("El proveedor rechazó el pago (código " + resultado.codigo()
                        + "). El saldo no fue afectado.");
            }
            case NO_ENVIADA -> {
                transaccionPagoService.rechazarYReintegrar(tx.getId(), cuenta.getId(), tx.getMonto(),
                        null, "No enviada: " + resultado.error());
                boolean interna = resultado.error() == ErrorCode.CONFIGURATION_ERROR || resultado.error() == ErrorCode.INTERNAL_ERROR;
                throw new ProveedorNoDisponibleException(
                        "No fue posible procesar el pago con el proveedor. El saldo no fue afectado; "
                                + "puede intentarlo de nuevo con una nueva Idempotency-Key.",
                        interna ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_GATEWAY);
            }
            default -> {
                transaccionPagoService.marcarIndeterminada(tx.getId(), resultado.codigo(),
                        resultado.texto() != null ? resultado.texto() : "Sin resultado: " + resultado.error());
                log.warn("Pago INDETERMINADO: monto retenido hasta conciliar. upc={}", tx.getUpc());
                return respuesta(tx, cuenta.getNumeroCuenta(), cuenta.getId(), EstatusTransaccionPago.INDETERMINADA,
                        resultado.codigo(), MENSAJE_INDETERMINADA);
            }
        }
    }

    /** Misma Idempotency-Key: se devuelve el resultado ya registrado, sin volver a cobrar ni a llamar al proveedor. */
    private PagoServicioResponse repetir(TransaccionPagoEntity previa, SesionUsuario sesion, String huella) {
        if (!Objects.equals(previa.getUsuarioId(), sesion.usuarioId())) {
            throw new OperacionDuplicadaException("La Idempotency-Key ya fue utilizada. Genere una nueva para cada pago.");
        }
        if (!Objects.equals(previa.getHashSolicitud(), huella)) {
            throw new OperacionDuplicadaException(
                    "La Idempotency-Key ya se utilizó con datos distintos. Genere una nueva para cada pago.");
        }
        log.info("Solicitud repetida con la misma Idempotency-Key: se devuelve el resultado registrado sin "
                + "volver a cobrar ni llamar a GestoPago. upc={} estatus={}", previa.getUpc(), previa.getEstatus());
        CuentaEntity cuenta = previa.getCuenta();
        return switch (previa.getEstatus()) {
            case CONFIRMADA -> respuesta(previa, cuenta.getNumeroCuenta(), cuenta.getId(), EstatusTransaccionPago.CONFIRMADA,
                    previa.getCodigoRespuesta(), previa.getTextoRespuesta());
            case RECHAZADA -> throw new PagoRechazadoException("El pago con esta Idempotency-Key no se aplicó y el saldo "
                    + "fue reintegrado. Para intentarlo de nuevo use una nueva Idempotency-Key.");
            default -> respuesta(previa, cuenta.getNumeroCuenta(), cuenta.getId(), previa.getEstatus(),
                    previa.getCodigoRespuesta(), MENSAJE_INDETERMINADA);
        };
    }

    // ---------------------------------------------------------------- validaciones de negocio

    private void validarClienteActivo(CuentaEntity cuenta) {
        ClienteEntity cliente = cuenta.getCliente();
        if (!Boolean.TRUE.equals(cliente.getActivo()) || !"ACTIVA".equals(cuenta.getEstatus())) {
            throw new ClienteInactivoException("El cliente o la cuenta se encuentra inactivo y no puede realizar pagos.");
        }
    }

    private String normalizarLlave(String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new ReglaNegocioException("El encabezado Idempotency-Key es obligatorio y debe ser un UUID "
                    + "(36 caracteres, p. ej. 3f1c2a9e-7b4d-4c1e-9a2b-5d6e7f8a9b0c). Genere uno nuevo por cada pago.");
        }
        return idempotencyKey.toLowerCase(Locale.ROOT);
    }

    /** SHA-256 de los datos que definen la operación (sin el PIN): detecta reuso de la llave con otro pago. */
    static String huella(PagoServicioRequest request) {
        String datos = String.join("|",
                request.getNumCuenta(),
                String.valueOf(request.getIdSer()),
                String.valueOf(request.getIdProd()),
                request.getMonto().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                String.valueOf(request.getEsReferencia()),
                Objects.toString(request.getTelefono(), ""),
                Objects.toString(request.getReference(), ""));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(datos.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no disponible", exception);
        }
    }

    // ---------------------------------------------------------------- reserva

    private TransaccionPagoEntity reservar(Long cuentaId, SesionUsuario sesion, String llave, String huella,
                                           PagoServicioRequest request, String upc) {
        boolean porReferencia = Boolean.TRUE.equals(request.getEsReferencia());
        TransaccionPagoEntity nueva = TransaccionPagoEntity.builder()
                .usuarioId(sesion.usuarioId())
                .idServicio(request.getIdSer())
                .idProducto(request.getIdProd())
                .monto(request.getMonto().setScale(2, RoundingMode.UNNECESSARY))
                .telefono(porReferencia ? null : request.getTelefono())
                .referencia(porReferencia ? request.getReference() : null)
                .upc(upc)
                .idempotencyKey(llave)
                .hashSolicitud(huella)
                .fechaTransaccion(LocalDateTime.now(zona))
                .build();
        try {
            return transaccionPagoService.reservar(cuentaId, nueva);
        } catch (DataIntegrityViolationException exception) {
            // Dos peticiones simultáneas con la misma llave: el índice único deja pasar solo una y revierte la otra
            throw new OperacionDuplicadaException("Ya existe una operación en curso con esta Idempotency-Key.");
        }
    }

    /** Identificador de operación de exactamente 10 caracteres [A-Z0-9], único en tb_transaccion_pago. */
    private String generarUpcDisponible() {
        for (int intento = 0; intento < MAX_INTENTOS_UPC; intento++) {
            String upc = generarUpc();
            if (!transaccionPagoService.existeUpc(upc)) {
                return upc;
            }
        }
        throw new IllegalStateException("No se pudo generar un upc único");
    }

    static String generarUpc() {
        StringBuilder upc = new StringBuilder(TransaccionPagoEntity.LONGITUD_UPC);
        for (int i = 0; i < TransaccionPagoEntity.LONGITUD_UPC; i++) {
            upc.append(ALFABETO_UPC.charAt(RANDOM.nextInt(ALFABETO_UPC.length())));
        }
        return upc.toString();
    }

    // ---------------------------------------------------------------- payload

    private SendTxPayload armarPayload(PagoServicioRequest request, ProductoPostgresEntity producto, String upc) {
        SendTxPayload.SendTxPayloadBuilder payload = SendTxPayload.builder()
                .idProducto(request.getIdProd())
                .idServicio(request.getIdSer())
                .montoPago(request.getMonto().setScale(2, RoundingMode.UNNECESSARY))
                .horaLocal(LocalDateTime.now(zona).format(FORMATO_HORA_LOCAL))
                .upc(upc)
                .unidad(unidad);

        if (Boolean.TRUE.equals(request.getEsReferencia())) {
            payload.referencia(request.getReference())
                    .telefono(telefonoParaReferencia(producto));
        } else {
            payload.telefono(request.getTelefono());
        }
        return payload.build();
    }

    /** Reglas de GestoPago: tipoFront 1 o 2 -> "1111111111"; idCatTipoServicio 10 u 11 -> "#1111111111". */
    private String telefonoParaReferencia(ProductoPostgresEntity producto) {
        Integer tipoFront = producto.getTipoFront();
        Integer tipoServicio = producto.getIdCatTipoServicio();
        if (tipoFront != null && (tipoFront == 1 || tipoFront == 2)) {
            return TELEFONO_TIPO_FRONT;
        }
        if (tipoServicio != null && (tipoServicio == 10 || tipoServicio == 11)) {
            return TELEFONO_TIPO_SERVICIO;
        }
        return null;
    }

    // ---------------------------------------------------------------- respuesta

    private PagoServicioResponse respuesta(TransaccionPagoEntity tx, String numCuenta, Long cuentaId,
                                           EstatusTransaccionPago estatus, String codigo, String mensaje) {
        BigDecimal saldo = transaccionPagoService.saldo(cuentaId);
        return PagoServicioResponse.builder()
                .numCuenta(enmascarar(numCuenta))
                .upc(tx.getUpc())
                .estatus(estatus)
                .monto(tx.getMonto())
                .saldoRestante(saldo)
                .codigoRespuesta(codigo)
                .mensajeRespuesta(mensaje)
                .fechaTransaccion(tx.getFechaTransaccion())
                .build();
    }

    static String enmascarar(String numCuenta) {
        if (numCuenta == null || numCuenta.length() <= 4) {
            return numCuenta;
        }
        return "*".repeat(numCuenta.length() - 4) + numCuenta.substring(numCuenta.length() - 4);
    }
}

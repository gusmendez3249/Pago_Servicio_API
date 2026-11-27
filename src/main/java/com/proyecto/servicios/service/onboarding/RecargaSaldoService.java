package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.ConsultaCuentaAdminRequest;
import com.proyecto.servicios.dto.onboarding.CuentaAdminResponse;
import com.proyecto.servicios.dto.onboarding.HistorialRecargasRequest;
import com.proyecto.servicios.dto.onboarding.HistorialRecargasResponse;
import com.proyecto.servicios.dto.onboarding.RecargaSaldoRequest;
import com.proyecto.servicios.dto.onboarding.RecargaSaldoResponse;
import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import com.proyecto.servicios.entity.onboarding.CuentaEntity;
import com.proyecto.servicios.entity.onboarding.RecargaSaldoEntity;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.exception.ClienteInactivoException;
import com.proyecto.servicios.exception.OperacionDuplicadaException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.exception.TopeRecargaException;
import com.proyecto.servicios.mapper.PagoServicioMapper;
import com.proyecto.servicios.repositorys.onboarding.CuentaRepository;
import com.proyecto.servicios.repositorys.onboarding.RecargaSaldoRepository;
import com.proyecto.servicios.repositorys.onboarding.UsuarioLoginRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Recargas de saldo desde el panel de administrador. Es la única forma de abonar dinero a una cuenta (las cuentas
 * nuevas nacen en 0.00).
 * <ul>
 *   <li>Solo un usuario EJECUTIVO puede consultar cuentas y recargar.</li>
 *   <li>Idempotency-Key obligatoria: la misma solicitud repetida devuelve la recarga original sin abonar dos veces.</li>
 *   <li>El abono es un UPDATE atómico condicionado a cuenta ACTIVA, y la recarga queda auditada en la misma transacción.</li>
 *   <li>Tope diario por cuenta y por ejecutivo: se bloquean la fila del ejecutivo y la de la cuenta, así dos recargas
 *   simultáneas no pueden rebasarlo.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecargaSaldoService {

    static final int TAMANIO_PAGINA = 20;
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private final SesionService sesionService;
    private final CuentaRepository cuentaRepository;
    private final RecargaSaldoRepository recargaRepository;
    private final TransactionTemplate transactionTemplate;
    private final UsuarioLoginRepository usuarioLoginRepository;

    @Value("${app.recargas.tope-diario-cuenta:100000.00}")
    private BigDecimal topeDiarioCuenta = new BigDecimal("100000.00");

    @Value("${app.recargas.tope-diario-ejecutivo:200000.00}")
    private BigDecimal topeDiarioEjecutivo = new BigDecimal("200000.00");

    public CuentaAdminResponse consultarCuenta(String authorization, ConsultaCuentaAdminRequest request) {
        exigirEjecutivo(authorization);
        CuentaEntity cuenta = buscarCuenta(request.getNumeroCuenta());
        return CuentaAdminResponse.builder()
                .numCuentaEnmascarada(PagoServicioMapper.enmascarar(cuenta.getNumeroCuenta()))
                .titular(titular(cuenta.getCliente()))
                .saldo(cuenta.getSaldo())
                .estatus(cuenta.getEstatus())
                .recargable(recargable(cuenta))
                .build();
    }

    public RecargaSaldoResponse recargar(String authorization, String idempotencyKey, RecargaSaldoRequest request) {
        SesionUsuario sesion = exigirEjecutivo(authorization);
        String llave = normalizarLlave(idempotencyKey);
        BigDecimal monto = request.getMonto().setScale(2, RoundingMode.UNNECESSARY);
        String huella = huella(request.getNumeroCuenta(), monto);

        RecargaSaldoEntity previa = recargaRepository.findByIdempotencyKey(llave).orElse(null);
        if (previa != null) {
            return repetir(previa, sesion, huella);
        }

        CuentaEntity cuenta = buscarCuenta(request.getNumeroCuenta());
        if (!recargable(cuenta)) {
            throw new ClienteInactivoException("La cuenta o su titular están inactivos: no se puede recargar.",
                    MotivoPago.CUENTA_INACTIVA);
        }
        RecargaSaldoEntity recarga;
        try {
            recarga = transactionTemplate.execute(status -> {
                LocalDateTime inicioDelDia = LocalDate.now().atStartOfDay();
                // Orden fijo de bloqueos (ejecutivo, luego cuenta) para no provocar interbloqueos
                usuarioLoginRepository.findByIdParaActualizar(sesion.usuarioId());
                if (recargaRepository.sumarPorEjecutivoDesde(sesion.usuarioId(), inicioDelDia).add(monto)
                        .compareTo(topeDiarioEjecutivo) > 0) {
                    throw new TopeRecargaException("La recarga rebasa el tope diario del ejecutivo.",
                            MotivoPago.TOPE_DIARIO_EJECUTIVO);
                }
                // El UPDATE bloquea la fila de la cuenta hasta el commit: la suma siguiente ya no puede cambiar
                if (cuentaRepository.abonarSaldo(cuenta.getId(), monto) == 0) {
                    throw new ClienteInactivoException("La cuenta está inactiva: no se puede recargar.",
                            MotivoPago.CUENTA_INACTIVA);
                }
                if (recargaRepository.sumarPorCuentaDesde(cuenta.getId(), inicioDelDia).add(monto)
                        .compareTo(topeDiarioCuenta) > 0) {
                    throw new TopeRecargaException("La recarga rebasa el tope diario de la cuenta.",
                            MotivoPago.TOPE_DIARIO_CUENTA);
                }
                BigDecimal saldo = cuentaRepository.findSaldoById(cuenta.getId()).orElse(null);
                return recargaRepository.saveAndFlush(RecargaSaldoEntity.builder()
                        .cuenta(cuentaRepository.getReferenceById(cuenta.getId()))
                        .monto(monto)
                        .saldoResultante(saldo)
                        .ejecutivoId(sesion.usuarioId())
                        .idempotencyKey(llave)
                        .hashSolicitud(huella)
                        .fecha(LocalDateTime.now())
                        .build());
            });
        } catch (DataIntegrityViolationException exception) {
            // Dos solicitudes simultáneas con la misma llave: el índice único revierte la segunda (y su abono)
            throw new OperacionDuplicadaException("Ya existe una recarga en curso con esta Idempotency-Key.",
                    MotivoPago.LLAVE_EN_USO);
        }
        log.info("Recarga de saldo aplicada. folio={} cuenta={} monto={} ejecutivoId={}", folio(recarga.getId()),
                PagoServicioMapper.enmascarar(cuenta.getNumeroCuenta()), monto, sesion.usuarioId());
        return aRespuesta(recarga, cuenta);
    }

    public HistorialRecargasResponse historial(String authorization, HistorialRecargasRequest request) {
        exigirEjecutivo(authorization);
        int pagina = request.getPagina() == null ? 0 : request.getPagina();
        Page<RecargaSaldoEntity> resultado = transactionTemplate.execute(status ->
                recargaRepository.findAllByOrderByFechaDesc(PageRequest.of(pagina, TAMANIO_PAGINA)));
        return HistorialRecargasResponse.builder()
                .recargas(resultado.getContent().stream().map(r -> aRespuesta(r, r.getCuenta())).toList())
                .pagina(pagina)
                .totalPaginas(resultado.getTotalPages())
                .totalRecargas(resultado.getTotalElements())
                .build();
    }

    // ---------------------------------------------------------------- apoyo

    private SesionUsuario exigirEjecutivo(String authorization) {
        SesionUsuario sesion = sesionService.autenticar(authorization);
        if (!sesion.esEjecutivo()) {
            throw new AccesoDenegadoException("Solo un administrador puede recargar saldo.");
        }
        return sesion;
    }

    private CuentaEntity buscarCuenta(String numeroCuenta) {
        return transactionTemplate.execute(status -> cuentaRepository.findConClienteByNumeroCuenta(numeroCuenta))
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe una cuenta con ese número.",
                        MotivoPago.CUENTA_NO_ENCONTRADA));
    }

    private static boolean recargable(CuentaEntity cuenta) {
        ClienteEntity cliente = cuenta.getCliente();
        return "ACTIVA".equals(cuenta.getEstatus()) && cliente != null && Boolean.TRUE.equals(cliente.getActivo());
    }

    private RecargaSaldoResponse repetir(RecargaSaldoEntity previa, SesionUsuario sesion, String huella) {
        if (!Objects.equals(previa.getEjecutivoId(), sesion.usuarioId()) || !Objects.equals(previa.getHashSolicitud(), huella)) {
            throw new OperacionDuplicadaException("La Idempotency-Key ya se utilizó con otra recarga. Genere una nueva.",
                    MotivoPago.LLAVE_OTROS_DATOS);
        }
        return aRespuesta(previa, previa.getCuenta());
    }

    private static RecargaSaldoResponse aRespuesta(RecargaSaldoEntity r, CuentaEntity cuenta) {
        return RecargaSaldoResponse.builder()
                .folio(folio(r.getId()))
                .numCuentaEnmascarada(PagoServicioMapper.enmascarar(cuenta.getNumeroCuenta()))
                .titular(titular(cuenta.getCliente()))
                .monto(r.getMonto())
                .saldoResultante(r.getSaldoResultante())
                .fecha(r.getFecha())
                .build();
    }

    static String folio(Long id) {
        return String.format(Locale.ROOT, "R-%06d", id);
    }

    private static String titular(ClienteEntity c) {
        if (c == null) {
            return null;
        }
        StringBuilder nombre = new StringBuilder(c.getNombre());
        if (c.getApellidoPaterno() != null) {
            nombre.append(' ').append(c.getApellidoPaterno());
        }
        if (c.getApellidoMaterno() != null) {
            nombre.append(' ').append(c.getApellidoMaterno());
        }
        return nombre.toString();
    }

    private static String normalizarLlave(String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new ReglaNegocioException("El encabezado Idempotency-Key es obligatorio y debe ser un UUID. "
                    + "Genere uno nuevo por cada recarga.", MotivoPago.IDEMPOTENCY_KEY_INVALIDA);
        }
        return idempotencyKey.toLowerCase(Locale.ROOT);
    }

    static String huella(String numeroCuenta, BigDecimal monto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((numeroCuenta + "|" + monto.toPlainString()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no disponible", exception);
        }
    }
}

package com.proyecto.servicios.entity.onboarding;

import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.enums.OrigenConfirmacion;
import com.proyecto.servicios.enums.OrigenMonto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pago de servicio enviado (o por enviar) a GestoPago. La fila se crea en la misma transacción que reserva el
 * saldo, ANTES de llamar al proveedor, para que toda operación quede trazable y se pueda conciliar.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_transaccion_pago", indexes = {
        @Index(name = "idx_transaccion_cuenta", columnList = "cuenta_id"),
        @Index(name = "idx_transaccion_estatus_fecha", columnList = "estatus, fecha_transaccion")
})
public class TransaccionPagoEntity {

    /** Longitud fija del identificador de operación (upc) enviado a GestoPago. */
    public static final int LONGITUD_UPC = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_id", nullable = false)
    @ToString.Exclude
    private CuentaEntity cuenta;

    /** Usuario autenticado que solicitó el pago (auditoría). */
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "id_servicio", nullable = false)
    private Integer idServicio;

    @Column(name = "id_producto", nullable = false)
    private Integer idProducto;

    @Column(name = "monto", nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    @Column(name = "telefono", length = ClienteEntity.LONGITUD_TELEFONO)
    private String telefono;

    @Column(name = "referencia", length = 100)
    private String referencia;

    @Column(name = "upc", nullable = false, unique = true, length = LONGITUD_UPC)
    private String upc;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 36)
    private String idempotencyKey;

    @Column(name = "hash_solicitud", nullable = false, length = 64)
    private String hashSolicitud;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus", nullable = false, length = 15)
    private EstatusTransaccionPago estatus;

    @Column(name = "codigo_respuesta", length = 10)
    private String codigoRespuesta;

    @Column(name = "texto_respuesta", length = 500)
    private String textoRespuesta;

    /** Fecha en que se reservó el saldo y se creó la operación. */
    @Column(name = "fecha_transaccion", nullable = false)
    private LocalDateTime fechaTransaccion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    // ---------- Sprint 3: datos del proveedor, instantanea del catalogo y control de confirmTx (todo nulable) ----------

    /** NUM_AUTORIZACION del proveedor: el folio que ve el usuario. */
    @Column(name = "folio", length = 30)
    private String folio;

    @Column(name = "id_tx", length = 30)
    private String idTx;

    @Column(name = "comision", precision = 15, scale = 2)
    private BigDecimal comision;

    @Column(name = "monto_proveedor", precision = 15, scale = 2)
    private BigDecimal montoProveedor;

    @Column(name = "fecha_proveedor", length = 30)
    private String fechaProveedor;

    /** PIN de tarjeta de regalo CIFRADO (AES). Nunca se registra en logs ni se imprime. */
    @Column(name = "pin_servicio", length = 512)
    @ToString.Exclude
    private String pinServicio;

    @Column(name = "leyenda", length = 500)
    private String leyenda;

    @Column(name = "nombre_servicio", length = 150)
    private String nombreServicio;

    @Column(name = "nombre_producto", length = 150)
    private String nombreProducto;

    @Column(name = "tipo_front")
    private Integer tipoFront;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen_monto", length = 12)
    private OrigenMonto origenMonto;

    /** Motivo enumerado cuando la operacion termina RECHAZADA. */
    @Enumerated(EnumType.STRING)
    @Column(name = "motivo", length = 40)
    private MotivoPago motivo;

    /** JSON exacto enviado a sendTx: confirmTx reenvia los mismos parametros. Nunca se registra en logs. */
    @Column(name = "payload_sendtx", columnDefinition = "TEXT")
    @ToString.Exclude
    private String payloadSendtx;

    /**
     * Quién dio por aplicado el pago: GESTOPAGO (el proveedor lo aplicó) o SIMULADO (GestoPago no funcionó y el
     * backend lo dio por exitoso). Nulo mientras la operación no esté CONFIRMADA.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "origen_confirmacion", length = 10)
    private OrigenConfirmacion origenConfirmacion;

    @Column(name = "intentos_confirmacion", nullable = false)
    @Builder.Default
    private Integer intentosConfirmacion = 0;

    @Column(name = "fecha_ultima_confirmacion")
    private LocalDateTime fechaUltimaConfirmacion;
}

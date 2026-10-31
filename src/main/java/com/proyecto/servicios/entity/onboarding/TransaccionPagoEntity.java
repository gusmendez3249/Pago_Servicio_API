package com.proyecto.servicios.entity.onboarding;

import com.proyecto.servicios.enums.EstatusTransaccionPago;
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
}

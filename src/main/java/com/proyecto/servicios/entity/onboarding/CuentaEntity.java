package com.proyecto.servicios.entity.onboarding;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_cuenta", indexes = {
        @Index(name = "idx_cuenta_numero", columnList = "numero_cuenta")
})
public class CuentaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_cuenta", nullable = false, unique = true, length = 20)
    private String numeroCuenta;

    @Column(name = "saldo", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal saldo = new BigDecimal("1000.00");

    @Column(name = "estatus", nullable = false, length = 20)
    @Builder.Default
    private String estatus = "ACTIVA";

    @Column(name = "fecha_apertura", nullable = false, updatable = false)
    private LocalDateTime fechaApertura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private ClienteEntity cliente;

    @PrePersist
    public void prePersist() {
        if (this.fechaApertura == null) {
            this.fechaApertura = LocalDateTime.now();
        }
        if (this.estatus == null) {
            this.estatus = "ACTIVA";
        }
        if (this.saldo == null) {
            this.saldo = new BigDecimal("1000.00");
        }
    }
}

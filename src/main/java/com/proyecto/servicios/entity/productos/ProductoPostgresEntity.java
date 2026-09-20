package com.proyecto.servicios.entity.productos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "productos")
public class ProductoPostgresEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tipo_front", nullable = false)
    private Integer tipoFront;

    @Column(name = "servicio")
    private String servicio;

    @Column(name = "producto")
    private String producto;

    @Column(name = "id_servicio")
    private Integer idServicio;

    @Column(name = "id_producto")
    private Integer idProducto;

    @Column(name = "id_cat_tipo_servicio")
    private Integer idCatTipoServicio;

    @Column(name = "has_digito_verificador")
    private Boolean hasDigitoVerificador;

    @Column(name = "precio")
    private BigDecimal precio;

    @Column(name = "show_ayuda")
    private Boolean showAyuda;

    @Column(name = "tipo_referencia")
    private String tipoReferencia;

    @Column(name = "legend", columnDefinition = "TEXT")
    private String legend;
}
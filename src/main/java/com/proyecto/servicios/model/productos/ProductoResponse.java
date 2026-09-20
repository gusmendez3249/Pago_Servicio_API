package com.proyecto.servicios.model.productos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductoResponse {

    private String servicio;
    private String producto;
    private Integer idServicio;
    private Integer idProducto;
    private Integer idCatTipoServicio;
    private Integer tipoFront;
    private Boolean hasDigitoVerificador;
    private BigDecimal precio;
    private Boolean showAyuda;
    private String tipoReferencia;
    private String legend;
}
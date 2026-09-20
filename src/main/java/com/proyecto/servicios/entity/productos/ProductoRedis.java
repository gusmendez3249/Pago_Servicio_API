package com.proyecto.servicios.entity.productos;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.math.BigDecimal;

@Data
@RedisHash("productos")
public class ProductoRedis {

    @Id
    private String id;

    private Integer tipoFront;
    private String servicio;
    private String producto;
    private Integer idServicio;
    private Integer idProducto;
    private Integer idCatTipoServicio;
    private Boolean hasDigitoVerificador;
    private BigDecimal precio;
    private Boolean showAyuda;
    private String tipoReferencia;
    private String legend;
}
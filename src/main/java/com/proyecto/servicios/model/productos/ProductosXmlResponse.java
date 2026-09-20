package com.proyecto.servicios.model.productos;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

@Data
@JacksonXmlRootElement(localName = "RESPONSE")
public class ProductosXmlResponse {

    @JacksonXmlProperty(localName = "MENSAJE")
    private MensajeXml mensaje;

    @JacksonXmlProperty(localName = "PRODUCTOS")
    private ProductosXml productos;
}
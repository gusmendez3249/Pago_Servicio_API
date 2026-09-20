package com.proyecto.servicios.model.productos;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class MensajeXml {

    @JacksonXmlProperty(localName = "CODIGO")
    private String codigo;

    @JacksonXmlProperty(localName = "TEXTO")
    private String texto;
}
package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.model.gestopago.DatosProveedor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import org.xml.sax.SAXException;

/**
 * Lectura del XML con que responden sendTx y confirmTx. GestoPago responde HTTP 200 tanto en éxito como en error:
 * lo único que decide es el nodo CODIGO. El nombre de los nodos se compara sin distinguir mayúsculas (p. ej. {@code legend}).
 * El nodo SALDO (saldo del distribuidor) no se lee nunca.
 */
final class RespuestaXmlGestoPago {

    private final Document documento;

    private RespuestaXmlGestoPago(Document documento) {
        this.documento = documento;
    }

    /** @throws IllegalArgumentException si el cuerpo no es un XML válido */
    static RespuestaXmlGestoPago leer(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Protección contra XXE: la respuesta viene de un tercero
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return new RespuestaXmlGestoPago(factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml)));
        } catch (ParserConfigurationException | SAXException | IOException | RuntimeException exception) {
            throw new IllegalArgumentException("XML ilegible", exception);
        }
    }

    String codigo() {
        return texto("CODIGO");
    }

    String textoProveedor() {
        return texto("TEXTO");
    }

    /** Folio, id de transacción, comisión, PIN y leyenda de una operación aplicada. */
    DatosProveedor datos() {
        return new DatosProveedor(texto("ID_TX"), texto("NUM_AUTORIZACION"), decimal("COMISION"), decimal("MONTO"),
                texto("FECHA"), texto("REFERENCIA"), texto("PIN"), texto("legend"));
    }

    /** Texto del primer nodo con ese nombre (sin distinguir mayúsculas), o nulo si no existe o está vacío. */
    String texto(String nombre) {
        NodeList nodos = documento.getElementsByTagName("*");
        for (int i = 0; i < nodos.getLength(); i++) {
            Element elemento = (Element) nodos.item(i);
            if (nombre.equalsIgnoreCase(elemento.getTagName())) {
                String valor = elemento.getTextContent() == null ? "" : elemento.getTextContent().trim();
                return valor.isEmpty() ? null : valor;
            }
        }
        return null;
    }

    private BigDecimal decimal(String nombre) {
        String valor = texto(nombre);
        if (valor == null) {
            return null;
        }
        try {
            return new BigDecimal(valor.replace(",", "").replace("$", "").trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}

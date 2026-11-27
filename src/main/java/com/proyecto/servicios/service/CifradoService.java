package com.proyecto.servicios.service;

public interface CifradoService {

    /**
     * Cifra el texto con AES-256-CBC (PKCS5/PKCS7). La llave y el IV se leen en Base64 de la configuración;
     * el texto se cifra en UTF-8 y el resultado se devuelve en Base64 sin saltos de línea.
     */
    String cifrar(String textoPlano);

    /**
     * Operación inversa de {@link #cifrar}: recibe el Base64 cifrado y devuelve el texto original en UTF-8.
     * Se usa para entregar al titular el PIN de una tarjeta de regalo guardado cifrado; el resultado nunca se registra.
     *
     * @throws com.proyecto.servicios.exception.IntegrationException si la configuración es inválida o el texto no se puede descifrar
     */
    String descifrar(String textoCifradoBase64);
}

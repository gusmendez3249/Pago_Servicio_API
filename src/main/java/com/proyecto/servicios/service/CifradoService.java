package com.proyecto.servicios.service;

public interface CifradoService {

    /**
     * Cifra el texto con AES-256-CBC (PKCS5/PKCS7). La llave y el IV se leen en Base64 de la configuración;
     * el texto se cifra en UTF-8 y el resultado se devuelve en Base64 sin saltos de línea.
     */
    String cifrar(String textoPlano);
}

package com.proyecto.servicios.client;

import com.proyecto.servicios.config.SendTxFeignConfig;
import feign.Request;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "sendTxClient",
        url = "${gestopago.base-url}",
        configuration = SendTxFeignConfig.class
)
public interface SendTxClient {

    /** El cuerpo (random, signed) viaja como application/x-www-form-urlencoded; la respuesta XML se lee como bytes. */
    @PostMapping(
            value = "${gestopago.sendtx.endpoint}",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    byte[] enviarTransaccion(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestBody MultiValueMap<String, String> formulario
    );

    /**
     * confirmTx: consulta si una operación de sendTx se aplicó. Mismo formulario (random + signed) con los mismos
     * parámetros del sendTx original. Es una consulta: usa su propio timeout de lectura (15 s) en lugar del de
     * sendTx (65 s) mediante el parámetro {@code Request.Options}.
     */
    @PostMapping(
            value = "${gestopago.confirmtx.endpoint:/sistema/service/confirmTx.do}",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    byte[] confirmarTransaccion(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestBody MultiValueMap<String, String> formulario,
            Request.Options opciones
    );
}

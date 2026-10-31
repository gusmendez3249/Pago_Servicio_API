package com.proyecto.servicios.client;

import com.proyecto.servicios.config.SendTxFeignConfig;
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
}

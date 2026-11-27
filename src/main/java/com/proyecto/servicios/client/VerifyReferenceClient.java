package com.proyecto.servicios.client;

import com.proyecto.servicios.config.VerifyReferenceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "verifyReferenceClient",
        url = "${gestopago.base-url}",
        configuration = VerifyReferenceFeignConfig.class
)
public interface VerifyReferenceClient {

    /** El cuerpo (random, signed) viaja como application/x-www-form-urlencoded; la respuesta XML se lee como bytes. */
    @PostMapping(
            value = "${gestopago.verifyref.endpoint}",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    byte[] verificarReferencia(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestBody MultiValueMap<String, String> formulario
    );
}

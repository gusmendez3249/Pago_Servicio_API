package com.proyecto.servicios.config;

import feign.Client;
import feign.Request;
import feign.Retryer;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Beans de infraestructura: serialización de Redis y opciones de los clientes Feign. */
class RedisYFeignConfigTest {

    @Test
    void redisTemplateUsaLlavesDeTextoYValoresJson() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);

        RedisTemplate<String, Object> template = new RedisConfig().redisTemplate(factory);

        assertSame(factory, template.getConnectionFactory());
        assertInstanceOf(StringRedisSerializer.class, template.getKeySerializer());
        assertInstanceOf(StringRedisSerializer.class, template.getHashKeySerializer());
        assertInstanceOf(GenericJackson2JsonRedisSerializer.class, template.getValueSerializer());
        assertInstanceOf(GenericJackson2JsonRedisSerializer.class, template.getHashValueSerializer());
    }

    @Test
    void productosSigueRedireccionesConLosTimeoutsConfigurados() {
        Request.Options options = new ProductosFeignConfig(1000, 2000).requestOptions();

        assertEquals(1000, options.connectTimeoutMillis());
        assertEquals(2000, options.readTimeoutMillis());
        assertTrue(options.isFollowRedirects());
    }

    @Test
    void sendTxNoReintentaNiSigueRedirecciones() {
        SendTxFeignConfig config = new SendTxFeignConfig();

        Request.Options options = config.sendTxRequestOptions(5000, 65000);

        assertEquals(65000, options.readTimeoutMillis());
        assertFalse(options.isFollowRedirects(), "un POST financiero no debe redirigirse");
        assertSame(Retryer.NEVER_RETRY, config.sendTxRetryer());
        assertInstanceOf(Client.Default.class, config.sendTxFeignClient());
    }
}

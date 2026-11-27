package com.proyecto.servicios.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Limitador de tasa con Redis (script Lua atómico) y respaldo en memoria cuando Redis falla. */
class LimitadorTasaTest {

    private static final Duration VENTANA = Duration.ofMinutes(1);

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valores = mock(ValueOperations.class);
    private final AtomicLong ahora = new AtomicLong(1_000_000);
    private LimitadorTasa limitador;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valores);
        limitador = new LimitadorTasa(redis, ahora::get);
    }

    @SuppressWarnings("unchecked")
    private void redisCuenta(long n, long ttlMs) {
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(List.of(n, ttlMs));
    }

    @Test
    void dentroDelLimiteDevuelveCeroYUsaElPrefijo() {
        redisCuenta(3, 40_000);

        assertEquals(0, limitador.registrar("login:ip", 5, VENTANA));
        verify(redis).execute(any(RedisScript.class), eq(List.of("rl:login:ip")), eq("60000"));
    }

    @Test
    void alExcederDevuelveLosSegundosRestantesRedondeadosHaciaArriba() {
        redisCuenta(6, 1_500);
        assertEquals(2, limitador.registrar("login:ip", 5, VENTANA));

        redisCuenta(6, -1);
        assertEquals(1, limitador.registrar("login:ip", 5, VENTANA), "TTL negativo: al menos 1 s");
    }

    @Test
    @SuppressWarnings("unchecked")
    void siRedisFallaCuentaEnMemoriaYNoLoReintentaDuranteLaPausa() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("caído"));

        for (int i = 0; i < 2; i++) {
            assertEquals(0, limitador.registrar("pago:s", 2, VENTANA));
        }
        assertEquals(60, limitador.registrar("pago:s", 2, VENTANA), "la memoria sigue limitando");
        verify(redis, times(1)).execute(any(RedisScript.class), anyList(), anyString());

        ahora.addAndGet(30_001);
        redisCuenta(1, 60_000);
        assertEquals(0, limitador.registrar("pago:s", 2, VENTANA));
        verify(redis, times(2)).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    void bloqueoConsultaSinContar() {
        when(valores.get("rl:k")).thenReturn(null, "2", "5");
        when(redis.getExpire("rl:k", TimeUnit.MILLISECONDS)).thenReturn(4_200L);

        assertEquals(0, limitador.bloqueo("k", 5), "sin contador");
        assertEquals(0, limitador.bloqueo("k", 5), "debajo del máximo");
        assertEquals(5, limitador.bloqueo("k", 5), "bloqueado: 4.2 s -> 5 s");
    }

    @Test
    void bloqueoConTtlDesconocidoYConRedisCaido() {
        when(valores.get("rl:k")).thenReturn("9");
        when(redis.getExpire("rl:k", TimeUnit.MILLISECONDS)).thenReturn(null);
        assertEquals(1, limitador.bloqueo("k", 5));

        when(valores.get("rl:k")).thenThrow(new RedisConnectionFailureException("caído"));
        assertEquals(0, limitador.bloqueo("k", 5), "sin datos en memoria no hay bloqueo");
    }

    @Test
    void limpiarBorraEnRedisYEnMemoria() {
        limitador.limpiar("k");
        verify(redis).delete("rl:k");

        doThrow(new RedisConnectionFailureException("caído")).when(redis).delete(anyString());
        limitador.limpiar("k");
    }

    @Test
    void soloMemoriaNoTocaRedis() {
        LimitadorTasa local = LimitadorTasa.enMemoria(ahora::get);
        assertEquals(0, local.registrar("k", 1, VENTANA));
        assertEquals(60, local.registrar("k", 1, VENTANA));
        assertEquals(60, local.bloqueo("k", 1));
        local.limpiar("k");
        assertEquals(0, local.bloqueo("k", 1));
    }
}

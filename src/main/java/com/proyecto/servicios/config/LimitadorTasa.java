package com.proyecto.servicios.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Contadores de límites de tasa y de intentos fallidos COMPARTIDOS entre instancias: viven en Redis (INCR + PEXPIRE
 * atómicos con un script Lua), así que el límite no se multiplica con varias instancias ni se reinicia con cada
 * despliegue. Si Redis no responde, se usa un contador en memoria y Redis se deja de consultar 30 s (circuit breaker),
 * para no sumar su timeout a cada petición.
 */
@Slf4j
@Component
public class LimitadorTasa {

    private static final String PREFIJO = "rl:";
    private static final long PAUSA_REDIS_MS = 30_000;
    private static final DefaultRedisScript<List> CONTAR = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1]) "
                    + "if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                    + "return {n, redis.call('PTTL', KEYS[1])}", List.class);

    private final StringRedisTemplate redis;
    private final LimitadorVentanaFija memoria;
    private final LongSupplier reloj;
    private volatile long redisPausadoHasta;

    @Autowired
    public LimitadorTasa(StringRedisTemplate redis) {
        this(redis, System::currentTimeMillis);
    }

    LimitadorTasa(StringRedisTemplate redis, LongSupplier reloj) {
        this.redis = redis;
        this.reloj = reloj;
        this.memoria = new LimitadorVentanaFija(reloj);
    }

    /** Solo memoria (pruebas y usos sin Redis). */
    public static LimitadorTasa enMemoria(LongSupplier reloj) {
        return new LimitadorTasa(null, reloj);
    }

    /**
     * Cuenta un evento para la clave dentro de la ventana.
     * @return 0 si está dentro del límite; si lo excede, los segundos que faltan para que se libere (mínimo 1)
     */
    public long registrar(String clave, int maximo, Duration ventana) {
        if (redisUsable()) {
            try {
                List<?> r = redis.execute(CONTAR, List.of(PREFIJO + clave), String.valueOf(ventana.toMillis()));
                long n = ((Number) r.get(0)).longValue();
                long ttl = ((Number) r.get(1)).longValue();
                return n <= maximo ? 0 : Math.max(1, (Math.max(ttl, 0) + 999) / 1000);
            } catch (RuntimeException exception) {
                pausarRedis(exception);
            }
        }
        return memoria.registrar(clave, maximo, ventana.toMillis());
    }

    /** Segundos de bloqueo vigentes (el contador ya alcanzó {@code maximo}) sin contar un evento nuevo; 0 si no hay. */
    public long bloqueo(String clave, int maximo) {
        if (redisUsable()) {
            try {
                String valor = redis.opsForValue().get(PREFIJO + clave);
                if (valor == null || Long.parseLong(valor) < maximo) {
                    return 0;
                }
                Long ttlMs = redis.getExpire(PREFIJO + clave, java.util.concurrent.TimeUnit.MILLISECONDS);
                return Math.max(1, ((ttlMs == null ? 0 : Math.max(ttlMs, 0)) + 999) / 1000);
            } catch (RuntimeException exception) {
                pausarRedis(exception);
            }
        }
        return memoria.bloqueo(clave, maximo);
    }

    public void limpiar(String clave) {
        memoria.limpiar(clave);
        if (redisUsable()) {
            try {
                redis.delete(PREFIJO + clave);
            } catch (RuntimeException exception) {
                pausarRedis(exception);
            }
        }
    }

    private boolean redisUsable() {
        return redis != null && reloj.getAsLong() >= redisPausadoHasta;
    }

    private void pausarRedis(RuntimeException exception) {
        redisPausadoHasta = reloj.getAsLong() + PAUSA_REDIS_MS;
        log.warn("Redis no respondió en el limitador de tasa; se usan contadores en memoria 30 s. causa={}",
                exception.getClass().getSimpleName());
    }
}

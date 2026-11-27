package com.proyecto.servicios.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Contador de peticiones por clave en ventanas fijas, en memoria. Es el respaldo de {@link LimitadorTasa} cuando Redis
 * no responde. Con más de {@value #MAX_CLAVES} claves se descartan las ventanas vencidas; si aun así sigue lleno (rocío
 * de IPs distintas), las claves nuevas comparten un único contador global para no crecer sin límite.
 */
final class LimitadorVentanaFija {

    static final long VENTANA_MS = 60_000;
    static final int MAX_CLAVES = 10_000;
    private static final String CLAVE_DESBORDE = "__desborde__";

    private final LongSupplier reloj;
    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    LimitadorVentanaFija(LongSupplier reloj) {
        this.reloj = reloj;
    }

    /** Cuenta una petición en una ventana de 1 minuto. */
    long registrar(String clave, int maximo) {
        return registrar(clave, maximo, VENTANA_MS);
    }

    /**
     * Cuenta una petición para la clave.
     * @return 0 si está dentro del límite; si lo excede, los segundos que faltan para que se libere (mínimo 1)
     */
    long registrar(String clave, int maximo, long ventanaMs) {
        long ahora = reloj.getAsLong();
        if (ventanas.size() > MAX_CLAVES) {
            ventanas.values().removeIf(v -> v.vencida(ahora));
        }
        String efectiva = ventanas.size() > MAX_CLAVES && !ventanas.containsKey(clave) ? CLAVE_DESBORDE : clave;
        Ventana ventana = ventanas.compute(efectiva,
                (k, v) -> v == null || v.vencida(ahora) ? new Ventana(ahora, ventanaMs, 1) : v.contar());
        return ventana.intentos <= maximo ? 0 : ventana.segundosRestantes(ahora);
    }

    /** Segundos de bloqueo vigentes para la clave sin contar una petición nueva (0 si no está bloqueada). */
    long bloqueo(String clave, int maximo) {
        long ahora = reloj.getAsLong();
        Ventana ventana = ventanas.get(clave);
        if (ventana == null || ventana.vencida(ahora) || ventana.intentos < maximo) {
            return 0;
        }
        return ventana.segundosRestantes(ahora);
    }

    void limpiar(String clave) {
        ventanas.remove(clave);
    }

    private record Ventana(long inicio, long duracionMs, int intentos) {
        Ventana contar() {
            return new Ventana(inicio, duracionMs, intentos + 1);
        }

        boolean vencida(long ahora) {
            return ahora - inicio >= duracionMs;
        }

        long segundosRestantes(long ahora) {
            return Math.max(1, (inicio + duracionMs - ahora + 999) / 1000);
        }
    }
}

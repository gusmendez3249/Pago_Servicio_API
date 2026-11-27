/** Genera una clave de idempotencia nueva (una por intento lógico de pago). */
export function nuevaIdempotencyKey(): string {
  return crypto.randomUUID();
}

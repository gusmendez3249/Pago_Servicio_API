package com.proyecto.servicios.repositorys.onboarding;

import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransaccionPagoRepository extends JpaRepository<TransaccionPagoEntity, Long> {

    /** Con la cuenta ya cargada (la relación es LAZY y open-in-view está desactivado). */
    @Query("select t from TransaccionPagoEntity t join fetch t.cuenta where t.idempotencyKey = :llave")
    Optional<TransaccionPagoEntity> findConCuentaByIdempotencyKey(@Param("llave") String idempotencyKey);

    @Query("select t from TransaccionPagoEntity t join fetch t.cuenta where t.upc = :upc")
    Optional<TransaccionPagoEntity> findConCuentaByUpc(@Param("upc") String upc);

    boolean existsByUpc(String upc);

    List<TransaccionPagoEntity> findByEstatusAndFechaTransaccionBefore(EstatusTransaccionPago estatus, LocalDateTime limite);

    /**
     * Cambia el estatus solo si la operación sigue en alguno de los estatus de origen. Devuelve 0 si otro proceso
     * (la petición original o la conciliación) ya la resolvió: así un pago nunca se confirma ni se reintegra dos veces.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TransaccionPagoEntity t set t.estatus = :nuevo, t.codigoRespuesta = :codigo, "
            + "t.textoRespuesta = :texto, t.fechaActualizacion = :fecha "
            + "where t.id = :id and t.estatus in :origen")
    int cambiarEstatus(@Param("id") Long id,
                       @Param("origen") Collection<EstatusTransaccionPago> origen,
                       @Param("nuevo") EstatusTransaccionPago nuevo,
                       @Param("codigo") String codigo,
                       @Param("texto") String texto,
                       @Param("fecha") LocalDateTime fecha);
}

package com.proyecto.servicios.repositorys.onboarding;

import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.enums.OrigenConfirmacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransaccionPagoRepository
        extends JpaRepository<TransaccionPagoEntity, Long>, JpaSpecificationExecutor<TransaccionPagoEntity> {

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

    /**
     * Confirma la operación guardando los datos del proveedor (folio, id_tx, comisión, PIN cifrado, leyenda), con la
     * misma condición de estatus abierto que {@link #cambiarEstatus}: nunca se confirma dos veces.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TransaccionPagoEntity t set t.estatus = :nuevo, t.codigoRespuesta = :codigo, "
            + "t.textoRespuesta = :texto, t.fechaActualizacion = :fecha, t.folio = :folio, t.idTx = :idTx, "
            + "t.comision = :comision, t.montoProveedor = :montoProveedor, t.fechaProveedor = :fechaProveedor, "
            + "t.pinServicio = :pin, t.leyenda = :leyenda, t.origenConfirmacion = :origenConfirmacion "
            + "where t.id = :id and t.estatus in :origen")
    int confirmarConDatos(@Param("id") Long id,
                          @Param("origen") Collection<EstatusTransaccionPago> origen,
                          @Param("nuevo") EstatusTransaccionPago nuevo,
                          @Param("codigo") String codigo,
                          @Param("texto") String texto,
                          @Param("fecha") LocalDateTime fecha,
                          @Param("folio") String folio,
                          @Param("idTx") String idTx,
                          @Param("comision") BigDecimal comision,
                          @Param("montoProveedor") BigDecimal montoProveedor,
                          @Param("fechaProveedor") String fechaProveedor,
                          @Param("pin") String pin,
                          @Param("leyenda") String leyenda,
                          @Param("origenConfirmacion") OrigenConfirmacion origenConfirmacion);

    /** Confirma sin datos del proveedor (conciliación manual), registrando quién la dio por aplicada. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TransaccionPagoEntity t set t.estatus = :nuevo, t.codigoRespuesta = :codigo, "
            + "t.textoRespuesta = :texto, t.fechaActualizacion = :fecha, t.origenConfirmacion = :origenConfirmacion "
            + "where t.id = :id and t.estatus in :origen")
    int confirmarSinDatos(@Param("id") Long id,
                          @Param("origen") Collection<EstatusTransaccionPago> origen,
                          @Param("nuevo") EstatusTransaccionPago nuevo,
                          @Param("codigo") String codigo,
                          @Param("texto") String texto,
                          @Param("fecha") LocalDateTime fecha,
                          @Param("origenConfirmacion") OrigenConfirmacion origenConfirmacion);

    /** Igual que {@link #cambiarEstatus} pero registrando el motivo enumerado del rechazo. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TransaccionPagoEntity t set t.estatus = :nuevo, t.codigoRespuesta = :codigo, "
            + "t.textoRespuesta = :texto, t.fechaActualizacion = :fecha, t.motivo = :motivo "
            + "where t.id = :id and t.estatus in :origen")
    int cambiarEstatusConMotivo(@Param("id") Long id,
                                @Param("origen") Collection<EstatusTransaccionPago> origen,
                                @Param("nuevo") EstatusTransaccionPago nuevo,
                                @Param("codigo") String codigo,
                                @Param("texto") String texto,
                                @Param("fecha") LocalDateTime fecha,
                                @Param("motivo") MotivoPago motivo);

    /**
     * Reclama de forma atómica el derecho a consultar confirmTx: solo prospera si la operación sigue INDETERMINADA y
     * pasó el tiempo mínimo desde la última consulta ({@code limite}). Así el planificador y /pagos/estado nunca
     * consultan a la vez. Devuelve 0 si otro proceso ya la reclamó o la operación se resolvió.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TransaccionPagoEntity t set t.intentosConfirmacion = t.intentosConfirmacion + 1, "
            + "t.fechaUltimaConfirmacion = :ahora "
            + "where t.id = :id and t.estatus = com.proyecto.servicios.enums.EstatusTransaccionPago.INDETERMINADA "
            + "and (t.fechaUltimaConfirmacion is null or t.fechaUltimaConfirmacion <= :limite)")
    int reclamarConsulta(@Param("id") Long id, @Param("ahora") LocalDateTime ahora, @Param("limite") LocalDateTime limite);
}

package com.proyecto.servicios.repositorys.onboarding;

import com.proyecto.servicios.entity.onboarding.RecargaSaldoEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.Optional;

public interface RecargaSaldoRepository extends JpaRepository<RecargaSaldoEntity, Long> {

    @EntityGraph(attributePaths = {"cuenta", "cuenta.cliente"})
    Optional<RecargaSaldoEntity> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"cuenta", "cuenta.cliente"})
    Page<RecargaSaldoEntity> findAllByOrderByFechaDesc(Pageable pageable);

    /** Total abonado a una cuenta desde una fecha (tope diario por cuenta). */
    @Query("select coalesce(sum(r.monto), 0) from RecargaSaldoEntity r where r.cuenta.id = :cuentaId and r.fecha >= :desde")
    BigDecimal sumarPorCuentaDesde(@Param("cuentaId") Long cuentaId, @Param("desde") LocalDateTime desde);

    /** Total abonado por un ejecutivo desde una fecha (tope diario por ejecutivo). */
    @Query("select coalesce(sum(r.monto), 0) from RecargaSaldoEntity r where r.ejecutivoId = :ejecutivoId and r.fecha >= :desde")
    BigDecimal sumarPorEjecutivoDesde(@Param("ejecutivoId") Long ejecutivoId, @Param("desde") LocalDateTime desde);
}

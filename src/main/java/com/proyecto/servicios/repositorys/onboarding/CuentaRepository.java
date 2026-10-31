package com.proyecto.servicios.repositorys.onboarding;

import com.proyecto.servicios.entity.onboarding.CuentaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CuentaRepository extends JpaRepository<CuentaEntity, Long> {

    Optional<CuentaEntity> findByNumeroCuenta(String numeroCuenta);

    List<CuentaEntity> findByEstatus(String estatus);

    List<CuentaEntity> findByClienteId(Long clienteId);

    List<CuentaEntity> findByClienteIdIn(java.util.Collection<Long> clienteIds);

    boolean existsByNumeroCuenta(String numeroCuenta);

    /** Cuenta con su cliente ya cargado (la relación es LAZY y open-in-view está desactivado). */
    @Query("select c from CuentaEntity c join fetch c.cliente where c.numeroCuenta = :numeroCuenta")
    Optional<CuentaEntity> findConClienteByNumeroCuenta(@Param("numeroCuenta") String numeroCuenta);

    /** Descuenta el saldo de forma atómica; devuelve 0 si ya no alcanza el saldo o la cuenta dejó de estar activa. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CuentaEntity c set c.saldo = c.saldo - :monto "
            + "where c.id = :id and c.saldo >= :monto and c.estatus = 'ACTIVA'")
    int descontarSaldo(@Param("id") Long id, @Param("monto") BigDecimal monto);

    /** Devuelve a la cuenta un monto reservado de una operación que no se aplicó. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CuentaEntity c set c.saldo = c.saldo + :monto where c.id = :id")
    int reintegrarSaldo(@Param("id") Long id, @Param("monto") BigDecimal monto);

    @Query("select c.saldo from CuentaEntity c where c.id = :id")
    Optional<BigDecimal> findSaldoById(@Param("id") Long id);
}

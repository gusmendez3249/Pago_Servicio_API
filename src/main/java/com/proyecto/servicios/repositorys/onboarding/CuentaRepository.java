package com.proyecto.servicios.repositorys.onboarding;

import com.proyecto.servicios.entity.onboarding.CuentaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CuentaRepository extends JpaRepository<CuentaEntity, Long> {

    Optional<CuentaEntity> findByNumeroCuenta(String numeroCuenta);

    List<CuentaEntity> findByEstatus(String estatus);

    List<CuentaEntity> findByClienteId(Long clienteId);

    boolean existsByNumeroCuenta(String numeroCuenta);
}

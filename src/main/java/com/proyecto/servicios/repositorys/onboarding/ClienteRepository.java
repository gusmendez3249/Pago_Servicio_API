package com.proyecto.servicios.repositorys.onboarding;

import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<ClienteEntity, Long>, JpaSpecificationExecutor<ClienteEntity> {

    Optional<ClienteEntity> findByCurp(String curp);

    Optional<ClienteEntity> findByRfc(String rfc);

    Optional<ClienteEntity> findByCorreo(String correo);

    List<ClienteEntity> findByActivoTrue();

    List<ClienteEntity> findByFechaRegistroBetween(LocalDateTime inicio, LocalDateTime fin);

    boolean existsByCurp(String curp);

    boolean existsByRfc(String rfc);

    boolean existsByCorreo(String correo);
}

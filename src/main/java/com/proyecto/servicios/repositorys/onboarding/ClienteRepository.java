package com.proyecto.servicios.repositorys.onboarding;

import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** Bloquea la fila del cliente: los intentos del PIN se cuentan sin perder incrementos entre peticiones simultáneas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClienteEntity c where c.id = :id")
    Optional<ClienteEntity> findByIdParaActualizar(@Param("id") Long id);
}

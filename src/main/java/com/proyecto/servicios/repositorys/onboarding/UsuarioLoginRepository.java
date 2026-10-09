package com.proyecto.servicios.repositorys.onboarding;

import com.proyecto.servicios.entity.onboarding.UsuarioLoginEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioLoginRepository extends JpaRepository<UsuarioLoginEntity, Long> {

    Optional<UsuarioLoginEntity> findByUsername(String username);

    Optional<UsuarioLoginEntity> findByFaceIdBiometrico(Long faceIdBiometrico);

    Optional<UsuarioLoginEntity> findByUsernameAndFaceIdBiometrico(String username, Long faceIdBiometrico);

    boolean existsByUsername(String username);

    Optional<UsuarioLoginEntity> findByClienteId(Long clienteId);

    List<UsuarioLoginEntity> findByIsLoggedInTrue();

    /**
     * Suma un intento fallido en una sola sentencia UPDATE (atómica ante peticiones concurrentes)
     * y bloquea la cuenta hasta {@code hasta} al alcanzar {@code maxIntentos}.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UsuarioLoginEntity u SET u.intentosFallidos = u.intentosFallidos + 1, " +
            "u.bloqueadoHasta = CASE WHEN u.intentosFallidos + 1 >= :maxIntentos THEN :hasta ELSE u.bloqueadoHasta END " +
            "WHERE u.id = :id")
    int registrarIntentoFallido(@Param("id") Long id, @Param("maxIntentos") short maxIntentos, @Param("hasta") LocalDateTime hasta);
}

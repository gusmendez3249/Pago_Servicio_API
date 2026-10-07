package com.proyecto.servicios.repositorys.onboarding;

import com.proyecto.servicios.entity.onboarding.UsuarioLoginEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}

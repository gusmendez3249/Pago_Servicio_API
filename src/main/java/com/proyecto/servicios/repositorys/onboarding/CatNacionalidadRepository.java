package com.proyecto.servicios.repositorys.onboarding;

import com.proyecto.servicios.entity.onboarding.CatNacionalidadEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatNacionalidadRepository extends JpaRepository<CatNacionalidadEntity, Long> {

    Optional<CatNacionalidadEntity> findByNombreIgnoreCaseAndActivoTrue(String nombre);

    Optional<CatNacionalidadEntity> findByClaveIgnoreCaseAndActivoTrue(String clave);

    boolean existsByNombreIgnoreCaseAndActivoTrue(String nombre);

    /** Coincidencia exacta (sensible a mayúsculas) contra el nombre de un registro activo. */
    boolean existsByNombreAndActivoTrue(String nombre);

    boolean existsByClaveIgnoreCaseAndActivoTrue(String clave);

    List<CatNacionalidadEntity> findByActivoTrue();
}

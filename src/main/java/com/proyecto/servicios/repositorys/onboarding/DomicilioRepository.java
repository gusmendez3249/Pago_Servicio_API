package com.proyecto.servicios.repositorys.onboarding;

import com.proyecto.servicios.entity.onboarding.DomicilioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DomicilioRepository extends JpaRepository<DomicilioEntity, Long> {
}

package com.proyecto.servicios.entity.onboarding;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cat_nacionalidad")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatNacionalidadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String clave;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}

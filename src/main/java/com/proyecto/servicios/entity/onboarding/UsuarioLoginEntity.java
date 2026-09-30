package com.proyecto.servicios.entity.onboarding;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_usuario_login", indexes = {
        @Index(name = "idx_usuario_username", columnList = "username"),
        @Index(name = "idx_usuario_face_id", columnList = "face_id_biometrico")
})
public class UsuarioLoginEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "face_id_biometrico")
    private Long faceIdBiometrico;

    @Column(name = "is_logged_in", nullable = false)
    @Builder.Default
    private Boolean isLoggedIn = false;

    @Column(name = "ultima_actividad")
    private LocalDateTime ultimaActividad;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", referencedColumnName = "id")
    private ClienteEntity cliente;
}

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
@Table(name = "tb_usuario_login")
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

    @Column(name = "intentos_fallidos", nullable = false)
    @Builder.Default
    private Short intentosFallidos = 0;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    /** CLIENTE: solo accede a su propio registro. EJECUTIVO: puede operar sobre cualquier cliente. */
    @Column(name = "rol", nullable = false, length = 10)
    @Builder.Default
    private String rol = "CLIENTE";

    /** SHA-256 del token de sesión vigente (el token en claro no se guarda). */
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "token_expira")
    private LocalDateTime tokenExpira;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", referencedColumnName = "id")
    private ClienteEntity cliente;
}

package com.proyecto.servicios.entity.onboarding;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

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

    /** Hash PBKDF2 del Face ID (dato biométrico): nunca se guarda en claro. */
    @Column(name = "face_id_hash", length = 255)
    @ToString.Exclude
    private String faceIdHash;

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

    /** Cierra la sesión: el token vigente deja de servir de inmediato. No persiste; quien llama guarda la entidad. */
    public void cerrarSesion() {
        this.isLoggedIn = false;
        this.tokenHash = null;
        this.tokenExpira = null;
    }
}

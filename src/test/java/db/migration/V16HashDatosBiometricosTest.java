package db.migration;

import com.proyecto.servicios.config.onboarding.PasswordEncoderUtil;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** La migración convierte el Face ID en claro a hash PBKDF2 y borra la columna en claro. */
class V16HashDatosBiometricosTest {

    private Connection conexion;
    private final Context context = mock(Context.class);
    private final PasswordEncoderUtil encoder = new PasswordEncoderUtil();

    @BeforeEach
    void setUp() throws Exception {
        conexion = DriverManager.getConnection("jdbc:h2:mem:v16;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        try (Statement st = conexion.createStatement()) {
            st.execute("CREATE TABLE tb_usuario_login (id BIGINT PRIMARY KEY, face_id_biometrico BIGINT)");
            st.execute("CREATE TABLE tb_cliente (id BIGINT PRIMARY KEY, datos_biometricos BIGINT)");
            st.execute("INSERT INTO tb_usuario_login VALUES (1, 123456), (2, NULL)");
            st.execute("INSERT INTO tb_cliente VALUES (7, 987654)");
        }
        when(context.getConnection()).thenReturn(conexion);
    }

    @AfterEach
    void cerrar() throws Exception {
        try (Statement st = conexion.createStatement()) {
            st.execute("DROP ALL OBJECTS");
        }
        conexion.close();
    }

    private String valor(String sql) throws Exception {
        try (Statement st = conexion.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private boolean existeColumna(String tabla, String columna) throws Exception {
        return valor("SELECT column_name FROM information_schema.columns WHERE table_name = '" + tabla
                + "' AND column_name = '" + columna + "'") != null;
    }

    @Test
    void hasheaYBorraLasColumnasEnClaro() throws Exception {
        new V16__hash_datos_biometricos().migrate(context);

        assertFalse(existeColumna("tb_usuario_login", "face_id_biometrico"));
        assertFalse(existeColumna("tb_cliente", "datos_biometricos"));
        String hash = valor("SELECT face_id_hash FROM tb_usuario_login WHERE id = 1");
        assertNotEquals("123456", hash);
        assertTrue(encoder.matchesBiometrico(123456L, hash));
        assertNull(valor("SELECT face_id_hash FROM tb_usuario_login WHERE id = 2"));
        assertTrue(encoder.matchesBiometrico(987654L, valor("SELECT datos_biometricos_hash FROM tb_cliente WHERE id = 7")));
    }

    @Test
    void esIdempotenteSiYaSeAplico() throws Exception {
        new V16__hash_datos_biometricos().migrate(context);
        String hash = valor("SELECT face_id_hash FROM tb_usuario_login WHERE id = 1");

        assertDoesNotThrow(() -> new V16__hash_datos_biometricos().migrate(context));
        assertEquals(hash, valor("SELECT face_id_hash FROM tb_usuario_login WHERE id = 1"));
    }
}

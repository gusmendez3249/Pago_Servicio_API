package db.migration;

import com.proyecto.servicios.config.onboarding.PasswordEncoderUtil;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V16: los datos biometricos dejan de guardarse en claro.
 *
 *   tb_usuario_login.face_id_biometrico (BIGINT en claro) -> face_id_hash (PBKDF2)
 *   tb_cliente.datos_biometricos        (BIGINT en claro) -> datos_biometricos_hash (PBKDF2)
 *
 * Es una migracion Java porque PBKDF2 no se puede calcular en SQL. Hashea cada valor existente con
 * {@link PasswordEncoderUtil} (el mismo esquema que valida el login) y despues ELIMINA las columnas en claro.
 * Todo corre en la transaccion de Flyway: si algo falla, la base queda como estaba.
 */
public class V16__hash_datos_biometricos extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws SQLException {
        Connection conexion = context.getConnection();
        PasswordEncoderUtil encoder = new PasswordEncoderUtil();
        try (Statement st = conexion.createStatement()) {
            st.execute("ALTER TABLE tb_usuario_login ADD COLUMN IF NOT EXISTS face_id_hash VARCHAR(255)");
            st.execute("ALTER TABLE tb_cliente ADD COLUMN IF NOT EXISTS datos_biometricos_hash VARCHAR(255)");
        }
        migrarColumna(conexion, encoder, "tb_usuario_login", "face_id_biometrico", "face_id_hash");
        migrarColumna(conexion, encoder, "tb_cliente", "datos_biometricos", "datos_biometricos_hash");
    }

    private static void migrarColumna(Connection conexion, PasswordEncoderUtil encoder, String tabla,
                                      String columnaClaro, String columnaHash) throws SQLException {
        if (!existeColumna(conexion, tabla, columnaClaro)) {
            return;
        }
        Map<Long, Long> valores = new LinkedHashMap<>();
        try (Statement st = conexion.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, " + columnaClaro + " FROM " + tabla
                     + " WHERE " + columnaClaro + " IS NOT NULL AND " + columnaHash + " IS NULL")) {
            while (rs.next()) {
                valores.put(rs.getLong(1), rs.getLong(2));
            }
        }
        try (PreparedStatement ps = conexion.prepareStatement(
                "UPDATE " + tabla + " SET " + columnaHash + " = ? WHERE id = ?")) {
            for (Map.Entry<Long, Long> fila : valores.entrySet()) {
                ps.setString(1, encoder.encodeBiometrico(fila.getValue()));
                ps.setLong(2, fila.getKey());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        try (Statement st = conexion.createStatement()) {
            st.execute("ALTER TABLE " + tabla + " DROP COLUMN " + columnaClaro);
        }
    }

    private static boolean existeColumna(Connection conexion, String tabla, String columna) throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement(
                "SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() "
                        + "AND table_name = ? AND column_name = ?")) {
            ps.setString(1, tabla);
            ps.setString(2, columna);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}

package db.migration;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Rebuilds SQLite currency constraints atomically without applying foreign-key delete cascades. */
public class V96__catalog_currency_codes extends BaseJavaMigration {
    private static final String SCRIPT = "db/scripts/sqlite-catalog-currency-codes.sql";

    @Override
    public Integer getChecksum() {
        try (InputStream stream = new ClassPathResource(SCRIPT).getInputStream()) {
            CRC32 checksum = new CRC32();
            checksum.update(stream.readAllBytes());
            return (int) checksum.getValue();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read currency migration", exception);
        }
    }

    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean autoCommit = connection.getAutoCommit();
        try (Statement statement = connection.createStatement()) {
            boolean foreignKeys;
            try (ResultSet result = statement.executeQuery("PRAGMA foreign_keys")) {
                result.next();
                foreignKeys = result.getInt(1) != 0;
            }
            statement.execute("PRAGMA foreign_keys=OFF");
            connection.setAutoCommit(false);
            try {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource(SCRIPT));
                try (ResultSet violations = statement.executeQuery("PRAGMA foreign_key_check")) {
                    if (violations.next()) {
                        throw new SQLException("Currency migration would leave invalid references");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
                statement.execute("PRAGMA foreign_keys=" + (foreignKeys ? "ON" : "OFF"));
            }
        }
    }
}

package org.openfinance.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class DatabaseConfigTest {

    @Test
    void shouldThrowWhenDatabaseUrlIsNull() {
        // Given / When / Then
        NullPointerException ex =
                assertThrows(
                        NullPointerException.class,
                        () -> new DatabaseConfig(null, "org.sqlite.JDBC"));
        assertThat(ex).hasMessageContaining("spring.datasource.url must be set");
    }

    @Test
    void shouldThrowWhenDriverClassNameIsNull() {
        // Given / When / Then
        NullPointerException ex =
                assertThrows(
                        NullPointerException.class,
                        () -> new DatabaseConfig("jdbc:sqlite::memory:", null));
        assertThat(ex).hasMessageContaining("spring.datasource.driver-class-name must be set");
    }

    @Test
    void shouldCreateHikariDataSourceWithExpectedConfiguration() {
        // Arrange
        String url = "jdbc:sqlite::memory:";
        String driver = "org.sqlite.JDBC";
        DatabaseConfig config = new DatabaseConfig(url, driver);

        // Act
        DataSource ds = config.dataSource();

        // Assert
        assertThat(ds).isInstanceOf(HikariDataSource.class);
        HikariDataSource hikari = (HikariDataSource) ds;

        assertThat(hikari.getJdbcUrl()).isEqualTo(url);
        assertThat(hikari.getDriverClassName()).isEqualTo(driver);
        assertThat(hikari.getMaximumPoolSize()).isEqualTo(5);
        assertThat(hikari.getMinimumIdle()).isEqualTo(1);
        assertThat(hikari.getConnectionTimeout()).isEqualTo(30000L);
        assertThat(hikari.getPoolName()).isEqualTo("OpenFinanceHikariPool");
        assertThat(hikari.getConnectionInitSql()).contains("PRAGMA journal_mode=WAL");

        // Clean up
        hikari.close();
    }
}

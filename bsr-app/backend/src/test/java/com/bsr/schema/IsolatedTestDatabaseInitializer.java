package com.bsr.schema;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class IsolatedTestDatabaseInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Object MIGRATION_MONITOR = new Object();

    private static final List<String> REQUIRED_VARIABLES = List.of(
            "TEST_DB_HOST",
            "TEST_DB_PORT",
            "TEST_DB_NAME",
            "TEST_DB_USERNAME",
            "TEST_DB_PASSWORD");

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        List<String> missing = REQUIRED_VARIABLES.stream()
                .filter(name -> {
                    String value = System.getenv(name);
                    return value == null || value.isBlank();
                })
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Isolated MySQL integration test variables are missing: " + String.join(", ", missing));
        }

        String databaseName = System.getenv("TEST_DB_NAME").toLowerCase(Locale.ROOT);
        if (!databaseName.matches("^bsr_(?:test|it)_[a-z0-9_]+$")) {
            throw new IllegalStateException(
                    "TEST_DB_NAME must start with bsr_test_ or bsr_it_ and contain only lowercase letters, "
                            + "digits, and underscores; refusing to migrate a non-test database.");
        }

        synchronized (MIGRATION_MONITOR) {
            migrateHistoricalFixture(databaseName);
        }
    }

    private static void migrateHistoricalFixture(String databaseName) {
        String jdbcUrl = "jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC"
                .formatted(
                        System.getenv("TEST_DB_HOST"),
                        System.getenv("TEST_DB_PORT"),
                        databaseName);
        DataSource dataSource = new DriverManagerDataSource(
                jdbcUrl,
                System.getenv("TEST_DB_USERNAME"),
                System.getenv("TEST_DB_PASSWORD"));
        Flyway latest = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        MigrationVersion current = latest.info().current() == null
                ? null
                : latest.info().current().getVersion();

        if (current == null) {
            assertDatabaseIsEmpty(dataSource);
            Flyway throughV002 = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("2"))
                    .load();
            MigrateResult initial = throughV002.migrate();
            if (initial.migrationsExecuted != 2) {
                throw new IllegalStateException(
                        "Expected an empty test database to execute V001-V002, but executed "
                                + initial.migrationsExecuted + ".");
            }
            loadHistoricalFixture(dataSource);
            MigrateResult upgrade = latest.migrate();
            if (upgrade.migrationsExecuted != 6) {
                throw new IllegalStateException(
                        "Expected the historical fixture upgrade to execute V003-V008, but executed "
                                + upgrade.migrationsExecuted + ".");
            }
            return;
        }

        if (!MigrationVersion.fromVersion("3").equals(current)
                && !MigrationVersion.fromVersion("4").equals(current)
                && !MigrationVersion.fromVersion("5").equals(current)
                && !MigrationVersion.fromVersion("6").equals(current)
                && !MigrationVersion.fromVersion("7").equals(current)
                && !MigrationVersion.fromVersion("8").equals(current)) {
            throw new IllegalStateException(
                    "Isolated test database must be empty or already at V003/V004/V005/V006/V007/V008; found " + current + ".");
        }
        MigrateResult rerun = latest.migrate();
        int expected = 8 - Integer.parseInt(current.toString());
        if (rerun.migrationsExecuted != expected) {
            throw new IllegalStateException("Expected " + expected + " upgrade migration(s) through V008, but executed "
                    + rerun.migrationsExecuted + ".");
        }
    }

    private static void assertDatabaseIsEmpty(DataSource dataSource) {
        Long tableCount = new JdbcTemplate(dataSource).queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                """,
                Long.class);
        if (tableCount == null || tableCount != 0L) {
            throw new IllegalStateException(
                    "Refusing to migrate a non-empty database without Flyway V003 history.");
        }
    }

    private static void loadHistoricalFixture(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("fixtures/core/v003-foundation-smoke.sql"));
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load the pre-V003 historical fixture.", exception);
        }
    }
}

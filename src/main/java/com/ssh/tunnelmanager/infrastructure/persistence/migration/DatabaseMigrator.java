package com.ssh.tunnelmanager.infrastructure.persistence.migration;

import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseHealthChecker;
import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceException;
import com.ssh.tunnelmanager.infrastructure.persistence.backup.BackupService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class DatabaseMigrator {
  private static final String INITIAL_SCHEMA = "/db/migrations/V001__initial_schema.sql";

  private final DatabaseFactory database;
  private final List<MigrationScript> migrations;
  private final Runnable beforeMigration;
  private final Clock clock;

  public DatabaseMigrator(DatabaseFactory database) {
    this(database, List.of(loadInitialSchema()), () -> {}, Clock.systemUTC());
  }

  public DatabaseMigrator(DatabaseFactory database, Runnable beforeMigration) {
    this(database, List.of(loadInitialSchema()), beforeMigration, Clock.systemUTC());
  }

  public DatabaseMigrator(DatabaseFactory database, BackupService backups) {
    this(database, () -> backups.beforeRiskyOperation("migration"));
  }

  public DatabaseMigrator(
      DatabaseFactory database,
      List<MigrationScript> migrations,
      BackupService backups,
      Clock clock) {
    this(database, migrations, () -> backups.beforeRiskyOperation("migration"), clock);
  }

  public DatabaseMigrator(
      DatabaseFactory database,
      List<MigrationScript> migrations,
      Runnable beforeMigration,
      Clock clock) {
    this.database = database;
    this.migrations =
        migrations.stream().sorted(Comparator.comparingInt(MigrationScript::version)).toList();
    this.beforeMigration = beforeMigration;
    this.clock = clock;
  }

  public void migrate() {
    boolean existingDatabase =
        Files.exists(database.databasePath()) && hasContent(database.databasePath());
    if (existingDatabase) {
      new DatabaseHealthChecker(database).assertHealthy();
    }

    database.inTransaction(
        connection -> {
          try (var statement = connection.createStatement()) {
            statement.execute(
                "CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, name TEXT NOT NULL, applied_at TEXT NOT NULL)");
          }
          return null;
        });

    int currentVersion =
        database.withConnection(
            connection -> {
              try (var statement = connection.createStatement();
                  var result =
                      statement.executeQuery(
                          "SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
                result.next();
                return result.getInt(1);
              }
            });

    for (MigrationScript migration : migrations) {
      if (migration.version() <= currentVersion) {
        continue;
      }
      beforeMigration.run();
      database.inTransaction(
          connection -> {
            executeStatements(connection, migration.sql());
            try (var statement =
                connection.prepareStatement(
                    "INSERT INTO schema_version(version, name, applied_at) VALUES (?, ?, ?)")) {
              statement.setInt(1, migration.version());
              statement.setString(2, migration.name());
              statement.setString(3, Instant.now(clock).toString());
              statement.executeUpdate();
            }
            return null;
          });
      currentVersion = migration.version();
    }
  }

  private static void executeStatements(java.sql.Connection connection, String sql)
      throws java.sql.SQLException {
    for (String statementSql : sql.split(";")) {
      String statement = statementSql.trim();
      if (!statement.isEmpty()) {
        try (var sqlStatement = connection.createStatement()) {
          sqlStatement.execute(statementSql);
        }
      }
    }
  }

  private static MigrationScript loadInitialSchema() {
    try (InputStream input = DatabaseMigrator.class.getResourceAsStream(INITIAL_SCHEMA)) {
      if (input == null) {
        throw new PersistenceException("No se encontró la migración inicial SQLite");
      }
      return new MigrationScript(
          1, "V001__initial_schema", new String(input.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException exception) {
      throw new PersistenceException("No se pudo leer la migración inicial SQLite", exception);
    }
  }

  private static boolean hasContent(java.nio.file.Path path) {
    try {
      return Files.size(path) > 0;
    } catch (IOException exception) {
      throw new PersistenceException("No se pudo inspeccionar la base SQLite", exception);
    }
  }
}

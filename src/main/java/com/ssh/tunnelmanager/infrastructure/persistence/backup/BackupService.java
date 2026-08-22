package com.ssh.tunnelmanager.infrastructure.persistence.backup;

import com.ssh.tunnelmanager.application.portability.BackupPort;
import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

public final class BackupService implements BackupPort {
  private static final String DIRTY_KEY = "backup.dirty";
  private static final String LAST_BACKUP_KEY = "backup.last-created-at";
  private static final Duration PERIOD = Duration.ofMinutes(30);
  private static final int MAX_BACKUPS = 10;

  private final DatabaseFactory database;
  private final Path backupsDirectory;
  private final Clock clock;
  private final SQLiteSnapshotService snapshots;

  public BackupService(DatabaseFactory database, Path backupsDirectory) {
    this(database, backupsDirectory, Clock.systemUTC());
  }

  public BackupService(DatabaseFactory database, Path backupsDirectory, Clock clock) {
    this.database = database;
    this.backupsDirectory = backupsDirectory.toAbsolutePath().normalize();
    this.clock = clock;
    this.snapshots = new SQLiteSnapshotService(database);
  }

  public void markDirty() {
    putMetadata(DIRTY_KEY, "true");
  }

  public boolean isDirty() {
    return "true".equalsIgnoreCase(readMetadata(DIRTY_KEY));
  }

  public Path createBackup(String reason) {
    requireDatabase();
    try {
      Files.createDirectories(backupsDirectory);
      String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now(clock)).replace(":", "-");
      Path destination = backupsDirectory.resolve("backup-" + timestamp + ".db");
      int suffix = 1;
      while (Files.exists(destination)) {
        destination = backupsDirectory.resolve("backup-" + timestamp + "-" + suffix++ + ".db");
      }
      snapshots.createSnapshot(destination);
      SQLiteSnapshotService.validate(destination);
      putMetadata(DIRTY_KEY, "false");
      putMetadata(LAST_BACKUP_KEY, Instant.now(clock).toString());
      retainLatestTen();
      return destination;
    } catch (IOException exception) {
      throw new PersistenceException("No se pudo crear el backup SQLite: " + reason, exception);
    }
  }

  @Override
  public Path backupIfDue() {
    if (!isDirty()) {
      return null;
    }
    String last = readMetadata(LAST_BACKUP_KEY);
    if (last != null && !last.isBlank()) {
      Instant lastCreated = Instant.parse(last);
      if (Instant.now(clock).isBefore(lastCreated.plus(PERIOD))) {
        return null;
      }
    }
    return createBackup("periodic");
  }

  public Path beforeRiskyOperation(String operation) {
    return createBackup("before-" + operation);
  }

  @Override
  public Path beforeImport() {
    return beforeRiskyOperation("import");
  }

  @Override
  public Path beforeRestore() {
    return beforeRiskyOperation("restore");
  }

  public Path beforeBulkDeletion() {
    return beforeRiskyOperation("bulk-deletion");
  }

  public List<Path> listBackups() {
    if (!Files.isDirectory(backupsDirectory)) {
      return List.of();
    }
    try (var paths = Files.list(backupsDirectory)) {
      return paths
          .filter(
              path ->
                  path.getFileName().toString().startsWith("backup-")
                      && path.toString().endsWith(".db"))
          .filter(BackupService::isValidBackup)
          .sorted(Comparator.comparing(Path::toString).reversed())
          .toList();
    } catch (IOException exception) {
      throw new PersistenceException("No se pudieron listar los backups SQLite", exception);
    }
  }

  public void retainLatestTen() {
    List<Path> backups = listBackups();
    for (Path backup : backups.subList(Math.min(MAX_BACKUPS, backups.size()), backups.size())) {
      try {
        Files.deleteIfExists(backup);
      } catch (IOException exception) {
        throw new PersistenceException("No se pudo aplicar la retención de backups", exception);
      }
    }
  }

  public void restore(Path backup) {
    SQLiteSnapshotService.validate(backup);
    restoreValidated(backup, false);
    putMetadata(DIRTY_KEY, "false");
  }

  /** Restores without attempting to snapshot an unreadable current database. */
  public void restoreAfterCorruption(Path backup) {
    SQLiteSnapshotService.validate(backup);
    restoreValidated(backup, true);
  }

  private void restoreValidated(Path backup, boolean preservePrevious) {
    Path temporary = null;
    Path previous = null;
    Path databasePath = database.databasePath();
    try {
      Files.createDirectories(databasePath.toAbsolutePath().normalize().getParent());
      temporary = Files.createTempFile(databasePath.getParent(), "restore-", ".db");
      Files.copy(backup, temporary, StandardCopyOption.REPLACE_EXISTING);
      SQLiteSnapshotService.validate(temporary);
      previous = Files.createTempFile(databasePath.getParent(), "restore-previous-", ".db");
      Files.deleteIfExists(previous);
      if (Files.exists(databasePath)) {
        moveReplacing(databasePath, previous);
      }
      deleteSidecars(databasePath);
      try {
        moveReplacing(temporary, databasePath);
        temporary = null;
        if (preservePrevious && previous != null) {
          Path preserved =
              databasePath.resolveSibling(
                  databasePath.getFileName() + ".corrupt-" + System.currentTimeMillis() + ".db");
          moveReplacing(previous, preserved);
        } else {
          Files.deleteIfExists(previous);
        }
        previous = null;
      } catch (IOException exception) {
        if (previous != null && Files.exists(previous)) {
          moveReplacing(previous, databasePath);
          previous = null;
        }
        throw exception;
      }
    } catch (IOException exception) {
      throw new PersistenceException("No se pudo restaurar el backup SQLite", exception);
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
          // La restauración ya ha fallado; conservar el error original es más útil.
        }
      }
      if (previous != null) {
        try {
          Files.deleteIfExists(previous);
        } catch (IOException ignored) {
          // No se puede deshacer el restore después de conservar el estado anterior.
        }
      }
    }
  }

  private static boolean isValidBackup(Path path) {
    try {
      SQLiteSnapshotService.validate(path);
      return true;
    } catch (PersistenceException exception) {
      return false;
    }
  }

  private static void moveReplacing(Path source, Path destination) throws IOException {
    try {
      Files.move(
          source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
      Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void requireDatabase() {
    if (!Files.isRegularFile(database.databasePath())) {
      throw new PersistenceException("No existe la base SQLite para crear un backup");
    }
  }

  private String readMetadata(String key) {
    return database.withConnection(
        connection -> {
          if (!metadataTableExists(connection)) {
            return null;
          }
          try (var statement =
              connection.prepareStatement("SELECT value FROM application_setting WHERE key = ?")) {
            statement.setString(1, key);
            try (var result = statement.executeQuery()) {
              return result.next() ? result.getString(1) : null;
            }
          }
        });
  }

  private void putMetadata(String key, String value) {
    database.inTransaction(
        connection -> {
          if (!metadataTableExists(connection)) {
            return null;
          }
          try (var statement =
              connection.prepareStatement(
                  "INSERT INTO application_setting(key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
          }
          return null;
        });
  }

  private static boolean metadataTableExists(java.sql.Connection connection)
      throws java.sql.SQLException {
    try (var statement =
        connection.prepareStatement(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'application_setting'")) {
      try (var result = statement.executeQuery()) {
        return result.next();
      }
    }
  }

  private static void deleteSidecars(Path databasePath) throws IOException {
    Files.deleteIfExists(databasePath.resolveSibling(databasePath.getFileName() + "-wal"));
    Files.deleteIfExists(databasePath.resolveSibling(databasePath.getFileName() + "-shm"));
  }
}

package com.ssh.tunnelmanager.infrastructure.persistence.backup;

import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SQLiteSnapshotService {
  private final DatabaseFactory database;

  public SQLiteSnapshotService(DatabaseFactory database) {
    this.database = database;
  }

  public void createSnapshot(Path destination) {
    try {
      Files.createDirectories(destination.toAbsolutePath().normalize().getParent());
      if (Files.exists(destination)) {
        throw new PersistenceException("El destino del snapshot ya existe");
      }
    } catch (java.io.IOException exception) {
      throw new PersistenceException("No se pudo preparar el snapshot SQLite", exception);
    }
    database.withConnection(
        connection -> {
          try (var statement = connection.prepareStatement("VACUUM INTO ?")) {
            statement.setString(1, destination.toAbsolutePath().normalize().toString());
            statement.execute();
          }
          return null;
        });
  }

  public static void validate(Path snapshot) {
    if (!Files.isRegularFile(snapshot)) {
      throw new PersistenceException("El snapshot SQLite no existe");
    }
    DatabaseFactory snapshotDatabase = new DatabaseFactory(snapshot);
    snapshotDatabase.withConnection(
        connection -> {
          try (var statement = connection.createStatement();
              var result = statement.executeQuery("PRAGMA integrity_check")) {
            if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) {
              throw new PersistenceException("El snapshot SQLite no supera integrity_check");
            }
            return null;
          }
        });
  }
}

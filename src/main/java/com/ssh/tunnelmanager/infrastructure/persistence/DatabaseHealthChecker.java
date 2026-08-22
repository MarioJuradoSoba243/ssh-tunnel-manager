package com.ssh.tunnelmanager.infrastructure.persistence;

import java.sql.DriverManager;

public final class DatabaseHealthChecker {
  private final DatabaseFactory database;

  public DatabaseHealthChecker(DatabaseFactory database) {
    this.database = database;
  }

  public void assertHealthy() {
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.databasePath())) {
      try (var statement = connection.createStatement();
          var result = statement.executeQuery("PRAGMA integrity_check")) {
        if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) {
          throw new DatabaseCorruptionException(
              "La base SQLite está corrupta y no se sobrescribirá");
        }
      } catch (java.sql.SQLException exception) {
        throw new DatabaseCorruptionException("La base SQLite está corrupta y no se sobrescribirá");
      }
    } catch (java.sql.SQLException exception) {
      throw new PersistenceException("No se pudo comprobar la salud de la base SQLite", exception);
    }
  }
}

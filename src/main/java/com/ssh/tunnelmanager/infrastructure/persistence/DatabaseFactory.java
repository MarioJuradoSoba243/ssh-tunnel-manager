package com.ssh.tunnelmanager.infrastructure.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseFactory {
  private final Path databasePath;
  private final String jdbcUrl;

  public DatabaseFactory(Path databasePath) {
    this.databasePath = databasePath.toAbsolutePath().normalize();
    this.jdbcUrl = "jdbc:sqlite:" + this.databasePath;
  }

  public Path databasePath() {
    return databasePath;
  }

  public Connection openConnection() {
    Connection connection = null;
    try {
      connection = DriverManager.getConnection(jdbcUrl);
      configure(connection);
      return connection;
    } catch (SQLException exception) {
      if (connection != null) {
        try {
          connection.close();
        } catch (SQLException closeException) {
          exception.addSuppressed(closeException);
        }
      }
      throw new PersistenceException("No se pudo abrir la base SQLite", exception);
    }
  }

  public <T> T withConnection(SqlWork<T> work) {
    try (Connection connection = openConnection()) {
      return work.execute(connection);
    } catch (SQLException exception) {
      throw new PersistenceException("Falló una operación SQLite", exception);
    }
  }

  public <T> T inTransaction(SqlWork<T> work) {
    try (Connection connection = openConnection()) {
      connection.setAutoCommit(false);
      try {
        T result = work.execute(connection);
        connection.commit();
        return result;
      } catch (SQLException | RuntimeException exception) {
        try {
          connection.rollback();
        } catch (SQLException rollbackException) {
          exception.addSuppressed(rollbackException);
        }
        if (exception instanceof SQLException sqlException) {
          throw new PersistenceException("Falló una transacción SQLite", sqlException);
        }
        throw exception;
      }
    } catch (SQLException exception) {
      throw new PersistenceException("Falló una transacción SQLite", exception);
    }
  }

  private static void configure(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("PRAGMA foreign_keys = ON");
      statement.execute("PRAGMA journal_mode = WAL");
      statement.execute("PRAGMA busy_timeout = 5000");
    }
  }
}

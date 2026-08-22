package com.ssh.tunnelmanager.infrastructure.persistence.repository;

import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceConstraintException;
import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

abstract class JdbcRepositorySupport {
  protected final DatabaseFactory database;
  private final Runnable dirtyMarker;

  protected JdbcRepositorySupport(DatabaseFactory database) {
    this(database, () -> {});
  }

  protected JdbcRepositorySupport(DatabaseFactory database, Runnable dirtyMarker) {
    this.database = database;
    this.dirtyMarker = dirtyMarker;
  }

  protected void markDirty() {
    dirtyMarker.run();
  }

  protected static UUID uuid(ResultSet result, String column) throws SQLException {
    String value = result.getString(column);
    return value == null ? null : UUID.fromString(value);
  }

  protected static Instant instant(ResultSet result, String column) throws SQLException {
    String value = result.getString(column);
    return value == null ? null : Instant.parse(value);
  }

  protected static void setUuid(PreparedStatement statement, int index, UUID value)
      throws SQLException {
    if (value == null) {
      statement.setNull(index, java.sql.Types.VARCHAR);
    } else {
      statement.setString(index, value.toString());
    }
  }

  protected static PersistenceException translate(String operation, SQLException exception) {
    String message =
        exception.getMessage() == null
            ? ""
            : exception.getMessage().toLowerCase(java.util.Locale.ROOT);
    if ("23000".equals(exception.getSQLState())
        || message.contains("constraint")
        || message.contains("unique")
        || message.contains("foreign key")) {
      return new PersistenceConstraintException(
          "No se pudo completar " + operation + " por una restricción", exception);
    }
    return new PersistenceException("No se pudo completar " + operation, exception);
  }

  protected static PersistenceException translate(
      String operation, PersistenceException exception) {
    if (exception.getCause() instanceof SQLException sqlException) {
      return translate(operation, sqlException);
    }
    return new PersistenceException("No se pudo completar " + operation, exception);
  }
}

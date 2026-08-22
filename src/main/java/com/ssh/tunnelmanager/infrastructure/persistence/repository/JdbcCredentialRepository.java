package com.ssh.tunnelmanager.infrastructure.persistence.repository;

import com.ssh.tunnelmanager.application.persistence.CredentialRepository;
import com.ssh.tunnelmanager.application.persistence.model.CredentialRecord;
import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcCredentialRepository extends JdbcRepositorySupport
    implements CredentialRepository {
  public JdbcCredentialRepository(DatabaseFactory database) {
    super(database);
  }

  public JdbcCredentialRepository(DatabaseFactory database, Runnable dirtyMarker) {
    super(database, dirtyMarker);
  }

  @Override
  public Optional<CredentialRecord> findById(UUID id) {
    return database.withConnection(
        connection -> {
          try (var statement =
              connection.prepareStatement(
                  "SELECT id, username, password FROM credential WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (var result = statement.executeQuery()) {
              return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
          }
        });
  }

  @Override
  public List<CredentialRecord> findAll() {
    return database.withConnection(
        connection -> {
          try (var statement = connection.createStatement();
              var result =
                  statement.executeQuery(
                      "SELECT id, username, password FROM credential ORDER BY username, id")) {
            var credentials = new java.util.ArrayList<CredentialRecord>();
            while (result.next()) {
              credentials.add(read(result));
            }
            return List.copyOf(credentials);
          }
        });
  }

  @Override
  public void save(CredentialRecord credential) {
    try {
      database.withConnection(
          connection -> {
            try (var statement =
                connection.prepareStatement(
                    "INSERT INTO credential(id, username, password) VALUES (?, ?, ?) "
                        + "ON CONFLICT(id) DO UPDATE SET username = excluded.username, password = excluded.password")) {
              statement.setString(1, credential.id().toString());
              statement.setString(2, credential.username());
              statement.setString(3, credential.password());
              statement.executeUpdate();
              return null;
            }
          });
      markDirty();
    } catch (PersistenceException exception) {
      throw translate("guardar la credencial", exception);
    }
  }

  @Override
  public void delete(UUID id) {
    try {
      database.withConnection(
          connection -> {
            try (var statement =
                connection.prepareStatement("DELETE FROM credential WHERE id = ?")) {
              statement.setString(1, id.toString());
              statement.executeUpdate();
              return null;
            }
          });
      markDirty();
    } catch (PersistenceException exception) {
      throw translate("borrar la credencial", exception);
    }
  }

  private static CredentialRecord read(java.sql.ResultSet result) throws SQLException {
    return new CredentialRecord(
        UUID.fromString(result.getString("id")),
        result.getString("username"),
        result.getString("password"));
  }
}

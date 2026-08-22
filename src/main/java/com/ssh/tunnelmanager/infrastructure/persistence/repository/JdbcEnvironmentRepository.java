package com.ssh.tunnelmanager.infrastructure.persistence.repository;

import com.ssh.tunnelmanager.application.persistence.EnvironmentRepository;
import com.ssh.tunnelmanager.application.persistence.model.EnvironmentRecord;
import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcEnvironmentRepository extends JdbcRepositorySupport
    implements EnvironmentRepository {
  public JdbcEnvironmentRepository(DatabaseFactory database) {
    super(database);
  }

  public JdbcEnvironmentRepository(DatabaseFactory database, Runnable dirtyMarker) {
    super(database, dirtyMarker);
  }

  @Override
  public Optional<EnvironmentRecord> findById(UUID id) {
    return database.withConnection(
        connection -> {
          try (var statement =
              connection.prepareStatement("SELECT id, name FROM environment WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (var result = statement.executeQuery()) {
              return result.next()
                  ? Optional.of(
                      new EnvironmentRecord(
                          UUID.fromString(result.getString(1)), result.getString(2)))
                  : Optional.empty();
            }
          }
        });
  }

  @Override
  public List<EnvironmentRecord> findAll() {
    return database.withConnection(
        connection -> {
          try (var statement = connection.createStatement();
              var result =
                  statement.executeQuery("SELECT id, name FROM environment ORDER BY name, id")) {
            var environments = new java.util.ArrayList<EnvironmentRecord>();
            while (result.next()) {
              environments.add(
                  new EnvironmentRecord(UUID.fromString(result.getString(1)), result.getString(2)));
            }
            return List.copyOf(environments);
          }
        });
  }

  @Override
  public void save(EnvironmentRecord environment) {
    mutate(
        "guardar el entorno",
        "INSERT INTO environment(id, name) VALUES (?, ?) ON CONFLICT(id) DO UPDATE SET name = excluded.name",
        environment.id(),
        environment.name());
  }

  @Override
  public void delete(UUID id) {
    mutate("borrar el entorno", "DELETE FROM environment WHERE id = ?", id, null);
  }

  private void mutate(String operation, String sql, UUID id, String name) {
    try {
      database.withConnection(
          connection -> {
            try (var statement = connection.prepareStatement(sql)) {
              statement.setString(1, id.toString());
              if (name != null) {
                statement.setString(2, name);
              }
              statement.executeUpdate();
              return null;
            }
          });
      markDirty();
    } catch (PersistenceException exception) {
      throw translate(operation, exception);
    }
  }
}

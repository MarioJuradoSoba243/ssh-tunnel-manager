package com.ssh.tunnelmanager.infrastructure.persistence.repository;

import com.ssh.tunnelmanager.application.persistence.ProjectRepository;
import com.ssh.tunnelmanager.application.persistence.model.ProjectRecord;
import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcProjectRepository extends JdbcRepositorySupport
    implements ProjectRepository {
  public JdbcProjectRepository(DatabaseFactory database) {
    super(database);
  }

  public JdbcProjectRepository(DatabaseFactory database, Runnable dirtyMarker) {
    super(database, dirtyMarker);
  }

  @Override
  public Optional<ProjectRecord> findById(UUID id) {
    return database.withConnection(
        connection -> {
          try (var statement =
              connection.prepareStatement("SELECT id, name FROM project WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (var result = statement.executeQuery()) {
              return result.next()
                  ? Optional.of(
                      new ProjectRecord(UUID.fromString(result.getString(1)), result.getString(2)))
                  : Optional.empty();
            }
          }
        });
  }

  @Override
  public List<ProjectRecord> findAll() {
    return database.withConnection(
        connection -> {
          try (var statement = connection.createStatement();
              var result =
                  statement.executeQuery("SELECT id, name FROM project ORDER BY name, id")) {
            var projects = new java.util.ArrayList<ProjectRecord>();
            while (result.next()) {
              projects.add(
                  new ProjectRecord(UUID.fromString(result.getString(1)), result.getString(2)));
            }
            return List.copyOf(projects);
          }
        });
  }

  @Override
  public void save(ProjectRecord project) {
    mutate(
        "guardar el proyecto",
        "INSERT INTO project(id, name) VALUES (?, ?) ON CONFLICT(id) DO UPDATE SET name = excluded.name",
        project.id(),
        project.name());
  }

  @Override
  public void delete(UUID id) {
    mutate("borrar el proyecto", "DELETE FROM project WHERE id = ?", id, null);
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

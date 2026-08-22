package com.ssh.tunnelmanager.infrastructure.persistence.repository;

import com.ssh.tunnelmanager.application.persistence.TagRepository;
import com.ssh.tunnelmanager.application.persistence.model.TagRecord;
import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcTagRepository extends JdbcRepositorySupport implements TagRepository {
  public JdbcTagRepository(DatabaseFactory database) {
    super(database);
  }

  public JdbcTagRepository(DatabaseFactory database, Runnable dirtyMarker) {
    super(database, dirtyMarker);
  }

  @Override
  public Optional<TagRecord> findById(UUID id) {
    return database.withConnection(
        connection -> {
          try (var statement =
              connection.prepareStatement("SELECT id, name FROM tag WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (var result = statement.executeQuery()) {
              return result.next()
                  ? Optional.of(
                      new TagRecord(UUID.fromString(result.getString(1)), result.getString(2)))
                  : Optional.empty();
            }
          }
        });
  }

  @Override
  public List<TagRecord> findAll() {
    return database.withConnection(
        connection -> {
          try (var statement = connection.createStatement();
              var result = statement.executeQuery("SELECT id, name FROM tag ORDER BY name, id")) {
            var tags = new java.util.ArrayList<TagRecord>();
            while (result.next()) {
              tags.add(new TagRecord(UUID.fromString(result.getString(1)), result.getString(2)));
            }
            return List.copyOf(tags);
          }
        });
  }

  @Override
  public void save(TagRecord tag) {
    mutate(
        "guardar la etiqueta",
        "INSERT INTO tag(id, name) VALUES (?, ?) ON CONFLICT(id) DO UPDATE SET name = excluded.name",
        tag.id(),
        tag.name());
  }

  @Override
  public void delete(UUID id) {
    mutate("borrar la etiqueta", "DELETE FROM tag WHERE id = ?", id, null);
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

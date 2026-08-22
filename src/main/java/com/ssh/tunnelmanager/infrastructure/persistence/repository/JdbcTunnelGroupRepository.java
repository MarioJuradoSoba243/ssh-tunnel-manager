package com.ssh.tunnelmanager.infrastructure.persistence.repository;

import com.ssh.tunnelmanager.application.persistence.TunnelGroupRepository;
import com.ssh.tunnelmanager.application.persistence.model.TunnelGroupRecord;
import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class JdbcTunnelGroupRepository extends JdbcRepositorySupport
    implements TunnelGroupRepository {
  public JdbcTunnelGroupRepository(DatabaseFactory database) {
    super(database);
  }

  public JdbcTunnelGroupRepository(DatabaseFactory database, Runnable dirtyMarker) {
    super(database, dirtyMarker);
  }

  @Override
  public Optional<TunnelGroupRecord> findById(UUID id) {
    return database.withConnection(
        connection -> {
          try (var statement = connection.prepareStatement(baseSelect() + " WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (var result = statement.executeQuery()) {
              return result.next() ? Optional.of(read(connection, result)) : Optional.empty();
            }
          }
        });
  }

  @Override
  public List<TunnelGroupRecord> findAll() {
    return database.withConnection(
        connection -> {
          try (var statement = connection.createStatement();
              var result = statement.executeQuery(baseSelect() + " ORDER BY name, id")) {
            var groups = new ArrayList<TunnelGroupRecord>();
            while (result.next()) groups.add(read(connection, result));
            return List.copyOf(groups);
          }
        });
  }

  @Override
  public void save(TunnelGroupRecord group) {
    try {
      database.inTransaction(
          connection -> {
            try (var statement =
                connection.prepareStatement(
                    "INSERT INTO tunnel_group(id, name, project_id, environment_id, is_favorite) VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT(id) DO UPDATE SET name = excluded.name, project_id = excluded.project_id, environment_id = excluded.environment_id, is_favorite = excluded.is_favorite")) {
              statement.setString(1, group.id().toString());
              statement.setString(2, group.name());
              setUuid(statement, 3, group.projectId());
              setUuid(statement, 4, group.environmentId());
              statement.setInt(5, group.favorite() ? 1 : 0);
              statement.executeUpdate();
            }
            replaceMembers(connection, group);
            replaceTags(connection, group);
            return null;
          });
      markDirty();
    } catch (PersistenceException exception) {
      throw translate("guardar el grupo", exception);
    }
  }

  @Override
  public void delete(UUID id) {
    try {
      database.withConnection(
          connection -> {
            try (var statement =
                connection.prepareStatement("DELETE FROM tunnel_group WHERE id = ?")) {
              statement.setString(1, id.toString());
              statement.executeUpdate();
              return null;
            }
          });
      markDirty();
    } catch (PersistenceException exception) {
      throw translate("borrar el grupo", exception);
    }
  }

  private static String baseSelect() {
    return "SELECT id, name, project_id, environment_id, is_favorite FROM tunnel_group";
  }

  private static TunnelGroupRecord read(Connection connection, java.sql.ResultSet result)
      throws SQLException {
    UUID id = UUID.fromString(result.getString("id"));
    return new TunnelGroupRecord(
        id,
        result.getString("name"),
        nullableUuid(result.getString("project_id")),
        nullableUuid(result.getString("environment_id")),
        result.getInt("is_favorite") == 1,
        loadIds(connection, "SELECT tunnel_id FROM tunnel_group_member WHERE group_id = ?", id),
        loadIds(connection, "SELECT tag_id FROM group_tag WHERE group_id = ?", id));
  }

  private static Set<UUID> loadIds(Connection connection, String sql, UUID groupId)
      throws SQLException {
    try (var statement = connection.prepareStatement(sql)) {
      statement.setString(1, groupId.toString());
      try (var result = statement.executeQuery()) {
        Set<UUID> ids = new HashSet<>();
        while (result.next()) ids.add(UUID.fromString(result.getString(1)));
        return Set.copyOf(ids);
      }
    }
  }

  private static void replaceMembers(Connection connection, TunnelGroupRecord group)
      throws SQLException {
    replaceRelation(
        connection,
        "DELETE FROM tunnel_group_member WHERE group_id = ?",
        "INSERT INTO tunnel_group_member(group_id, tunnel_id) VALUES (?, ?)",
        group.id(),
        group.tunnelIds());
  }

  private static void replaceTags(Connection connection, TunnelGroupRecord group)
      throws SQLException {
    replaceRelation(
        connection,
        "DELETE FROM group_tag WHERE group_id = ?",
        "INSERT INTO group_tag(group_id, tag_id) VALUES (?, ?)",
        group.id(),
        group.tagIds());
  }

  private static void replaceRelation(
      Connection connection, String deleteSql, String insertSql, UUID groupId, Set<UUID> ids)
      throws SQLException {
    try (var delete = connection.prepareStatement(deleteSql)) {
      delete.setString(1, groupId.toString());
      delete.executeUpdate();
    }
    try (var insert = connection.prepareStatement(insertSql)) {
      for (UUID id : ids) {
        insert.setString(1, groupId.toString());
        insert.setString(2, id.toString());
        insert.addBatch();
      }
      insert.executeBatch();
    }
  }

  private static UUID nullableUuid(String value) {
    return value == null ? null : UUID.fromString(value);
  }
}

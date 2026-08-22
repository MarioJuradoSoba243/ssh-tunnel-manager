package com.ssh.tunnelmanager.infrastructure.persistence.repository;

import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class JdbcTunnelRepository extends JdbcRepositorySupport implements TunnelRepository {
  public JdbcTunnelRepository(DatabaseFactory database) {
    super(database);
  }

  public JdbcTunnelRepository(DatabaseFactory database, Runnable dirtyMarker) {
    super(database, dirtyMarker);
  }

  @Override
  public Optional<TunnelRecord> findById(UUID id) {
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
  public List<TunnelRecord> findAll() {
    return database.withConnection(
        connection -> {
          try (var statement = connection.createStatement();
              var result = statement.executeQuery(baseSelect() + " ORDER BY name, id")) {
            var tunnels = new ArrayList<TunnelRecord>();
            while (result.next()) {
              tunnels.add(read(connection, result));
            }
            return List.copyOf(tunnels);
          }
        });
  }

  @Override
  public void save(TunnelRecord tunnel) {
    try {
      database.inTransaction(
          connection -> {
            try (var statement =
                connection.prepareStatement(
                    "INSERT INTO tunnel(id, name, bind_address, local_port, destination_host, destination_port, final_host_id, jump1_host_id, jump2_host_id, keep_alive_override_seconds, connection_timeout_override_seconds, retry_count_override, project_id, environment_id, is_favorite, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(id) DO UPDATE SET name = excluded.name, bind_address = excluded.bind_address, local_port = excluded.local_port, destination_host = excluded.destination_host, destination_port = excluded.destination_port, final_host_id = excluded.final_host_id, jump1_host_id = excluded.jump1_host_id, jump2_host_id = excluded.jump2_host_id, keep_alive_override_seconds = excluded.keep_alive_override_seconds, connection_timeout_override_seconds = excluded.connection_timeout_override_seconds, retry_count_override = excluded.retry_count_override, project_id = excluded.project_id, environment_id = excluded.environment_id, is_favorite = excluded.is_favorite, updated_at = excluded.updated_at")) {
              int index = 1;
              statement.setString(index++, tunnel.id().toString());
              statement.setString(index++, tunnel.name());
              statement.setString(index++, tunnel.bindAddress());
              statement.setInt(index++, tunnel.localPort());
              statement.setString(index++, tunnel.destinationHost());
              statement.setInt(index++, tunnel.destinationPort());
              setUuid(statement, index++, tunnel.finalHostId());
              setUuid(statement, index++, tunnel.jump1HostId());
              setUuid(statement, index++, tunnel.jump2HostId());
              setLong(statement, index++, tunnel.keepAliveOverrideSeconds());
              setLong(statement, index++, tunnel.connectionTimeoutOverrideSeconds());
              setInteger(statement, index++, tunnel.retryCountOverride());
              setUuid(statement, index++, tunnel.projectId());
              setUuid(statement, index++, tunnel.environmentId());
              statement.setInt(index++, tunnel.favorite() ? 1 : 0);
              String now = Instant.now().toString();
              statement.setString(index++, now);
              statement.setString(index, now);
              statement.executeUpdate();
            }
            replaceOptions(connection, tunnel);
            replaceTags(connection, tunnel.id(), tunnel.tagIds());
            return null;
          });
      markDirty();
    } catch (PersistenceException exception) {
      throw translate("guardar el túnel", exception);
    }
  }

  @Override
  public void delete(UUID id) {
    try {
      database.inTransaction(
          connection -> {
            try (var statement = connection.prepareStatement("DELETE FROM tunnel WHERE id = ?")) {
              statement.setString(1, id.toString());
              statement.executeUpdate();
            }
            return null;
          });
      markDirty();
    } catch (PersistenceException exception) {
      throw translate("borrar el túnel", exception);
    }
  }

  private static String baseSelect() {
    return "SELECT id, name, bind_address, local_port, destination_host, destination_port, final_host_id, jump1_host_id, jump2_host_id, keep_alive_override_seconds, connection_timeout_override_seconds, retry_count_override, project_id, environment_id, is_favorite FROM tunnel";
  }

  private static TunnelRecord read(Connection connection, java.sql.ResultSet result)
      throws SQLException {
    UUID id = UUID.fromString(result.getString("id"));
    return new TunnelRecord(
        id,
        result.getString("name"),
        result.getString("bind_address"),
        result.getInt("local_port"),
        result.getString("destination_host"),
        result.getInt("destination_port"),
        UUID.fromString(result.getString("final_host_id")),
        nullableUuid(result.getString("jump1_host_id")),
        nullableUuid(result.getString("jump2_host_id")),
        nullableLong(result, "keep_alive_override_seconds"),
        nullableLong(result, "connection_timeout_override_seconds"),
        nullableInteger(result, "retry_count_override"),
        nullableUuid(result.getString("project_id")),
        nullableUuid(result.getString("environment_id")),
        result.getInt("is_favorite") == 1,
        loadTags(connection, id),
        loadOptions(connection, id));
  }

  private static Set<UUID> loadTags(Connection connection, UUID tunnelId) throws SQLException {
    try (var statement =
        connection.prepareStatement("SELECT tag_id FROM tunnel_tag WHERE tunnel_id = ?")) {
      statement.setString(1, tunnelId.toString());
      try (var result = statement.executeQuery()) {
        Set<UUID> tags = new HashSet<>();
        while (result.next()) {
          tags.add(UUID.fromString(result.getString(1)));
        }
        return Set.copyOf(tags);
      }
    }
  }

  private static Map<String, String> loadOptions(Connection connection, UUID tunnelId)
      throws SQLException {
    try (var statement =
        connection.prepareStatement(
            "SELECT option_key, option_value FROM tunnel_ssh_option WHERE tunnel_id = ?")) {
      statement.setString(1, tunnelId.toString());
      try (var result = statement.executeQuery()) {
        Map<String, String> options = new HashMap<>();
        while (result.next()) {
          options.put(result.getString(1), result.getString(2));
        }
        return Map.copyOf(options);
      }
    }
  }

  private static void replaceOptions(Connection connection, TunnelRecord tunnel)
      throws SQLException {
    try (var delete =
        connection.prepareStatement("DELETE FROM tunnel_ssh_option WHERE tunnel_id = ?")) {
      delete.setString(1, tunnel.id().toString());
      delete.executeUpdate();
    }
    try (var insert =
        connection.prepareStatement(
            "INSERT INTO tunnel_ssh_option(tunnel_id, option_key, option_value) VALUES (?, ?, ?)")) {
      for (Map.Entry<String, String> option : tunnel.sshOptions().entrySet()) {
        insert.setString(1, tunnel.id().toString());
        insert.setString(2, option.getKey());
        insert.setString(3, option.getValue());
        insert.addBatch();
      }
      insert.executeBatch();
    }
  }

  private static void replaceTags(Connection connection, UUID tunnelId, Set<UUID> tagIds)
      throws SQLException {
    try (var delete = connection.prepareStatement("DELETE FROM tunnel_tag WHERE tunnel_id = ?")) {
      delete.setString(1, tunnelId.toString());
      delete.executeUpdate();
    }
    try (var insert =
        connection.prepareStatement("INSERT INTO tunnel_tag(tunnel_id, tag_id) VALUES (?, ?)")) {
      for (UUID tagId : tagIds) {
        insert.setString(1, tunnelId.toString());
        insert.setString(2, tagId.toString());
        insert.addBatch();
      }
      insert.executeBatch();
    }
  }

  private static void setLong(java.sql.PreparedStatement statement, int index, Long value)
      throws SQLException {
    if (value == null) statement.setNull(index, java.sql.Types.INTEGER);
    else statement.setLong(index, value);
  }

  private static void setInteger(java.sql.PreparedStatement statement, int index, Integer value)
      throws SQLException {
    if (value == null) statement.setNull(index, java.sql.Types.INTEGER);
    else statement.setInt(index, value);
  }

  private static Long nullableLong(java.sql.ResultSet result, String column) throws SQLException {
    long value = result.getLong(column);
    return result.wasNull() ? null : value;
  }

  private static Integer nullableInteger(java.sql.ResultSet result, String column)
      throws SQLException {
    int value = result.getInt(column);
    return result.wasNull() ? null : value;
  }

  private static UUID nullableUuid(String value) {
    return value == null ? null : UUID.fromString(value);
  }
}

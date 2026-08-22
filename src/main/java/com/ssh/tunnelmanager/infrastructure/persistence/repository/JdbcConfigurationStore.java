package com.ssh.tunnelmanager.infrastructure.persistence.repository;

import com.ssh.tunnelmanager.application.persistence.ConfigurationStore;
import com.ssh.tunnelmanager.application.persistence.model.ConfigurationSnapshot;
import com.ssh.tunnelmanager.application.persistence.model.CredentialRecord;
import com.ssh.tunnelmanager.application.persistence.model.EnvironmentRecord;
import com.ssh.tunnelmanager.application.persistence.model.KeyboardShortcutRecord;
import com.ssh.tunnelmanager.application.persistence.model.ProjectRecord;
import com.ssh.tunnelmanager.application.persistence.model.RecentItemRecord;
import com.ssh.tunnelmanager.application.persistence.model.RecentItemType;
import com.ssh.tunnelmanager.application.persistence.model.SettingRecord;
import com.ssh.tunnelmanager.application.persistence.model.SshHostRecord;
import com.ssh.tunnelmanager.application.persistence.model.TagRecord;
import com.ssh.tunnelmanager.application.persistence.model.TrustedHostKeyRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelGroupRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * JDBC implementation that keeps snapshot reads and replacement independent of table repositories.
 */
public final class JdbcConfigurationStore implements ConfigurationStore {
  private final DatabaseFactory database;
  private final Runnable dirtyMarker;

  public JdbcConfigurationStore(DatabaseFactory database) {
    this(database, () -> {});
  }

  public JdbcConfigurationStore(DatabaseFactory database, Runnable dirtyMarker) {
    this.database = database;
    this.dirtyMarker = dirtyMarker;
  }

  @Override
  public ConfigurationSnapshot snapshot() {
    return database.withConnection(JdbcConfigurationStore::readSnapshot);
  }

  @Override
  public void replace(ConfigurationSnapshot snapshot) {
    database.inTransaction(
        connection -> {
          deleteAll(connection);
          insertAll(connection, snapshot);
          return null;
        });
    dirtyMarker.run();
  }

  private static ConfigurationSnapshot readSnapshot(Connection connection) throws SQLException {
    return new ConfigurationSnapshot(
        readSchemaVersion(connection),
        readCredentials(connection),
        readHosts(connection),
        readProjects(connection),
        readEnvironments(connection),
        readTags(connection),
        readTunnels(connection),
        readGroups(connection),
        readTrustedKeys(connection),
        readSettings(connection),
        readRecentItems(connection),
        readShortcuts(connection));
  }

  private static int readSchemaVersion(Connection connection) throws SQLException {
    try (var statement = connection.createStatement();
        var result =
            statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
      result.next();
      return result.getInt(1);
    }
  }

  private static List<CredentialRecord> readCredentials(Connection connection) throws SQLException {
    var values = new ArrayList<CredentialRecord>();
    try (var statement = connection.createStatement();
        var result =
            statement.executeQuery("SELECT id, username, password FROM credential ORDER BY id")) {
      while (result.next())
        values.add(
            new CredentialRecord(
                UUID.fromString(result.getString(1)), result.getString(2), result.getString(3)));
    }
    return List.copyOf(values);
  }

  private static List<SshHostRecord> readHosts(Connection connection) throws SQLException {
    var values = new ArrayList<SshHostRecord>();
    try (var statement = connection.createStatement();
        var result =
            statement.executeQuery(
                "SELECT id, name, hostname, ssh_port, credential_id FROM ssh_host ORDER BY id")) {
      while (result.next())
        values.add(
            new SshHostRecord(
                UUID.fromString(result.getString(1)),
                result.getString(2),
                result.getString(3),
                result.getInt(4),
                UUID.fromString(result.getString(5))));
    }
    return List.copyOf(values);
  }

  private static List<ProjectRecord> readProjects(Connection connection) throws SQLException {
    return readNamed(connection, "project", ProjectRecord.class);
  }

  private static List<EnvironmentRecord> readEnvironments(Connection connection)
      throws SQLException {
    return readNamed(connection, "environment", EnvironmentRecord.class);
  }

  private static List<TagRecord> readTags(Connection connection) throws SQLException {
    return readNamed(connection, "tag", TagRecord.class);
  }

  private static <T> List<T> readNamed(Connection connection, String table, Class<T> type)
      throws SQLException {
    var values = new ArrayList<T>();
    try (var statement = connection.createStatement();
        var result = statement.executeQuery("SELECT id, name FROM " + table + " ORDER BY id")) {
      while (result.next()) {
        UUID id = UUID.fromString(result.getString(1));
        String name = result.getString(2);
        if (type == ProjectRecord.class) values.add(type.cast(new ProjectRecord(id, name)));
        else if (type == EnvironmentRecord.class)
          values.add(type.cast(new EnvironmentRecord(id, name)));
        else values.add(type.cast(new TagRecord(id, name)));
      }
    }
    return List.copyOf(values);
  }

  private static List<TunnelRecord> readTunnels(Connection connection) throws SQLException {
    var values = new ArrayList<TunnelRecord>();
    try (var statement = connection.createStatement();
        var result =
            statement.executeQuery(
                "SELECT id, name, bind_address, local_port, destination_host, destination_port, final_host_id, jump1_host_id, jump2_host_id, keep_alive_override_seconds, connection_timeout_override_seconds, retry_count_override, project_id, environment_id, is_favorite FROM tunnel ORDER BY id")) {
      while (result.next()) {
        UUID id = UUID.fromString(result.getString("id"));
        values.add(
            new TunnelRecord(
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
                relationIds(connection, "SELECT tag_id FROM tunnel_tag WHERE tunnel_id = ?", id),
                options(connection, id)));
      }
    }
    return List.copyOf(values);
  }

  private static List<TunnelGroupRecord> readGroups(Connection connection) throws SQLException {
    var values = new ArrayList<TunnelGroupRecord>();
    try (var statement = connection.createStatement();
        var result =
            statement.executeQuery(
                "SELECT id, name, project_id, environment_id, is_favorite FROM tunnel_group ORDER BY id")) {
      while (result.next()) {
        UUID id = UUID.fromString(result.getString("id"));
        values.add(
            new TunnelGroupRecord(
                id,
                result.getString("name"),
                nullableUuid(result.getString("project_id")),
                nullableUuid(result.getString("environment_id")),
                result.getInt("is_favorite") == 1,
                relationIds(
                    connection, "SELECT tunnel_id FROM tunnel_group_member WHERE group_id = ?", id),
                relationIds(connection, "SELECT tag_id FROM group_tag WHERE group_id = ?", id)));
      }
    }
    return List.copyOf(values);
  }

  private static List<TrustedHostKeyRecord> readTrustedKeys(Connection connection)
      throws SQLException {
    var values = new ArrayList<TrustedHostKeyRecord>();
    try (var statement = connection.createStatement();
        var result =
            statement.executeQuery(
                "SELECT id, hostname, ssh_port, key_algorithm, public_key, fingerprint_sha256, approved_at FROM trusted_host_key ORDER BY id")) {
      while (result.next())
        values.add(
            new TrustedHostKeyRecord(
                UUID.fromString(result.getString(1)),
                result.getString(2),
                result.getInt(3),
                result.getString(4),
                result.getString(5),
                result.getString(6),
                Instant.parse(result.getString(7))));
    }
    return List.copyOf(values);
  }

  private static List<SettingRecord> readSettings(Connection connection) throws SQLException {
    var values = new ArrayList<SettingRecord>();
    try (var statement = connection.createStatement();
        var result =
            statement.executeQuery(
                "SELECT key, value FROM application_setting WHERE key NOT LIKE 'backup.%' ORDER BY key")) {
      while (result.next()) values.add(new SettingRecord(result.getString(1), result.getString(2)));
    }
    return List.copyOf(values);
  }

  private static List<RecentItemRecord> readRecentItems(Connection connection) throws SQLException {
    var values = new ArrayList<RecentItemRecord>();
    try (var statement = connection.createStatement();
        var result =
            statement.executeQuery(
                "SELECT item_type, item_id, last_started_at FROM recent_item ORDER BY last_started_at DESC")) {
      while (result.next())
        values.add(
            new RecentItemRecord(
                RecentItemType.valueOf(result.getString(1)),
                UUID.fromString(result.getString(2)),
                Instant.parse(result.getString(3))));
    }
    return List.copyOf(values);
  }

  private static List<KeyboardShortcutRecord> readShortcuts(Connection connection)
      throws SQLException {
    var values = new ArrayList<KeyboardShortcutRecord>();
    try (var statement = connection.createStatement();
        var result =
            statement.executeQuery(
                "SELECT action, shortcut FROM keyboard_shortcut ORDER BY action")) {
      while (result.next())
        values.add(new KeyboardShortcutRecord(result.getString(1), result.getString(2)));
    }
    return List.copyOf(values);
  }

  private static Set<UUID> relationIds(Connection connection, String sql, UUID id)
      throws SQLException {
    Set<UUID> values = new HashSet<>();
    try (var statement = connection.prepareStatement(sql)) {
      statement.setString(1, id.toString());
      try (var result = statement.executeQuery()) {
        while (result.next()) values.add(UUID.fromString(result.getString(1)));
      }
    }
    return Set.copyOf(values);
  }

  private static Map<String, String> options(Connection connection, UUID id) throws SQLException {
    Map<String, String> values = new HashMap<>();
    try (var statement =
        connection.prepareStatement(
            "SELECT option_key, option_value FROM tunnel_ssh_option WHERE tunnel_id = ?")) {
      statement.setString(1, id.toString());
      try (var result = statement.executeQuery()) {
        while (result.next()) values.put(result.getString(1), result.getString(2));
      }
    }
    return Map.copyOf(values);
  }

  private static void deleteAll(Connection connection) throws SQLException {
    for (String table :
        List.of(
            "recent_item",
            "keyboard_shortcut",
            "application_setting",
            "trusted_host_key",
            "group_tag",
            "tunnel_tag",
            "tunnel_ssh_option",
            "tunnel_group_member",
            "tunnel_group",
            "tunnel",
            "ssh_host",
            "credential",
            "tag",
            "project",
            "environment")) {
      try (var statement = connection.createStatement()) {
        statement.executeUpdate("DELETE FROM " + table);
      }
    }
    try (var statement = connection.createStatement()) {
      statement.executeUpdate("DELETE FROM application_setting WHERE key NOT LIKE 'backup.%'");
    }
  }

  private static void insertAll(Connection connection, ConfigurationSnapshot snapshot)
      throws SQLException {
    for (CredentialRecord value : snapshot.credentials())
      execute(
          connection,
          "INSERT INTO credential(id, username, password) VALUES (?, ?, ?)",
          value.id().toString(),
          value.username(),
          value.password());
    for (SshHostRecord value : snapshot.hosts())
      execute(
          connection,
          "INSERT INTO ssh_host(id, name, hostname, ssh_port, credential_id) VALUES (?, ?, ?, ?, ?)",
          value.id().toString(),
          value.name(),
          value.hostname(),
          value.sshPort(),
          value.credentialId().toString());
    for (ProjectRecord value : snapshot.projects())
      execute(
          connection,
          "INSERT INTO project(id, name) VALUES (?, ?)",
          value.id().toString(),
          value.name());
    for (EnvironmentRecord value : snapshot.environments())
      execute(
          connection,
          "INSERT INTO environment(id, name) VALUES (?, ?)",
          value.id().toString(),
          value.name());
    for (TagRecord value : snapshot.tags())
      execute(
          connection,
          "INSERT INTO tag(id, name) VALUES (?, ?)",
          value.id().toString(),
          value.name());
    for (TrustedHostKeyRecord value : snapshot.trustedHostKeys())
      execute(
          connection,
          "INSERT INTO trusted_host_key(id, hostname, ssh_port, key_algorithm, public_key, fingerprint_sha256, approved_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
          value.id().toString(),
          value.hostname(),
          value.sshPort(),
          value.keyAlgorithm(),
          value.publicKey(),
          value.fingerprintSha256(),
          value.approvedAt().toString());
    for (TunnelRecord value : snapshot.tunnels()) insertTunnel(connection, value);
    for (TunnelGroupRecord value : snapshot.groups()) insertGroup(connection, value);
    for (SettingRecord value : snapshot.settings())
      execute(
          connection,
          "INSERT INTO application_setting(key, value) VALUES (?, ?)",
          value.key(),
          value.value());
    for (RecentItemRecord value : snapshot.recentItems())
      execute(
          connection,
          "INSERT INTO recent_item(item_type, item_id, last_started_at) VALUES (?, ?, ?)",
          value.itemType().name(),
          value.itemId().toString(),
          value.lastStartedAt().toString());
    for (KeyboardShortcutRecord value : snapshot.keyboardShortcuts())
      execute(
          connection,
          "INSERT INTO keyboard_shortcut(action, shortcut) VALUES (?, ?)",
          value.action(),
          value.shortcut());
  }

  private static void insertTunnel(Connection connection, TunnelRecord value) throws SQLException {
    try (var statement =
        connection.prepareStatement(
            "INSERT INTO tunnel(id, name, bind_address, local_port, destination_host, destination_port, final_host_id, jump1_host_id, jump2_host_id, keep_alive_override_seconds, connection_timeout_override_seconds, retry_count_override, project_id, environment_id, is_favorite, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
      int i = 1;
      statement.setString(i++, value.id().toString());
      statement.setString(i++, value.name());
      statement.setString(i++, value.bindAddress());
      statement.setInt(i++, value.localPort());
      statement.setString(i++, value.destinationHost());
      statement.setInt(i++, value.destinationPort());
      setUuid(statement, i++, value.finalHostId());
      setUuid(statement, i++, value.jump1HostId());
      setUuid(statement, i++, value.jump2HostId());
      setLong(statement, i++, value.keepAliveOverrideSeconds());
      setLong(statement, i++, value.connectionTimeoutOverrideSeconds());
      setInteger(statement, i++, value.retryCountOverride());
      setUuid(statement, i++, value.projectId());
      setUuid(statement, i++, value.environmentId());
      statement.setInt(i++, value.favorite() ? 1 : 0);
      statement.setString(i++, Instant.now().toString());
      statement.setString(i, Instant.now().toString());
      statement.executeUpdate();
    }
    for (Map.Entry<String, String> option : value.sshOptions().entrySet())
      execute(
          connection,
          "INSERT INTO tunnel_ssh_option(tunnel_id, option_key, option_value) VALUES (?, ?, ?)",
          value.id().toString(),
          option.getKey(),
          option.getValue());
    for (UUID tagId : value.tagIds())
      execute(
          connection,
          "INSERT INTO tunnel_tag(tunnel_id, tag_id) VALUES (?, ?)",
          value.id().toString(),
          tagId.toString());
  }

  private static void insertGroup(Connection connection, TunnelGroupRecord value)
      throws SQLException {
    try (var statement =
        connection.prepareStatement(
            "INSERT INTO tunnel_group(id, name, project_id, environment_id, is_favorite) VALUES (?, ?, ?, ?, ?)")) {
      statement.setString(1, value.id().toString());
      statement.setString(2, value.name());
      setUuid(statement, 3, value.projectId());
      setUuid(statement, 4, value.environmentId());
      statement.setInt(5, value.favorite() ? 1 : 0);
      statement.executeUpdate();
    }
    for (UUID tunnelId : value.tunnelIds())
      execute(
          connection,
          "INSERT INTO tunnel_group_member(group_id, tunnel_id) VALUES (?, ?)",
          value.id().toString(),
          tunnelId.toString());
    for (UUID tagId : value.tagIds())
      execute(
          connection,
          "INSERT INTO group_tag(group_id, tag_id) VALUES (?, ?)",
          value.id().toString(),
          tagId.toString());
  }

  private static void execute(Connection connection, String sql, Object... values)
      throws SQLException {
    try (var statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < values.length; i++) {
        Object value = values[i];
        if (value == null) statement.setNull(i + 1, java.sql.Types.VARCHAR);
        else if (value instanceof Integer integer) statement.setInt(i + 1, integer);
        else statement.setString(i + 1, value.toString());
      }
      statement.executeUpdate();
    }
  }

  private static void setUuid(PreparedStatement statement, int index, UUID value)
      throws SQLException {
    if (value == null) statement.setNull(index, java.sql.Types.VARCHAR);
    else statement.setString(index, value.toString());
  }

  private static void setLong(PreparedStatement statement, int index, Long value)
      throws SQLException {
    if (value == null) statement.setNull(index, java.sql.Types.INTEGER);
    else statement.setLong(index, value);
  }

  private static void setInteger(PreparedStatement statement, int index, Integer value)
      throws SQLException {
    if (value == null) statement.setNull(index, java.sql.Types.INTEGER);
    else statement.setInt(index, value);
  }

  private static UUID nullableUuid(String value) {
    return value == null ? null : UUID.fromString(value);
  }

  private static Long nullableLong(ResultSet result, String column) throws SQLException {
    long value = result.getLong(column);
    return result.wasNull() ? null : value;
  }

  private static Integer nullableInteger(ResultSet result, String column) throws SQLException {
    int value = result.getInt(column);
    return result.wasNull() ? null : value;
  }
}

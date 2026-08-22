package com.ssh.tunnelmanager.infrastructure.persistence.repository;

import com.ssh.tunnelmanager.application.persistence.ApplicationSettingsRepository;
import com.ssh.tunnelmanager.application.persistence.model.SettingRecord;
import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;

public final class JdbcApplicationSettingsRepository extends JdbcRepositorySupport
    implements ApplicationSettingsRepository {
  public JdbcApplicationSettingsRepository(DatabaseFactory database) {
    super(database);
  }

  public JdbcApplicationSettingsRepository(DatabaseFactory database, Runnable dirtyMarker) {
    super(database, dirtyMarker);
  }

  @Override
  public Optional<String> findValue(String key) {
    return database.withConnection(
        connection -> {
          try (var statement =
              connection.prepareStatement("SELECT value FROM application_setting WHERE key = ?")) {
            statement.setString(1, key);
            try (var result = statement.executeQuery()) {
              return result.next() ? Optional.of(result.getString(1)) : Optional.empty();
            }
          }
        });
  }

  @Override
  public List<SettingRecord> findAll() {
    return database.withConnection(
        connection -> {
          try (var statement = connection.createStatement();
              var result =
                  statement.executeQuery(
                      "SELECT key, value FROM application_setting ORDER BY key")) {
            var settings = new java.util.ArrayList<SettingRecord>();
            while (result.next())
              settings.add(new SettingRecord(result.getString(1), result.getString(2)));
            return List.copyOf(settings);
          }
        });
  }

  @Override
  public void put(String key, String value) {
    mutate(
        "guardar la configuración",
        "INSERT INTO application_setting(key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        key,
        value);
  }

  @Override
  public void delete(String key) {
    mutate("borrar la configuración", "DELETE FROM application_setting WHERE key = ?", key, null);
  }

  private void mutate(String operation, String sql, String key, String value) {
    try {
      database.withConnection(
          connection -> {
            try (var statement = connection.prepareStatement(sql)) {
              statement.setString(1, key);
              if (value != null) statement.setString(2, value);
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

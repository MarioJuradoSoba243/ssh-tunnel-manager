package com.ssh.tunnelmanager.infrastructure.persistence.repository;

import com.ssh.tunnelmanager.application.persistence.KeyboardShortcutRepository;
import com.ssh.tunnelmanager.application.persistence.model.KeyboardShortcutRecord;
import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;

public final class JdbcKeyboardShortcutRepository extends JdbcRepositorySupport
    implements KeyboardShortcutRepository {
  public JdbcKeyboardShortcutRepository(DatabaseFactory database) {
    super(database);
  }

  public JdbcKeyboardShortcutRepository(DatabaseFactory database, Runnable dirtyMarker) {
    super(database, dirtyMarker);
  }

  @Override
  public Optional<KeyboardShortcutRecord> findByAction(String action) {
    return database.withConnection(
        connection -> {
          try (var statement =
              connection.prepareStatement(
                  "SELECT action, shortcut FROM keyboard_shortcut WHERE action = ?")) {
            statement.setString(1, action);
            try (var result = statement.executeQuery()) {
              return result.next()
                  ? Optional.of(
                      new KeyboardShortcutRecord(result.getString(1), result.getString(2)))
                  : Optional.empty();
            }
          }
        });
  }

  @Override
  public List<KeyboardShortcutRecord> findAll() {
    return database.withConnection(
        connection -> {
          try (var statement = connection.createStatement();
              var result =
                  statement.executeQuery(
                      "SELECT action, shortcut FROM keyboard_shortcut ORDER BY action")) {
            var shortcuts = new java.util.ArrayList<KeyboardShortcutRecord>();
            while (result.next())
              shortcuts.add(new KeyboardShortcutRecord(result.getString(1), result.getString(2)));
            return List.copyOf(shortcuts);
          }
        });
  }

  @Override
  public void save(KeyboardShortcutRecord shortcut) {
    mutate(
        "guardar el atajo",
        "INSERT INTO keyboard_shortcut(action, shortcut) VALUES (?, ?) ON CONFLICT(action) DO UPDATE SET shortcut = excluded.shortcut",
        shortcut.action(),
        shortcut.shortcut());
  }

  @Override
  public void delete(String action) {
    mutate("borrar el atajo", "DELETE FROM keyboard_shortcut WHERE action = ?", action, null);
  }

  private void mutate(String operation, String sql, String action, String shortcut) {
    try {
      database.withConnection(
          connection -> {
            try (var statement = connection.prepareStatement(sql)) {
              statement.setString(1, action);
              if (shortcut != null) statement.setString(2, shortcut);
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

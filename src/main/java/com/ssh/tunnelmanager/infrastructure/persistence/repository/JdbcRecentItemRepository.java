package com.ssh.tunnelmanager.infrastructure.persistence.repository;

import com.ssh.tunnelmanager.application.persistence.RecentItemRepository;
import com.ssh.tunnelmanager.application.persistence.model.RecentItemRecord;
import com.ssh.tunnelmanager.application.persistence.model.RecentItemType;
import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class JdbcRecentItemRepository extends JdbcRepositorySupport
    implements RecentItemRepository {
  private static final int MAX_RECENT = 8;

  public JdbcRecentItemRepository(DatabaseFactory database) {
    super(database);
  }

  public JdbcRecentItemRepository(DatabaseFactory database, Runnable dirtyMarker) {
    super(database, dirtyMarker);
  }

  @Override
  public void record(RecentItemRecord item) {
    try {
      database.inTransaction(
          connection -> {
            try (var statement =
                connection.prepareStatement(
                    "INSERT INTO recent_item(item_type, item_id, last_started_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT(item_type, item_id) DO UPDATE SET last_started_at = excluded.last_started_at")) {
              statement.setString(1, item.itemType().name());
              statement.setString(2, item.itemId().toString());
              statement.setString(3, item.lastStartedAt().toString());
              statement.executeUpdate();
            }
            try (var statement =
                connection.prepareStatement(
                    "DELETE FROM recent_item WHERE rowid IN (SELECT rowid FROM recent_item ORDER BY last_started_at DESC LIMIT -1 OFFSET ?)"); ) {
              statement.setInt(1, MAX_RECENT);
              statement.executeUpdate();
            }
            return null;
          });
      markDirty();
    } catch (PersistenceException exception) {
      throw translate("guardar el histórico de recientes", exception);
    }
  }

  @Override
  public List<RecentItemRecord> findRecent(int limit) {
    if (limit < 0)
      throw new IllegalArgumentException("El límite de recientes no puede ser negativo");
    return database.withConnection(
        connection -> {
          try (var statement =
              connection.prepareStatement(
                  "SELECT item_type, item_id, last_started_at FROM recent_item ORDER BY last_started_at DESC LIMIT ?")) {
            statement.setInt(1, limit);
            try (var result = statement.executeQuery()) {
              var items = new ArrayList<RecentItemRecord>();
              while (result.next()) {
                items.add(
                    new RecentItemRecord(
                        RecentItemType.valueOf(result.getString(1)),
                        UUID.fromString(result.getString(2)),
                        Instant.parse(result.getString(3))));
              }
              return List.copyOf(items);
            }
          }
        });
  }

  @Override
  public void delete(UUID itemId) {
    mutate("DELETE FROM recent_item WHERE item_id = ?", itemId.toString());
  }

  @Override
  public void deleteAll() {
    mutate("DELETE FROM recent_item", null);
  }

  private void mutate(String sql, String itemId) {
    try {
      database.withConnection(
          connection -> {
            try (var statement = connection.prepareStatement(sql)) {
              if (itemId != null) statement.setString(1, itemId);
              statement.executeUpdate();
              return null;
            }
          });
      markDirty();
    } catch (PersistenceException exception) {
      throw translate("borrar el histórico de recientes", exception);
    }
  }
}

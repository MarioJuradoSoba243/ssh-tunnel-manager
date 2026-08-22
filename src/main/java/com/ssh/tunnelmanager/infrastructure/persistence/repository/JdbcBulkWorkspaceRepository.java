package com.ssh.tunnelmanager.infrastructure.persistence.repository;

import com.ssh.tunnelmanager.application.persistence.BulkWorkspaceRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceException;
import java.util.Set;
import java.util.UUID;

public final class JdbcBulkWorkspaceRepository extends JdbcRepositorySupport
    implements BulkWorkspaceRepository {
  public JdbcBulkWorkspaceRepository(DatabaseFactory database) {
    super(database);
  }

  @Override
  public void setTunnelFavorites(Set<UUID> tunnelIds, boolean favorite) {
    updateTunnels("UPDATE tunnel SET is_favorite = ? WHERE id = ?", tunnelIds, favorite ? 1 : 0);
  }

  @Override
  public void setTunnelClassification(Set<UUID> tunnelIds, UUID projectId, UUID environmentId) {
    try {
      database.inTransaction(
          connection -> {
            try (var statement =
                connection.prepareStatement(
                    "UPDATE tunnel SET project_id = ?, environment_id = ?, updated_at = ? WHERE id = ?")) {
              for (UUID tunnelId : tunnelIds) {
                setUuid(statement, 1, projectId);
                setUuid(statement, 2, environmentId);
                statement.setString(3, java.time.Instant.now().toString());
                statement.setString(4, tunnelId.toString());
                statement.addBatch();
              }
              statement.executeBatch();
            }
            return null;
          });
      markDirty();
    } catch (PersistenceException exception) {
      throw translate("clasificar túneles", exception);
    }
  }

  @Override
  public void addTunnelsToGroup(Set<UUID> tunnelIds, UUID groupId) {
    relationUpdate(
        "INSERT OR IGNORE INTO tunnel_group_member(group_id, tunnel_id) VALUES (?, ?)",
        tunnelIds,
        groupId);
  }

  @Override
  public void removeTunnelsFromGroup(Set<UUID> tunnelIds, UUID groupId) {
    relationUpdate(
        "DELETE FROM tunnel_group_member WHERE group_id = ? AND tunnel_id = ?", tunnelIds, groupId);
  }

  private void updateTunnels(String sql, Set<UUID> tunnelIds, int value) {
    try {
      database.inTransaction(
          connection -> {
            try (var statement = connection.prepareStatement(sql)) {
              for (UUID tunnelId : tunnelIds) {
                statement.setInt(1, value);
                statement.setString(2, tunnelId.toString());
                statement.addBatch();
              }
              statement.executeBatch();
            }
            return null;
          });
      markDirty();
    } catch (PersistenceException exception) {
      throw translate("actualizar favoritos", exception);
    }
  }

  private void relationUpdate(String sql, Set<UUID> tunnelIds, UUID groupId) {
    try {
      database.inTransaction(
          connection -> {
            try (var statement = connection.prepareStatement(sql)) {
              for (UUID tunnelId : tunnelIds) {
                statement.setString(1, groupId.toString());
                statement.setString(2, tunnelId.toString());
                statement.addBatch();
              }
              statement.executeBatch();
            }
            return null;
          });
      markDirty();
    } catch (PersistenceException exception) {
      throw translate("actualizar pertenencia al grupo", exception);
    }
  }
}

package com.ssh.tunnelmanager.application.persistence;

import java.util.Set;
import java.util.UUID;

/** Atomic updates for workspace classifications and group membership. */
public interface BulkWorkspaceRepository {
  void setTunnelFavorites(Set<UUID> tunnelIds, boolean favorite);

  void setTunnelClassification(Set<UUID> tunnelIds, UUID projectId, UUID environmentId);

  void addTunnelsToGroup(Set<UUID> tunnelIds, UUID groupId);

  void removeTunnelsFromGroup(Set<UUID> tunnelIds, UUID groupId);
}

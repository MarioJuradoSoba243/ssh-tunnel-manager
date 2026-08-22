package com.ssh.tunnelmanager.application.workspace;

import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager;
import com.ssh.tunnelmanager.domain.tunnel.ActualState;
import java.util.Set;
import java.util.UUID;

public record TunnelFilter(
    String query,
    UUID projectId,
    UUID environmentId,
    UUID groupId,
    Set<UUID> tagIds,
    ActualState state) {
  public TunnelFilter {
    query = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
    tagIds = Set.copyOf(tagIds == null ? Set.of() : tagIds);
  }

  public boolean matches(
      TunnelRecord tunnel,
      TunnelRuntimeManager runtime,
      java.util.function.Function<UUID, Set<UUID>> groupMembers) {
    if (!query.isEmpty()
        && !tunnel.name().toLowerCase(java.util.Locale.ROOT).contains(query)
        && !tunnel.destinationHost().toLowerCase(java.util.Locale.ROOT).contains(query))
      return false;
    if (projectId != null && !projectId.equals(tunnel.projectId())) return false;
    if (environmentId != null && !environmentId.equals(tunnel.environmentId())) return false;
    if (groupId != null && !groupMembers.apply(groupId).contains(tunnel.id())) return false;
    if (!tunnel.tagIds().containsAll(tagIds)) return false;
    return state == null || runtime.runtime(tunnel.id()).actualState() == state;
  }
}

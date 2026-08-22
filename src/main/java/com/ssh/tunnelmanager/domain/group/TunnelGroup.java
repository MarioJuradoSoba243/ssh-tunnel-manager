package com.ssh.tunnelmanager.domain.group;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record TunnelGroup(
    UUID id,
    String name,
    Set<UUID> tunnelIds,
    UUID projectId,
    UUID environmentId,
    boolean favorite,
    Set<UUID> tagIds) {
  public TunnelGroup {
    Objects.requireNonNull(id, "id");
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("El nombre del grupo es obligatorio");
    tunnelIds = Set.copyOf(tunnelIds == null ? Set.of() : tunnelIds);
    tagIds = Set.copyOf(tagIds == null ? Set.of() : tagIds);
  }

  public TunnelGroup(UUID id, String name, Set<UUID> tunnelIds) {
    this(id, name, tunnelIds, null, null, false, Set.of());
  }
}

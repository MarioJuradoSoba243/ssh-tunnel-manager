package com.ssh.tunnelmanager.domain.tunnel;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record Tunnel(
    UUID id,
    String name,
    LocalForward localForward,
    RemoteDestination destination,
    SshRoute route,
    TunnelOptions options,
    UUID projectId,
    UUID environmentId,
    boolean favorite,
    Set<UUID> tagIds) {
  public Tunnel {
    Objects.requireNonNull(id, "id");
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("El nombre es obligatorio");
    Objects.requireNonNull(localForward, "localForward");
    Objects.requireNonNull(destination, "destination");
    Objects.requireNonNull(route, "route");
    options = options == null ? TunnelOptions.defaults() : options;
    tagIds = tagIds == null ? Set.of() : Set.copyOf(tagIds);
  }

  public Tunnel(
      UUID id,
      String name,
      LocalForward localForward,
      RemoteDestination destination,
      SshRoute route,
      TunnelOptions options) {
    this(id, name, localForward, destination, route, options, null, null, false, Set.of());
  }
}

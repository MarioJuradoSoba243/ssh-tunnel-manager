package com.ssh.tunnelmanager.domain.organization;

import java.util.Objects;
import java.util.UUID;

public record Project(UUID id, String name) {
  public Project {
    Objects.requireNonNull(id, "id");
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("El nombre del proyecto es obligatorio");
    }
    name = name.trim();
  }
}

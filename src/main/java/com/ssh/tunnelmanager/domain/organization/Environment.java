package com.ssh.tunnelmanager.domain.organization;

import java.util.Objects;
import java.util.UUID;

public record Environment(UUID id, String name) {
  public Environment {
    Objects.requireNonNull(id, "id");
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("El nombre del entorno es obligatorio");
    }
    name = name.trim();
  }
}

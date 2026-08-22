package com.ssh.tunnelmanager.domain.organization;

import java.util.Objects;
import java.util.UUID;

public record Tag(UUID id, String name) {
  public Tag {
    Objects.requireNonNull(id, "id");
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("El nombre de la etiqueta es obligatorio");
    }
    name = name.trim();
  }
}

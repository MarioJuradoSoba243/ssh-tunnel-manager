package com.ssh.tunnelmanager.domain.credential;

import java.util.Objects;

public record Credential(String username, String password) {
  public Credential {
    requireText(username, "username");
    Objects.requireNonNull(password, "password");
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(field + " no puede estar vacío");
  }
}

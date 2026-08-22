package com.ssh.tunnelmanager.domain.host;

import com.ssh.tunnelmanager.domain.credential.Credential;
import java.util.Objects;
import java.util.UUID;

public record SshHost(UUID id, String name, String hostname, int sshPort, Credential credential) {
  public SshHost {
    Objects.requireNonNull(id, "id");
    requireText(name, "name");
    requireText(hostname, "hostname");
    if (sshPort < 1 || sshPort > 65535) throw new IllegalArgumentException("Puerto SSH inválido");
    Objects.requireNonNull(credential, "credential");
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(field + " no puede estar vacío");
  }
}

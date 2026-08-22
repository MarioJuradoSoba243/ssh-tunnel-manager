package com.ssh.tunnelmanager.domain.tunnel;

public record RemoteDestination(String host, int port) {
  public RemoteDestination {
    if (host == null || host.isBlank())
      throw new IllegalArgumentException("destinationHost requerido");
    if (port < 1 || port > 65535) throw new IllegalArgumentException("Puerto destino inválido");
  }
}

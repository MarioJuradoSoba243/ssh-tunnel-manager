package com.ssh.tunnelmanager.domain.tunnel;

import java.util.Map;
import java.util.Set;

public record TunnelOptions(
    Long keepAliveSeconds,
    Long connectionTimeoutSeconds,
    Integer retryCount,
    Map<String, String> sshOptions) {
  public TunnelOptions {
    if (keepAliveSeconds != null && keepAliveSeconds < 1)
      throw new IllegalArgumentException("Keep-alive inválido");
    if (connectionTimeoutSeconds != null && connectionTimeoutSeconds < 1)
      throw new IllegalArgumentException("Timeout inválido");
    if (retryCount != null && retryCount < 0)
      throw new IllegalArgumentException("Número de reintentos inválido");
    sshOptions = sshOptions == null ? Map.of() : Map.copyOf(sshOptions);
    Set<String> reserved =
        Set.of(
            "HostName",
            "Port",
            "User",
            "ProxyJump",
            "LocalForward",
            "bindAddress",
            "localPort",
            "destinationHost",
            "destinationPort");
    if (sshOptions.keySet().stream().anyMatch(reserved::contains))
      throw new IllegalArgumentException("Una opción SSH contradice un campo estructurado");
  }

  public static TunnelOptions defaults() {
    return new TunnelOptions(null, null, null, Map.of());
  }
}

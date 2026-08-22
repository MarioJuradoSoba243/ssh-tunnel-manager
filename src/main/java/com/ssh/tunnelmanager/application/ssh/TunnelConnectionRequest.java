package com.ssh.tunnelmanager.application.ssh;

import com.ssh.tunnelmanager.domain.credential.Credential;
import com.ssh.tunnelmanager.domain.tunnel.LocalForward;
import com.ssh.tunnelmanager.domain.tunnel.RemoteDestination;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record TunnelConnectionRequest(
    LocalForward localForward,
    RemoteDestination destination,
    List<RouteHop> route,
    Duration connectionTimeout,
    Duration keepAlive,
    Map<String, String> sshOptions,
    Integer retryCount) {
  public TunnelConnectionRequest {
    Objects.requireNonNull(localForward, "localForward");
    Objects.requireNonNull(destination, "destination");
    route = List.copyOf(Objects.requireNonNull(route, "route"));
    if (route.isEmpty() || route.size() > 3)
      throw new IllegalArgumentException("Ruta SSH inválida");
    if (connectionTimeout.isNegative() || connectionTimeout.isZero())
      throw new IllegalArgumentException("Timeout inválido");
    if (keepAlive.isNegative() || keepAlive.isZero())
      throw new IllegalArgumentException("Keep-alive inválido");
    sshOptions = sshOptions == null ? Map.of() : Map.copyOf(sshOptions);
    if (retryCount != null && retryCount < 0)
      throw new IllegalArgumentException("Reintentos inválidos");
  }

  public TunnelConnectionRequest(
      LocalForward localForward,
      RemoteDestination destination,
      List<RouteHop> route,
      Duration connectionTimeout,
      Duration keepAlive,
      Map<String, String> sshOptions) {
    this(localForward, destination, route, connectionTimeout, keepAlive, sshOptions, null);
  }

  public record RouteHop(String hostname, int sshPort, Credential credential, String position) {
    public RouteHop {
      if (hostname == null || hostname.isBlank())
        throw new IllegalArgumentException("hostname requerido");
      if (sshPort < 1 || sshPort > 65535) throw new IllegalArgumentException("Puerto SSH inválido");
      Objects.requireNonNull(credential, "credential");
      Objects.requireNonNull(position, "position");
    }
  }
}

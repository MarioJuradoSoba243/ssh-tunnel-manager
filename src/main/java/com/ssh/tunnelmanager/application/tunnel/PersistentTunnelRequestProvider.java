package com.ssh.tunnelmanager.application.tunnel;

import com.ssh.tunnelmanager.application.persistence.CredentialRepository;
import com.ssh.tunnelmanager.application.persistence.SshHostRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.persistence.model.CredentialRecord;
import com.ssh.tunnelmanager.application.persistence.model.SshHostRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import com.ssh.tunnelmanager.application.ssh.TunnelConnectionRequest;
import com.ssh.tunnelmanager.domain.credential.Credential;
import com.ssh.tunnelmanager.domain.tunnel.LocalForward;
import com.ssh.tunnelmanager.domain.tunnel.RemoteDestination;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PersistentTunnelRequestProvider implements TunnelRequestProvider {
  private final TunnelRepository tunnels;
  private final SshHostRepository hosts;
  private final CredentialRepository credentials;
  private final Duration defaultKeepAlive;
  private final Duration defaultTimeout;

  public PersistentTunnelRequestProvider(
      TunnelRepository tunnels, SshHostRepository hosts, CredentialRepository credentials) {
    this(tunnels, hosts, credentials, Duration.ofSeconds(30), Duration.ofSeconds(15));
  }

  public PersistentTunnelRequestProvider(
      TunnelRepository tunnels,
      SshHostRepository hosts,
      CredentialRepository credentials,
      Duration defaultKeepAlive,
      Duration defaultTimeout) {
    this.tunnels = tunnels;
    this.hosts = hosts;
    this.credentials = credentials;
    this.defaultKeepAlive = defaultKeepAlive;
    this.defaultTimeout = defaultTimeout;
  }

  @Override
  public TunnelConnectionRequest requestFor(UUID tunnelId) {
    TunnelRecord tunnel =
        tunnels
            .findById(tunnelId)
            .orElseThrow(() -> new IllegalArgumentException("Túnel no encontrado: " + tunnelId));
    List<TunnelConnectionRequest.RouteHop> route = new ArrayList<>();
    if (tunnel.jump1HostId() != null) route.add(hop(tunnel.jump1HostId(), "jump-1"));
    if (tunnel.jump2HostId() != null) route.add(hop(tunnel.jump2HostId(), "jump-2"));
    route.add(hop(tunnel.finalHostId(), "final"));
    return new TunnelConnectionRequest(
        new LocalForward(tunnel.bindAddress(), tunnel.localPort()),
        new RemoteDestination(tunnel.destinationHost(), tunnel.destinationPort()),
        route,
        seconds(tunnel.connectionTimeoutOverrideSeconds(), defaultTimeout),
        seconds(tunnel.keepAliveOverrideSeconds(), defaultKeepAlive),
        tunnel.sshOptions(),
        tunnel.retryCountOverride());
  }

  private TunnelConnectionRequest.RouteHop hop(UUID hostId, String position) {
    SshHostRecord host =
        hosts
            .findById(hostId)
            .orElseThrow(() -> new IllegalArgumentException("Host no encontrado: " + hostId));
    CredentialRecord credential =
        credentials
            .findById(host.credentialId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Credencial no encontrada: " + host.credentialId()));
    return new TunnelConnectionRequest.RouteHop(
        host.hostname(),
        host.sshPort(),
        new Credential(credential.username(), credential.password()),
        position);
  }

  private static Duration seconds(Long override, Duration defaultValue) {
    return override == null ? defaultValue : Duration.ofSeconds(override);
  }
}

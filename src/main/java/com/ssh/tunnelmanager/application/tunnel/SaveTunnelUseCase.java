package com.ssh.tunnelmanager.application.tunnel;

import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import com.ssh.tunnelmanager.domain.tunnel.Tunnel;

public final class SaveTunnelUseCase {
  private final TunnelRepository tunnels;

  public SaveTunnelUseCase(TunnelRepository tunnels) {
    this.tunnels = tunnels;
  }

  public void execute(Tunnel tunnel) {
    tunnels.save(
        new TunnelRecord(
            tunnel.id(),
            tunnel.name(),
            tunnel.localForward().bindAddress(),
            tunnel.localForward().localPort(),
            tunnel.destination().host(),
            tunnel.destination().port(),
            tunnel.route().finalHost().id(),
            tunnel.route().jumps().size() > 0 ? tunnel.route().jumps().get(0).id() : null,
            tunnel.route().jumps().size() > 1 ? tunnel.route().jumps().get(1).id() : null,
            tunnel.options().keepAliveSeconds(),
            tunnel.options().connectionTimeoutSeconds(),
            tunnel.options().retryCount(),
            tunnel.projectId(),
            tunnel.environmentId(),
            tunnel.favorite(),
            tunnel.tagIds(),
            tunnel.options().sshOptions()));
  }
}

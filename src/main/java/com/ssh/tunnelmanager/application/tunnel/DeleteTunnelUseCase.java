package com.ssh.tunnelmanager.application.tunnel;

import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import java.util.UUID;

public final class DeleteTunnelUseCase {
  private final TunnelRepository tunnels;

  public DeleteTunnelUseCase(TunnelRepository tunnels) {
    this.tunnels = tunnels;
  }

  public void execute(UUID tunnelId) {
    tunnels.delete(tunnelId);
  }
}

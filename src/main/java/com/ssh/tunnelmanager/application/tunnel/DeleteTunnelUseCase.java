package com.ssh.tunnelmanager.application.tunnel;

import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager;
import java.util.UUID;

public final class DeleteTunnelUseCase {
  private final TunnelRepository tunnels;
  private final TunnelRuntimeManager runtime;

  public DeleteTunnelUseCase(TunnelRepository tunnels) {
    this(tunnels, null);
  }

  public DeleteTunnelUseCase(TunnelRepository tunnels, TunnelRuntimeManager runtime) {
    this.tunnels = tunnels;
    this.runtime = runtime;
  }

  public void execute(UUID tunnelId) {
    if (runtime != null) runtime.stop(tunnelId);
    tunnels.delete(tunnelId);
  }
}

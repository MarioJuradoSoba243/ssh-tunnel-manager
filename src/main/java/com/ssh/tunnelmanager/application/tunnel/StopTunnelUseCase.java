package com.ssh.tunnelmanager.application.tunnel;

import com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager;
import java.util.UUID;

public final class StopTunnelUseCase {
  private final TunnelRuntimeManager manager;

  public StopTunnelUseCase(TunnelRuntimeManager manager) {
    this.manager = manager;
  }

  public void execute(UUID tunnelId) {
    manager.stop(tunnelId);
  }
}

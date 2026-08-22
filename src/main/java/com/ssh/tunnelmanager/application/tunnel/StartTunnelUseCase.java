package com.ssh.tunnelmanager.application.tunnel;

import com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager;
import java.util.UUID;

public final class StartTunnelUseCase {
  private final TunnelRuntimeManager manager;

  public StartTunnelUseCase(TunnelRuntimeManager manager) {
    this.manager = manager;
  }

  public void execute(UUID tunnelId) {
    manager.start(tunnelId);
  }
}

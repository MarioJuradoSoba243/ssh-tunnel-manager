package com.ssh.tunnelmanager.application.tunnel;

import com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager;
import java.util.UUID;

public final class RetryTunnelUseCase {
  private final TunnelRuntimeManager manager;

  public RetryTunnelUseCase(TunnelRuntimeManager manager) {
    this.manager = manager;
  }

  public void execute(UUID tunnelId) {
    manager.retry(tunnelId);
  }
}

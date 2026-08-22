package com.ssh.tunnelmanager.application.tunnel;

import com.ssh.tunnelmanager.application.recent.RecentItemService;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager;
import java.util.UUID;

public final class StartTunnelUseCase {
  private final TunnelRuntimeManager manager;
  private final RecentItemService recents;

  public StartTunnelUseCase(TunnelRuntimeManager manager) {
    this(manager, null);
  }

  public StartTunnelUseCase(TunnelRuntimeManager manager, RecentItemService recents) {
    this.manager = manager;
    this.recents = recents;
  }

  public void execute(UUID tunnelId) {
    manager.start(tunnelId);
    if (recents != null) recents.recordTunnelStart(tunnelId);
  }
}

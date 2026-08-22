package com.ssh.tunnelmanager.application.tunnel;

import com.ssh.tunnelmanager.application.ssh.ConnectionTestResult;
import com.ssh.tunnelmanager.application.ssh.SshTunnelEngine;
import java.util.UUID;

public final class TestTunnelConnectionUseCase {
  private final SshTunnelEngine engine;
  private final TunnelRequestProvider requests;

  public TestTunnelConnectionUseCase(SshTunnelEngine engine, TunnelRequestProvider requests) {
    this.engine = engine;
    this.requests = requests;
  }

  public ConnectionTestResult execute(UUID tunnelId) {
    return engine.testConnection(requests.requestFor(tunnelId));
  }
}

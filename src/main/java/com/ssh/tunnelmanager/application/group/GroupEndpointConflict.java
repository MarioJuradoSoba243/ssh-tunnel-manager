package com.ssh.tunnelmanager.application.group;

import java.util.UUID;

public record GroupEndpointConflict(
    UUID firstTunnelId,
    String firstTunnelName,
    UUID secondTunnelId,
    String secondTunnelName,
    String bindAddress,
    int localPort) {
  public String endpoint() {
    return bindAddress + ":" + localPort;
  }
}

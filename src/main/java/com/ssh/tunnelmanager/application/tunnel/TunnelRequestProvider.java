package com.ssh.tunnelmanager.application.tunnel;

import com.ssh.tunnelmanager.application.ssh.TunnelConnectionRequest;
import java.util.UUID;

@FunctionalInterface
public interface TunnelRequestProvider {
  TunnelConnectionRequest requestFor(UUID tunnelId);
}

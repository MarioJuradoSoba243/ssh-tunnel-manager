package com.ssh.tunnelmanager.application.tunnel;

import com.ssh.tunnelmanager.application.persistence.SshHostRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import java.util.UUID;

public final class DeleteSshHostUseCase {
  private final SshHostRepository hosts;
  private final TunnelRepository tunnels;

  public DeleteSshHostUseCase(SshHostRepository hosts, TunnelRepository tunnels) {
    this.hosts = hosts;
    this.tunnels = tunnels;
  }

  public void execute(UUID hostId) {
    boolean inUse =
        tunnels.findAll().stream()
            .anyMatch(
                tunnel ->
                    hostId.equals(tunnel.finalHostId())
                        || hostId.equals(tunnel.jump1HostId())
                        || hostId.equals(tunnel.jump2HostId()));
    if (inUse) throw new HostInUseException(hostId);
    hosts.delete(hostId);
  }

  public static final class HostInUseException extends IllegalStateException {
    public HostInUseException(UUID hostId) {
      super("El host SSH está referenciado por uno o más túneles: " + hostId);
    }
  }
}

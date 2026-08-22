package com.ssh.tunnelmanager.application.tunnel;

import com.ssh.tunnelmanager.application.persistence.SshHostRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import java.util.List;
import java.util.UUID;

public final class DeleteSshHostUseCase {
  private final SshHostRepository hosts;
  private final TunnelRepository tunnels;

  public DeleteSshHostUseCase(SshHostRepository hosts, TunnelRepository tunnels) {
    this.hosts = hosts;
    this.tunnels = tunnels;
  }

  public void execute(UUID hostId) {
    List<TunnelRecord> dependencies =
        tunnels.findAll().stream()
            .filter(
                tunnel ->
                    hostId.equals(tunnel.finalHostId())
                        || hostId.equals(tunnel.jump1HostId())
                        || hostId.equals(tunnel.jump2HostId()))
            .toList();
    if (!dependencies.isEmpty()) throw new HostInUseException(hostId, dependencies);
    hosts.delete(hostId);
  }

  public static final class HostInUseException extends IllegalStateException {
    private final List<TunnelRecord> dependencies;

    public HostInUseException(UUID hostId, List<TunnelRecord> dependencies) {
      super(
          "El host SSH está referenciado por los túneles: "
              + dependencies.stream().map(TunnelRecord::name).sorted().toList());
      this.dependencies = List.copyOf(dependencies);
    }

    public List<TunnelRecord> dependencies() {
      return dependencies;
    }
  }
}

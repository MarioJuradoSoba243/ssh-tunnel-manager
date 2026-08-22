package com.ssh.tunnelmanager.application.tunnel;

import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import java.util.UUID;

public final class DuplicateTunnelUseCase {
  private final TunnelRepository tunnels;

  public DuplicateTunnelUseCase(TunnelRepository tunnels) {
    this.tunnels = tunnels;
  }

  public TunnelRecord execute(UUID tunnelId, String duplicateName) {
    TunnelRecord source =
        tunnels
            .findById(tunnelId)
            .orElseThrow(() -> new IllegalArgumentException("Túnel no encontrado: " + tunnelId));
    if (duplicateName == null || duplicateName.isBlank())
      throw new IllegalArgumentException("El nombre del duplicado es obligatorio");
    TunnelRecord duplicate =
        new TunnelRecord(
            UUID.randomUUID(),
            duplicateName,
            source.bindAddress(),
            source.localPort(),
            source.destinationHost(),
            source.destinationPort(),
            source.finalHostId(),
            source.jump1HostId(),
            source.jump2HostId(),
            source.keepAliveOverrideSeconds(),
            source.connectionTimeoutOverrideSeconds(),
            source.retryCountOverride(),
            source.projectId(),
            source.environmentId(),
            false,
            source.tagIds(),
            source.sshOptions());
    tunnels.save(duplicate);
    return duplicate;
  }
}

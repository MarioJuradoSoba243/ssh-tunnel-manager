package com.ssh.tunnelmanager.application.group;

import com.ssh.tunnelmanager.application.persistence.TunnelGroupRepository;
import com.ssh.tunnelmanager.application.persistence.model.TunnelGroupRecord;
import java.util.UUID;

public final class DuplicateTunnelGroupUseCase {
  private final TunnelGroupRepository groups;

  public DuplicateTunnelGroupUseCase(TunnelGroupRepository groups) {
    this.groups = groups;
  }

  public TunnelGroupRecord execute(UUID groupId, String duplicateName) {
    TunnelGroupRecord source =
        groups
            .findById(groupId)
            .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado: " + groupId));
    if (duplicateName == null || duplicateName.isBlank())
      throw new IllegalArgumentException("El nombre del duplicado es obligatorio");
    TunnelGroupRecord duplicate =
        new TunnelGroupRecord(
            UUID.randomUUID(),
            duplicateName,
            source.projectId(),
            source.environmentId(),
            source.favorite(),
            source.tunnelIds(),
            source.tagIds());
    groups.save(duplicate);
    return duplicate;
  }
}

package com.ssh.tunnelmanager.application.group;

import com.ssh.tunnelmanager.application.persistence.TunnelGroupRepository;
import java.util.UUID;

public final class DeleteTunnelGroupUseCase {
  private final TunnelGroupRepository groups;

  public DeleteTunnelGroupUseCase(TunnelGroupRepository groups) {
    this.groups = groups;
  }

  public void execute(UUID groupId) {
    groups.delete(groupId);
  }
}

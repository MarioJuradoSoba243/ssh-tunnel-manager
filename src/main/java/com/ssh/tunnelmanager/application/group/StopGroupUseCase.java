package com.ssh.tunnelmanager.application.group;

import com.ssh.tunnelmanager.application.persistence.TunnelGroupRepository;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StopGroupUseCase {
  private static final Logger LOGGER = LoggerFactory.getLogger(StopGroupUseCase.class);
  private final TunnelGroupRepository groups;
  private final TunnelRuntimeManager runtime;

  public StopGroupUseCase(TunnelGroupRepository groups, TunnelRuntimeManager runtime) {
    this.groups = groups;
    this.runtime = runtime;
  }

  public UUID execute(UUID groupId) {
    var group =
        groups
            .findById(groupId)
            .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado: " + groupId));
    UUID operationId = UUID.randomUUID();
    LOGGER.info(
        "Parada de grupo solicitada operationId={} groupId={} requested={}",
        operationId,
        groupId,
        group.tunnelIds().size());
    group.tunnelIds().forEach(runtime::stop);
    return operationId;
  }
}

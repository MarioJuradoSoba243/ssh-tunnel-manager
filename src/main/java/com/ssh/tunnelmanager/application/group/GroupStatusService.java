package com.ssh.tunnelmanager.application.group;

import com.ssh.tunnelmanager.application.persistence.TunnelGroupRepository;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager;
import java.util.UUID;

public final class GroupStatusService {
  private final TunnelGroupRepository groups;
  private final TunnelRuntimeManager runtime;

  public GroupStatusService(TunnelGroupRepository groups, TunnelRuntimeManager runtime) {
    this.groups = groups;
    this.runtime = runtime;
  }

  public GroupRuntimeStatus status(UUID groupId) {
    var group =
        groups
            .findById(groupId)
            .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado: " + groupId));
    int open = 0;
    int connecting = 0;
    int queued = 0;
    int reconnecting = 0;
    int stopping = 0;
    int errors = 0;
    int closed = 0;
    for (UUID tunnelId : group.tunnelIds()) {
      switch (runtime.runtime(tunnelId).actualState()) {
        case OPEN -> open++;
        case CONNECTING -> connecting++;
        case QUEUED -> queued++;
        case RECONNECTING -> reconnecting++;
        case STOPPING -> stopping++;
        case ERROR -> errors++;
        case CLOSED -> closed++;
      }
    }
    GroupState state =
        deriveState(
            group.tunnelIds().size(), open, connecting, queued, reconnecting, stopping, errors);
    return new GroupRuntimeStatus(
        groupId,
        group.tunnelIds().size(),
        open,
        connecting,
        queued,
        reconnecting,
        stopping,
        errors,
        closed,
        state);
  }

  private static GroupState deriveState(
      int members,
      int open,
      int connecting,
      int queued,
      int reconnecting,
      int stopping,
      int errors) {
    if (members == 0
        || (open == 0
            && connecting == 0
            && queued == 0
            && reconnecting == 0
            && stopping == 0
            && errors == 0)) return GroupState.CLOSED;
    if (open == members) return GroupState.OPEN;
    if (errors == members) return GroupState.ERROR;
    if (open > 0 || errors > 0) return GroupState.PARTIAL;
    return GroupState.STARTING;
  }
}

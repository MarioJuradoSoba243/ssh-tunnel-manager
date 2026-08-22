package com.ssh.tunnelmanager.application.group;

import java.util.UUID;

public record GroupRuntimeStatus(
    UUID groupId,
    int members,
    int open,
    int connecting,
    int queued,
    int reconnecting,
    int stopping,
    int errors,
    int closed,
    GroupState state) {
  public int active() {
    return open + connecting + queued + reconnecting + stopping;
  }
}

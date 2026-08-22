package com.ssh.tunnelmanager.application.runtime;

import java.time.Instant;
import java.util.UUID;

public sealed interface GroupEvent permits GroupEvent.GroupStartCompleted {
  UUID operationId();

  UUID groupId();

  Instant at();

  record GroupStartCompleted(
      UUID operationId,
      UUID groupId,
      int requested,
      int open,
      int errors,
      int cancelled,
      Instant at)
      implements GroupEvent {}
}

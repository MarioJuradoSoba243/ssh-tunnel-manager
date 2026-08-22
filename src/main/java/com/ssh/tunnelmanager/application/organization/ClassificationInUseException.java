package com.ssh.tunnelmanager.application.organization;

import java.util.UUID;

public final class ClassificationInUseException extends RuntimeException {
  private final UUID id;
  private final ClassificationUsage usage;

  public ClassificationInUseException(UUID id, ClassificationUsage usage) {
    super("La clasificación está siendo utilizada por " + usage.total() + " elementos");
    this.id = id;
    this.usage = usage;
  }

  public UUID id() {
    return id;
  }

  public ClassificationUsage usage() {
    return usage;
  }
}

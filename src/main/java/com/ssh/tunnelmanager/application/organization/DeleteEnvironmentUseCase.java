package com.ssh.tunnelmanager.application.organization;

import com.ssh.tunnelmanager.application.persistence.EnvironmentRepository;
import java.util.UUID;

public final class DeleteEnvironmentUseCase {
  private final EnvironmentRepository environments;
  private final ClassificationUsageService usage;

  public DeleteEnvironmentUseCase(
      EnvironmentRepository environments, ClassificationUsageService usage) {
    this.environments = environments;
    this.usage = usage;
  }

  public void execute(UUID id, boolean confirmed) {
    ClassificationUsage current = usage.environment(id);
    if (current.total() > 0 && !confirmed) throw new ClassificationInUseException(id, current);
    environments.delete(id);
  }
}

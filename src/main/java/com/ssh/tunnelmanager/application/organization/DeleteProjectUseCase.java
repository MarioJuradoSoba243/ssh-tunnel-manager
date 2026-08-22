package com.ssh.tunnelmanager.application.organization;

import com.ssh.tunnelmanager.application.persistence.ProjectRepository;
import java.util.UUID;

public final class DeleteProjectUseCase {
  private final ProjectRepository projects;
  private final ClassificationUsageService usage;

  public DeleteProjectUseCase(ProjectRepository projects, ClassificationUsageService usage) {
    this.projects = projects;
    this.usage = usage;
  }

  public void execute(UUID id, boolean confirmed) {
    ClassificationUsage current = usage.project(id);
    if (current.total() > 0 && !confirmed) throw new ClassificationInUseException(id, current);
    projects.delete(id);
  }
}

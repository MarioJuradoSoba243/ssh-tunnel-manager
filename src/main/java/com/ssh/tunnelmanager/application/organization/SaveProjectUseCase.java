package com.ssh.tunnelmanager.application.organization;

import com.ssh.tunnelmanager.application.persistence.ProjectRepository;
import com.ssh.tunnelmanager.application.persistence.model.ProjectRecord;
import com.ssh.tunnelmanager.domain.organization.Project;

public final class SaveProjectUseCase {
  private final ProjectRepository projects;

  public SaveProjectUseCase(ProjectRepository projects) {
    this.projects = projects;
  }

  public void execute(Project project) {
    if (projects.findAll().stream()
        .anyMatch(
            existing ->
                existing.name().equalsIgnoreCase(project.name())
                    && !existing.id().equals(project.id()))) {
      throw new IllegalArgumentException("El nombre del proyecto ya está utilizado");
    }
    projects.save(new ProjectRecord(project.id(), project.name()));
  }
}

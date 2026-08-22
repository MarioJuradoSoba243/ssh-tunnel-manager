package com.ssh.tunnelmanager.application.organization;

import com.ssh.tunnelmanager.application.persistence.EnvironmentRepository;
import com.ssh.tunnelmanager.application.persistence.model.EnvironmentRecord;
import com.ssh.tunnelmanager.domain.organization.Environment;

public final class SaveEnvironmentUseCase {
  private final EnvironmentRepository environments;

  public SaveEnvironmentUseCase(EnvironmentRepository environments) {
    this.environments = environments;
  }

  public void execute(Environment environment) {
    if (environments.findAll().stream()
        .anyMatch(
            existing ->
                existing.name().equalsIgnoreCase(environment.name())
                    && !existing.id().equals(environment.id()))) {
      throw new IllegalArgumentException("El nombre del entorno ya está utilizado");
    }
    environments.save(new EnvironmentRecord(environment.id(), environment.name()));
  }
}

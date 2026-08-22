package com.ssh.tunnelmanager.application.persistence;

import com.ssh.tunnelmanager.application.persistence.model.ProjectRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {
  Optional<ProjectRecord> findById(UUID id);

  List<ProjectRecord> findAll();

  void save(ProjectRecord project);

  void delete(UUID id);
}

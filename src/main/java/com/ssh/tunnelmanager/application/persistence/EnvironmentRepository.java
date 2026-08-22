package com.ssh.tunnelmanager.application.persistence;

import com.ssh.tunnelmanager.application.persistence.model.EnvironmentRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnvironmentRepository {
  Optional<EnvironmentRecord> findById(UUID id);

  List<EnvironmentRecord> findAll();

  void save(EnvironmentRecord environment);

  void delete(UUID id);
}

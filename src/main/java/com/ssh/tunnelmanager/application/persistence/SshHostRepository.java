package com.ssh.tunnelmanager.application.persistence;

import com.ssh.tunnelmanager.application.persistence.model.SshHostRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SshHostRepository {
  Optional<SshHostRecord> findById(UUID id);

  List<SshHostRecord> findAll();

  void save(SshHostRecord host);

  void delete(UUID id);
}

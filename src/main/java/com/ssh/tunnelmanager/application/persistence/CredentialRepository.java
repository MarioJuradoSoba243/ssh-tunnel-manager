package com.ssh.tunnelmanager.application.persistence;

import com.ssh.tunnelmanager.application.persistence.model.CredentialRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository {
  Optional<CredentialRecord> findById(UUID id);

  List<CredentialRecord> findAll();

  void save(CredentialRecord credential);

  void delete(UUID id);
}

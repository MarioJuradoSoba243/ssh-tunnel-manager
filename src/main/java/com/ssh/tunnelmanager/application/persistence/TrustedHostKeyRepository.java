package com.ssh.tunnelmanager.application.persistence;

import com.ssh.tunnelmanager.application.persistence.model.TrustedHostKeyRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrustedHostKeyRepository {
  Optional<TrustedHostKeyRecord> findByHost(String hostname, int sshPort);

  List<TrustedHostKeyRecord> findAll();

  void save(TrustedHostKeyRecord key);

  void delete(UUID id);

  void deleteByHost(String hostname, int sshPort);
}

package com.ssh.tunnelmanager.application.persistence;

import com.ssh.tunnelmanager.application.persistence.model.TunnelGroupRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TunnelGroupRepository {
  Optional<TunnelGroupRecord> findById(UUID id);

  List<TunnelGroupRecord> findAll();

  void save(TunnelGroupRecord group);

  void delete(UUID id);
}

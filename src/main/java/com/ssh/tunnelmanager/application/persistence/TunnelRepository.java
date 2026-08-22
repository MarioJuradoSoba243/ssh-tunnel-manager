package com.ssh.tunnelmanager.application.persistence;

import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TunnelRepository {
  Optional<TunnelRecord> findById(UUID id);

  List<TunnelRecord> findAll();

  void save(TunnelRecord tunnel);

  void delete(UUID id);
}

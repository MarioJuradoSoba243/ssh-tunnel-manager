package com.ssh.tunnelmanager.application.persistence;

import com.ssh.tunnelmanager.application.persistence.model.TagRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository {
  Optional<TagRecord> findById(UUID id);

  List<TagRecord> findAll();

  void save(TagRecord tag);

  void delete(UUID id);
}

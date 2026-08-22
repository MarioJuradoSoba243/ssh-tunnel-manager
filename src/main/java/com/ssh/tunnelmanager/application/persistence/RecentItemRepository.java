package com.ssh.tunnelmanager.application.persistence;

import com.ssh.tunnelmanager.application.persistence.model.RecentItemRecord;
import java.util.List;
import java.util.UUID;

public interface RecentItemRepository {
  void record(RecentItemRecord item);

  List<RecentItemRecord> findRecent(int limit);

  void delete(UUID itemId);

  void deleteAll();
}

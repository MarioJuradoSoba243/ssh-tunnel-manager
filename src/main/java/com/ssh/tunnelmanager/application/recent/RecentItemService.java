package com.ssh.tunnelmanager.application.recent;

import com.ssh.tunnelmanager.application.persistence.RecentItemRepository;
import com.ssh.tunnelmanager.application.persistence.model.RecentItemRecord;
import com.ssh.tunnelmanager.application.persistence.model.RecentItemType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RecentItemService {
  private final RecentItemRepository recents;
  private final Clock clock;

  public RecentItemService(RecentItemRepository recents) {
    this(recents, Clock.systemUTC());
  }

  public RecentItemService(RecentItemRepository recents, Clock clock) {
    this.recents = recents;
    this.clock = clock;
  }

  public void recordTunnelStart(UUID tunnelId) {
    recents.record(new RecentItemRecord(RecentItemType.TUNNEL, tunnelId, Instant.now(clock)));
  }

  public void recordGroupStart(UUID groupId) {
    recents.record(new RecentItemRecord(RecentItemType.GROUP, groupId, Instant.now(clock)));
  }

  public List<RecentItemRecord> findRecent() {
    return recents.findRecent(8);
  }
}

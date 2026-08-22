package com.ssh.tunnelmanager.application.recent;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssh.tunnelmanager.application.persistence.RecentItemRepository;
import com.ssh.tunnelmanager.application.persistence.model.RecentItemRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecentItemServiceTest {
  @Test
  void recordsOnlyExplicitTunnelAndGroupStartsAndKeepsUtcOrdering() {
    Store store = new Store();
    RecentItemService service =
        new RecentItemService(
            store, Clock.fixed(Instant.parse("2026-08-22T20:00:00Z"), ZoneOffset.UTC));
    UUID tunnelId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();

    service.recordTunnelStart(tunnelId);
    service.recordGroupStart(groupId);

    assertThat(service.findRecent())
        .extracting(RecentItemRecord::itemId)
        .containsExactlyInAnyOrder(groupId, tunnelId);
    assertThat(service.findRecent())
        .allSatisfy(
            item ->
                assertThat(item.lastStartedAt()).isEqualTo(Instant.parse("2026-08-22T20:00:00Z")));
  }

  private static final class Store implements RecentItemRepository {
    private final List<RecentItemRecord> values = new ArrayList<>();

    public void record(RecentItemRecord item) {
      values.removeIf(
          existing ->
              existing.itemType() == item.itemType() && existing.itemId().equals(item.itemId()));
      values.add(item);
      values.sort(java.util.Comparator.comparing(RecentItemRecord::lastStartedAt).reversed());
    }

    public List<RecentItemRecord> findRecent(int limit) {
      return values.stream().limit(limit).toList();
    }

    public void delete(UUID itemId) {}

    public void deleteAll() {}
  }
}

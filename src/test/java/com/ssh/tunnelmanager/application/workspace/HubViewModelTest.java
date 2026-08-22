package com.ssh.tunnelmanager.application.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssh.tunnelmanager.application.persistence.RecentItemRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelGroupRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.persistence.model.RecentItemRecord;
import com.ssh.tunnelmanager.application.persistence.model.RecentItemType;
import com.ssh.tunnelmanager.application.persistence.model.TunnelGroupRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import com.ssh.tunnelmanager.application.runtime.ApplicationEventPublisher;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager;
import com.ssh.tunnelmanager.application.ssh.ConnectionTestResult;
import com.ssh.tunnelmanager.application.ssh.SshTunnelEngine;
import com.ssh.tunnelmanager.application.ssh.TunnelConnectionRequest;
import com.ssh.tunnelmanager.application.ssh.TunnelHandle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HubViewModelTest {
  @Test
  void exposesFavoritesAndRecentItemsWithoutPersistingRuntimeState() {
    UUID tunnelId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    TunnelStore tunnels = new TunnelStore();
    GroupStore groups = new GroupStore();
    RecentStore recents = new RecentStore();
    tunnels.save(tunnel(tunnelId));
    groups.save(
        new TunnelGroupRecord(groupId, "ops", null, null, true, Set.of(tunnelId), Set.of()));
    recents.record(new RecentItemRecord(RecentItemType.TUNNEL, tunnelId, Instant.now()));
    recents.record(
        new RecentItemRecord(RecentItemType.GROUP, groupId, Instant.now().plusSeconds(1)));
    TunnelRuntimeManager runtime =
        new TunnelRuntimeManager(
            new NoOpEngine(), ignored -> null, new ApplicationEventPublisher(), 1);
    try {
      HubViewModel hub = new HubViewModel(tunnels, groups, recents, runtime);
      hub.refresh();

      assertThat(hub.snapshot().favorites())
          .extracting(HubViewModel.HubItem::name)
          .containsExactlyInAnyOrder("service", "ops");
      assertThat(hub.snapshot().recent())
          .extracting(HubViewModel.HubItem::name)
          .containsExactly("ops", "service");
      assertThat(hub.snapshot().openTunnels()).isZero();
    } finally {
      runtime.close();
    }
  }

  private static TunnelRecord tunnel(UUID id) {
    return new TunnelRecord(
        id,
        "service",
        "127.0.0.1",
        12000,
        "target",
        80,
        UUID.randomUUID(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        true,
        Set.of(),
        Map.of());
  }

  private static final class RecentStore implements RecentItemRepository {
    private final List<RecentItemRecord> values = new ArrayList<>();

    public void record(RecentItemRecord item) {
      values.removeIf(
          existing ->
              existing.itemType() == item.itemType() && existing.itemId().equals(item.itemId()));
      values.add(item);
      values.sort(java.util.Comparator.comparing(RecentItemRecord::lastStartedAt).reversed());
      if (values.size() > 8) values.removeLast();
    }

    public List<RecentItemRecord> findRecent(int limit) {
      return values.stream().limit(limit).toList();
    }

    public void delete(UUID itemId) {
      values.removeIf(item -> item.itemId().equals(itemId));
    }

    public void deleteAll() {
      values.clear();
    }
  }

  private static final class TunnelStore implements TunnelRepository {
    private final List<TunnelRecord> values = new ArrayList<>();

    public Optional<TunnelRecord> findById(UUID id) {
      return values.stream().filter(value -> value.id().equals(id)).findFirst();
    }

    public List<TunnelRecord> findAll() {
      return List.copyOf(values);
    }

    public void save(TunnelRecord value) {
      values.add(value);
    }

    public void delete(UUID id) {}
  }

  private static final class GroupStore implements TunnelGroupRepository {
    private final List<TunnelGroupRecord> values = new ArrayList<>();

    public Optional<TunnelGroupRecord> findById(UUID id) {
      return values.stream().filter(value -> value.id().equals(id)).findFirst();
    }

    public List<TunnelGroupRecord> findAll() {
      return List.copyOf(values);
    }

    public void save(TunnelGroupRecord value) {
      values.add(value);
    }

    public void delete(UUID id) {}
  }

  private static final class NoOpEngine implements SshTunnelEngine {
    public TunnelHandle openTunnel(TunnelConnectionRequest request) {
      throw new UnsupportedOperationException();
    }

    public ConnectionTestResult testConnection(TunnelConnectionRequest request) {
      return ConnectionTestResult.success();
    }

    public void closeTunnel(TunnelHandle handle) {}
  }
}

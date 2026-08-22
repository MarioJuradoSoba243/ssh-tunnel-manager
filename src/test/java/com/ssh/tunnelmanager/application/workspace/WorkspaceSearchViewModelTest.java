package com.ssh.tunnelmanager.application.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssh.tunnelmanager.application.persistence.SshHostRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelGroupRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.persistence.model.SshHostRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelGroupRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceSearchViewModelTest {
  @Test
  void searchesAnInMemoryIndexWithMoreThanOneHundredTunnels() {
    TunnelStore tunnels = new TunnelStore();
    GroupStore groups = new GroupStore();
    HostStore hosts = new HostStore();
    UUID hostId = UUID.randomUUID();
    hosts.save(new SshHostRecord(hostId, "production", "prod.example", 22, UUID.randomUUID()));
    for (int index = 0; index < 120; index++) {
      tunnels.save(tunnel(hostId, index));
    }
    UUID groupId = UUID.randomUUID();
    groups.save(new TunnelGroupRecord(groupId, "backend", null, null, false, Set.of(), Set.of()));
    WorkspaceSearchViewModel search = new WorkspaceSearchViewModel(tunnels, groups, hosts);
    search.refresh();

    assertThat(search.search("service-119")).hasSize(1);
    assertThat(search.search("PRODUCTION"))
        .extracting(SearchResult::kind)
        .containsExactly(SearchResult.Kind.HOST);
    assertThat(search.search("backend"))
        .extracting(SearchResult::kind)
        .containsExactly(SearchResult.Kind.GROUP);
    assertThat(tunnels.findAll()).hasSize(120);
  }

  @Test
  void repeatedSearchesUseTheCachedIndexUntilAnExplicitRefresh() {
    TunnelStore tunnels = new TunnelStore();
    GroupStore groups = new GroupStore();
    HostStore hosts = new HostStore();
    UUID hostId = UUID.randomUUID();
    tunnels.save(tunnel(hostId, 1));
    WorkspaceSearchViewModel search = new WorkspaceSearchViewModel(tunnels, groups, hosts);
    search.refresh();
    tunnels.save(tunnel(hostId, 2));

    assertThat(search.search("service-2")).isEmpty();
    search.refresh();
    assertThat(search.search("service-2")).hasSize(1);
  }

  @Test
  void filtersLargeTunnelCollectionsByClassificationTagsGroupAndText() {
    TunnelStore tunnels = new TunnelStore();
    UUID projectId = UUID.randomUUID();
    UUID environmentId = UUID.randomUUID();
    UUID tagId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    List<TunnelRecord> values = new ArrayList<>();
    for (int index = 0; index < 120; index++) {
      values.add(
          new TunnelRecord(
              UUID.randomUUID(),
              "service-" + index,
              "127.0.0.1",
              10000 + index,
              "target-" + index,
              80,
              UUID.randomUUID(),
              null,
              null,
              null,
              null,
              null,
              index == 119 ? projectId : null,
              index == 119 ? environmentId : null,
              false,
              index == 119 ? Set.of(tagId) : Set.of(),
              Map.of()));
    }
    values.forEach(tunnels::save);
    TunnelFilter filter =
        new TunnelFilter("service-119", projectId, environmentId, groupId, Set.of(tagId), null);

    assertThat(
            values.stream()
                .filter(
                    tunnel ->
                        filter.matches(
                            tunnel,
                            null,
                            id -> id.equals(groupId) ? Set.of(tunnel.id()) : Set.of()))
                .toList())
        .hasSize(1)
        .first()
        .extracting(TunnelRecord::name)
        .isEqualTo("service-119");
  }

  private static TunnelRecord tunnel(UUID hostId, int index) {
    return new TunnelRecord(
        UUID.randomUUID(),
        "service-" + index,
        "127.0.0.1",
        10000 + index,
        "prod.example",
        80,
        hostId,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        Set.of(),
        Map.of());
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
      values.removeIf(existing -> existing.id().equals(value.id()));
      values.add(value);
    }

    public void delete(UUID id) {
      values.removeIf(value -> value.id().equals(id));
    }
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
      values.removeIf(existing -> existing.id().equals(value.id()));
      values.add(value);
    }

    public void delete(UUID id) {
      values.removeIf(value -> value.id().equals(id));
    }
  }

  private static final class HostStore implements SshHostRepository {
    private final List<SshHostRecord> values = new ArrayList<>();

    public Optional<SshHostRecord> findById(UUID id) {
      return values.stream().filter(value -> value.id().equals(id)).findFirst();
    }

    public List<SshHostRecord> findAll() {
      return List.copyOf(values);
    }

    public void save(SshHostRecord value) {
      values.removeIf(existing -> existing.id().equals(value.id()));
      values.add(value);
    }

    public void delete(UUID id) {
      values.removeIf(value -> value.id().equals(id));
    }
  }
}

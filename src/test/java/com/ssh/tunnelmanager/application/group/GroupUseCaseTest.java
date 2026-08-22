package com.ssh.tunnelmanager.application.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssh.tunnelmanager.application.persistence.TunnelGroupRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.persistence.model.TunnelGroupRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import com.ssh.tunnelmanager.application.runtime.ApplicationEventPublisher;
import com.ssh.tunnelmanager.application.runtime.GroupEvent;
import com.ssh.tunnelmanager.application.ssh.ConnectionTestResult;
import com.ssh.tunnelmanager.application.ssh.SshTunnelEngine;
import com.ssh.tunnelmanager.application.ssh.TunnelConnectionRequest;
import com.ssh.tunnelmanager.application.ssh.TunnelHandle;
import com.ssh.tunnelmanager.application.tunnel.TunnelRequestProvider;
import com.ssh.tunnelmanager.domain.group.TunnelGroup;
import com.ssh.tunnelmanager.domain.tunnel.ActualState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GroupUseCaseTest {
  private TunnelRuntimeManagerHolder runtimeHolder;

  @AfterEach
  void closeRuntime() {
    if (runtimeHolder != null) runtimeHolder.close();
  }

  @Test
  void savesGroupOnlyAfterExplicitConfirmationWhenLocalEndpointsConflict() {
    TunnelStore tunnels = new TunnelStore();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    tunnels.add(tunnel(first, "A", 15432));
    tunnels.add(tunnel(second, "B", 15432));
    GroupStore groups = new GroupStore();
    SaveTunnelGroupUseCase save = new SaveTunnelGroupUseCase(groups, tunnels);
    TunnelGroup group = new TunnelGroup(UUID.randomUUID(), "Backend", Set.of(first, second));

    assertThatThrownBy(() -> save.execute(group))
        .isInstanceOf(GroupConflictException.class)
        .satisfies(
            error ->
                assertThat(((GroupConflictException) error).conflicts().getFirst().endpoint())
                    .isEqualTo("127.0.0.1:15432"));
    save.execute(group, true);
    assertThat(groups.findById(group.id())).isPresent();
  }

  @Test
  void startsAllMembersWithoutDuplicatingAnAlreadyOpenTunnelAndPublishesSummary() throws Exception {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    TunnelStore tunnels = new TunnelStore();
    tunnels.add(tunnel(first, "A", 15432));
    tunnels.add(tunnel(second, "B", 15433));
    GroupStore groups = new GroupStore();
    UUID groupId = UUID.randomUUID();
    groups.save(
        new TunnelGroupRecord(
            groupId, "Backend", null, null, false, Set.of(first, second), Set.of()));
    ApplicationEventPublisher events = new ApplicationEventPublisher();
    FakeEngine engine = new FakeEngine();
    runtimeHolder = new TunnelRuntimeManagerHolder(tunnels, events, engine, 2, 0);
    StartGroupUseCase start = new StartGroupUseCase(groups, runtimeHolder.runtime, events);
    List<GroupEvent.GroupStartCompleted> completed = new ArrayList<>();
    events.subscribeGroup(event -> completed.add((GroupEvent.GroupStartCompleted) event));

    GroupStartOperation firstOperation = start.execute(groupId);
    GroupStartSummary firstSummary = firstOperation.completion().get(3, TimeUnit.SECONDS);
    assertThat(firstSummary).extracting(GroupStartSummary::open).isEqualTo(2);
    assertThat(engine.opens()).isEqualTo(2);

    GroupStartSummary secondSummary = start.execute(groupId).completion().get(3, TimeUnit.SECONDS);
    assertThat(secondSummary).extracting(GroupStartSummary::open).isEqualTo(2);
    assertThat(engine.opens()).isEqualTo(2);
    assertThat(completed).hasSize(2).allMatch(event -> event.operationId() != null);
  }

  @Test
  void isolatesOneMemberFailureAndStopsSharedTunnelWithoutOwnership() throws Exception {
    UUID good = UUID.randomUUID();
    UUID bad = UUID.randomUUID();
    TunnelStore tunnels = new TunnelStore();
    tunnels.add(tunnel(good, "Good", 15432));
    tunnels.add(tunnel(bad, "Bad", 15433));
    GroupStore groups = new GroupStore();
    UUID firstGroup = UUID.randomUUID();
    UUID secondGroup = UUID.randomUUID();
    groups.save(
        new TunnelGroupRecord(firstGroup, "First", null, null, false, Set.of(good, bad), Set.of()));
    groups.save(
        new TunnelGroupRecord(secondGroup, "Second", null, null, false, Set.of(good), Set.of()));
    ApplicationEventPublisher events = new ApplicationEventPublisher();
    FakeEngine engine = new FakeEngine();
    runtimeHolder = new TunnelRuntimeManagerHolder(tunnels, events, engine, 2, 0, bad);
    StartGroupUseCase start = new StartGroupUseCase(groups, runtimeHolder.runtime, events);
    GroupStartSummary summary = start.execute(firstGroup).completion().get(3, TimeUnit.SECONDS);

    assertThat(summary.open()).isEqualTo(1);
    assertThat(summary.errors()).isEqualTo(1);
    assertThat(runtimeHolder.runtime.runtime(good).actualState()).isEqualTo(ActualState.OPEN);
    assertThat(runtimeHolder.runtime.runtime(bad).actualState()).isEqualTo(ActualState.ERROR);

    StopGroupUseCase stop = new StopGroupUseCase(groups, runtimeHolder.runtime);
    stop.execute(secondGroup);
    assertThat(runtimeHolder.runtime.runtime(good).actualState()).isEqualTo(ActualState.CLOSED);
  }

  @Test
  void duplicateKeepsMembersButUsesNewIdAndNamesRemainUnique() {
    GroupStore groups = new GroupStore();
    UUID tunnelId = UUID.randomUUID();
    TunnelGroupRecord original =
        new TunnelGroupRecord(
            UUID.randomUUID(), "Backend", null, null, true, Set.of(tunnelId), Set.of());
    groups.save(original);
    TunnelGroupRecord duplicate =
        new DuplicateTunnelGroupUseCase(groups).execute(original.id(), "Backend copy");

    assertThat(duplicate.id()).isNotEqualTo(original.id());
    assertThat(duplicate.tunnelIds()).containsExactly(tunnelId);
    assertThat(groups.findAll())
        .extracting(TunnelGroupRecord::name)
        .containsExactlyInAnyOrder("Backend", "Backend copy");
  }

  @Test
  void startsMembersThroughTheGlobalFifoConcurrencyLimit() throws Exception {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    TunnelStore tunnels = new TunnelStore();
    tunnels.add(tunnel(first, "A", 15432));
    tunnels.add(tunnel(second, "B", 15433));
    GroupStore groups = new GroupStore();
    UUID groupId = UUID.randomUUID();
    groups.save(
        new TunnelGroupRecord(
            groupId, "Backend", null, null, false, Set.of(first, second), Set.of()));
    BlockingEngine engine = new BlockingEngine();
    ApplicationEventPublisher events = new ApplicationEventPublisher();
    runtimeHolder = new TunnelRuntimeManagerHolder(tunnels, events, engine, 1, 0);
    GroupStartOperation operation =
        new StartGroupUseCase(groups, runtimeHolder.runtime, events).execute(groupId);

    assertThat(engine.entered.await(3, TimeUnit.SECONDS)).isTrue();
    assertThat(runtimeHolder.runtime.runtime(first).actualState())
        .isIn(ActualState.CONNECTING, ActualState.QUEUED);
    assertThat(runtimeHolder.runtime.runtime(second).actualState())
        .isIn(ActualState.CONNECTING, ActualState.QUEUED);
    assertThat(engine.maxInFlight.get()).isEqualTo(1);
    engine.release.countDown();
    assertThat(operation.completion().get(3, TimeUnit.SECONDS).open()).isEqualTo(2);
    assertThat(engine.maxInFlight.get()).isEqualTo(1);
  }

  @Test
  void stoppingAGroupDuringConnectionCompletesTheInitialOperationAsCancelled() throws Exception {
    UUID tunnelId = UUID.randomUUID();
    TunnelStore tunnels = new TunnelStore();
    tunnels.add(tunnel(tunnelId, "A", 15432));
    GroupStore groups = new GroupStore();
    UUID groupId = UUID.randomUUID();
    groups.save(
        new TunnelGroupRecord(groupId, "Backend", null, null, false, Set.of(tunnelId), Set.of()));
    BlockingEngine engine = new BlockingEngine();
    ApplicationEventPublisher events = new ApplicationEventPublisher();
    runtimeHolder = new TunnelRuntimeManagerHolder(tunnels, events, engine, 1, 0);
    GroupStartOperation operation =
        new StartGroupUseCase(groups, runtimeHolder.runtime, events).execute(groupId);
    assertThat(engine.entered.await(3, TimeUnit.SECONDS)).isTrue();

    new StopGroupUseCase(groups, runtimeHolder.runtime).execute(groupId);

    assertThat(operation.completion().get(3, TimeUnit.SECONDS).cancelled()).isEqualTo(1);
    assertThat(runtimeHolder.runtime.runtime(tunnelId).desiredState())
        .isEqualTo(com.ssh.tunnelmanager.domain.tunnel.DesiredState.STOPPED);
  }

  private static TunnelRecord tunnel(UUID id, String name, int localPort) {
    return new TunnelRecord(
        id,
        name,
        "127.0.0.1",
        localPort,
        "destination",
        80,
        UUID.randomUUID(),
        null,
        null,
        null,
        null,
        0,
        null,
        null,
        false,
        Set.of(),
        Map.of());
  }

  private static final class TunnelRuntimeManagerHolder implements AutoCloseable {
    private final com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager runtime;

    private TunnelRuntimeManagerHolder(
        TunnelStore tunnels,
        ApplicationEventPublisher events,
        SshTunnelEngine engine,
        int slots,
        int retries) {
      this(tunnels, events, engine, slots, retries, null);
    }

    private TunnelRuntimeManagerHolder(
        TunnelStore tunnels,
        ApplicationEventPublisher events,
        SshTunnelEngine engine,
        int slots,
        int retries,
        UUID failingTunnel) {
      TunnelRequestProvider requests =
          tunnelId ->
              request(failingTunnel != null && tunnelId.equals(failingTunnel) ? "bad" : "good");
      runtime =
          new com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager(
              engine, requests, events, slots, retries, ignored -> {});
    }

    @Override
    public void close() {
      runtime.close();
    }
  }

  private static TunnelConnectionRequest request(String destinationHost) {
    return new TunnelConnectionRequest(
        new com.ssh.tunnelmanager.domain.tunnel.LocalForward("127.0.0.1", 12345),
        new com.ssh.tunnelmanager.domain.tunnel.RemoteDestination(destinationHost, 80),
        List.of(
            new TunnelConnectionRequest.RouteHop(
                "localhost",
                22,
                new com.ssh.tunnelmanager.domain.credential.Credential("user", "test-password"),
                "final")),
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        Map.of());
  }

  private static final class FakeEngine implements SshTunnelEngine {
    private final AtomicInteger opens = new AtomicInteger();

    private FakeEngine() {}

    @Override
    public TunnelHandle openTunnel(TunnelConnectionRequest request) {
      opens.incrementAndGet();
      if (request.destination().host().equals("bad"))
        throw new IllegalStateException("synthetic failure");
      return new Handle();
    }

    @Override
    public ConnectionTestResult testConnection(TunnelConnectionRequest request) {
      return ConnectionTestResult.success();
    }

    @Override
    public void closeTunnel(TunnelHandle handle) {
      handle.close();
    }

    private int opens() {
      return opens.get();
    }
  }

  private static final class BlockingEngine implements SshTunnelEngine {
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();

    @Override
    public TunnelHandle openTunnel(TunnelConnectionRequest request) {
      int current = inFlight.incrementAndGet();
      maxInFlight.accumulateAndGet(current, Math::max);
      entered.countDown();
      try {
        release.await();
        return new Handle();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("synthetic cancellation", exception);
      } finally {
        inFlight.decrementAndGet();
      }
    }

    @Override
    public ConnectionTestResult testConnection(TunnelConnectionRequest request) {
      return ConnectionTestResult.success();
    }

    @Override
    public void closeTunnel(TunnelHandle handle) {
      handle.close();
    }
  }

  private static final class Handle implements TunnelHandle {
    private boolean open = true;

    @Override
    public boolean isOpen() {
      return open;
    }

    @Override
    public void onClosed(Runnable listener) {}

    @Override
    public void close() {
      open = false;
    }
  }

  private static final class TunnelStore implements TunnelRepository {
    private final Map<UUID, TunnelRecord> values = new HashMap<>();

    private void add(TunnelRecord tunnel) {
      values.put(tunnel.id(), tunnel);
    }

    @Override
    public Optional<TunnelRecord> findById(UUID id) {
      return Optional.ofNullable(values.get(id));
    }

    @Override
    public List<TunnelRecord> findAll() {
      return List.copyOf(values.values());
    }

    @Override
    public void save(TunnelRecord tunnel) {
      values.put(tunnel.id(), tunnel);
    }

    @Override
    public void delete(UUID id) {
      values.remove(id);
    }
  }

  private static final class GroupStore implements TunnelGroupRepository {
    private final Map<UUID, TunnelGroupRecord> values = new HashMap<>();

    @Override
    public Optional<TunnelGroupRecord> findById(UUID id) {
      return Optional.ofNullable(values.get(id));
    }

    @Override
    public List<TunnelGroupRecord> findAll() {
      return List.copyOf(values.values());
    }

    @Override
    public void save(TunnelGroupRecord group) {
      if (values.values().stream()
          .anyMatch(
              existing ->
                  existing.name().equals(group.name()) && !existing.id().equals(group.id())))
        throw new IllegalArgumentException("duplicate name");
      values.put(group.id(), group);
    }

    @Override
    public void delete(UUID id) {
      values.remove(id);
    }
  }
}

package com.ssh.tunnelmanager.application.group;

import com.ssh.tunnelmanager.application.persistence.TunnelGroupRepository;
import com.ssh.tunnelmanager.application.recent.RecentItemService;
import com.ssh.tunnelmanager.application.runtime.GroupEvent;
import com.ssh.tunnelmanager.application.runtime.TunnelEvent;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntime;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager;
import com.ssh.tunnelmanager.domain.tunnel.ActualState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StartGroupUseCase {
  private static final Logger LOGGER = LoggerFactory.getLogger(StartGroupUseCase.class);

  private final TunnelGroupRepository groups;
  private final TunnelRuntimeManager runtime;
  private final com.ssh.tunnelmanager.application.runtime.ApplicationEventPublisher events;
  private final RecentItemService recents;
  private final Map<UUID, List<Tracker>> trackersByTunnel = new HashMap<>();

  public StartGroupUseCase(
      TunnelGroupRepository groups,
      TunnelRuntimeManager runtime,
      com.ssh.tunnelmanager.application.runtime.ApplicationEventPublisher events) {
    this(groups, runtime, events, null);
  }

  public StartGroupUseCase(
      TunnelGroupRepository groups,
      TunnelRuntimeManager runtime,
      com.ssh.tunnelmanager.application.runtime.ApplicationEventPublisher events,
      RecentItemService recents) {
    this.groups = groups;
    this.runtime = runtime;
    this.events = events;
    this.recents = recents;
    runtime.subscribe(this::onTunnelEvent);
  }

  public GroupStartOperation execute(UUID groupId) {
    var group =
        groups
            .findById(groupId)
            .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado: " + groupId));
    UUID operationId = UUID.randomUUID();
    Tracker tracker = new Tracker(operationId, groupId, group.tunnelIds());
    synchronized (trackersByTunnel) {
      for (UUID tunnelId : group.tunnelIds())
        trackersByTunnel.computeIfAbsent(tunnelId, ignored -> new ArrayList<>()).add(tracker);
    }
    LOGGER.info(
        "Inicio de grupo solicitado operationId={} groupId={} requested={}",
        operationId,
        groupId,
        group.tunnelIds().size());
    if (recents != null) recents.recordGroupStart(groupId);
    for (UUID tunnelId : group.tunnelIds()) {
      TunnelRuntime current = runtime.runtime(tunnelId);
      if (current.actualState() == ActualState.OPEN) {
        tracker.record(tunnelId, Result.OPEN);
      } else {
        runtime.start(tunnelId);
        recordIfStable(tracker, tunnelId);
      }
    }
    tracker.completeIfReady();
    return new GroupStartOperation(operationId, groupId, tracker.completion);
  }

  private void onTunnelEvent(TunnelEvent event) {
    if (!(event instanceof TunnelEvent.TunnelStateChanged stateChanged)) return;
    if (!isStable(stateChanged.state())) return;
    List<Tracker> trackers;
    synchronized (trackersByTunnel) {
      trackers = List.copyOf(trackersByTunnel.getOrDefault(stateChanged.tunnelId(), List.of()));
    }
    for (Tracker tracker : trackers) recordIfStable(tracker, stateChanged.tunnelId());
  }

  private void recordIfStable(Tracker tracker, UUID tunnelId) {
    TunnelRuntime state = runtime.runtime(tunnelId);
    if (!isStable(state.actualState())) return;
    Result result =
        state.actualState() == ActualState.OPEN
            ? Result.OPEN
            : state.actualState() == ActualState.ERROR ? Result.ERROR : Result.CANCELLED;
    tracker.record(tunnelId, result);
    tracker.completeIfReady();
  }

  private static boolean isStable(ActualState state) {
    return state == ActualState.OPEN || state == ActualState.ERROR || state == ActualState.CLOSED;
  }

  private enum Result {
    OPEN,
    ERROR,
    CANCELLED
  }

  private final class Tracker {
    private final UUID operationId;
    private final UUID groupId;
    private final Set<UUID> allTunnelIds;
    private final Set<UUID> remaining;
    private final CompletableFuture<GroupStartSummary> completion = new CompletableFuture<>();
    private int open;
    private int errors;
    private int cancelled;

    private Tracker(UUID operationId, UUID groupId, Set<UUID> tunnelIds) {
      this.operationId = operationId;
      this.groupId = groupId;
      this.allTunnelIds = Set.copyOf(tunnelIds);
      this.remaining = new HashSet<>(tunnelIds);
    }

    private synchronized void record(UUID tunnelId, Result result) {
      if (!remaining.remove(tunnelId)) return;
      switch (result) {
        case OPEN -> open++;
        case ERROR -> errors++;
        case CANCELLED -> cancelled++;
      }
    }

    private synchronized void completeIfReady() {
      if (!remaining.isEmpty() || completion.isDone()) return;
      GroupStartSummary summary =
          new GroupStartSummary(
              operationId, groupId, open + errors + cancelled, open, errors, cancelled);
      completion.complete(summary);
      removeTracker(this);
      events.publishGroup(
          new GroupEvent.GroupStartCompleted(
              operationId, groupId, summary.requested(), open, errors, cancelled, Instant.now()));
      LOGGER.info(
          "Inicio de grupo completado operationId={} groupId={} open={} errors={} cancelled={}",
          operationId,
          groupId,
          open,
          errors,
          cancelled);
    }
  }

  private void removeTracker(Tracker tracker) {
    synchronized (trackersByTunnel) {
      for (UUID tunnelId : tracker.allTunnelIds) {
        List<Tracker> trackers = trackersByTunnel.get(tunnelId);
        if (trackers != null) trackers.remove(tracker);
      }
      trackersByTunnel.values().removeIf(List::isEmpty);
    }
  }
}

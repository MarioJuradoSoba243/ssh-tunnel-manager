package com.ssh.tunnelmanager.application.runtime;

import com.ssh.tunnelmanager.application.ssh.SshTunnelEngine;
import com.ssh.tunnelmanager.application.ssh.TunnelConnectionRequest;
import com.ssh.tunnelmanager.application.ssh.TunnelHandle;
import com.ssh.tunnelmanager.application.tunnel.TunnelRequestProvider;
import com.ssh.tunnelmanager.domain.tunnel.ActualState;
import com.ssh.tunnelmanager.domain.tunnel.DesiredState;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TunnelRuntimeManager implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(TunnelRuntimeManager.class);
  private static final Duration[] BACKOFF = {
    Duration.ofSeconds(2),
    Duration.ofSeconds(5),
    Duration.ofSeconds(10),
    Duration.ofSeconds(20),
    Duration.ofSeconds(30)
  };

  private final SshTunnelEngine engine;
  private final TunnelRequestProvider requestProvider;
  private final ApplicationEventPublisher events;
  private final ExecutorService executor;
  private final Semaphore connectionSlots;
  private final int defaultRetryCount;
  private final Sleeper sleeper;
  private final Map<UUID, Slot> slots = new ConcurrentHashMap<>();

  public TunnelRuntimeManager(
      SshTunnelEngine engine,
      TunnelRequestProvider requestProvider,
      ApplicationEventPublisher events,
      int maxConcurrentConnections) {
    this(engine, requestProvider, events, maxConcurrentConnections, 5, Thread::sleep);
  }

  public TunnelRuntimeManager(
      SshTunnelEngine engine,
      TunnelRequestProvider requestProvider,
      ApplicationEventPublisher events,
      int maxConcurrentConnections,
      int defaultRetryCount,
      Sleeper sleeper) {
    if (maxConcurrentConnections < 1)
      throw new IllegalArgumentException("El límite de conexiones debe ser positivo");
    if (defaultRetryCount < 0)
      throw new IllegalArgumentException("El número de reintentos no puede ser negativo");
    this.engine = engine;
    this.requestProvider = requestProvider;
    this.events = events;
    this.executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    this.connectionSlots = new Semaphore(maxConcurrentConnections, true);
    this.defaultRetryCount = defaultRetryCount;
    this.sleeper = sleeper;
  }

  public void start(UUID tunnelId) {
    Slot slot = slots.computeIfAbsent(tunnelId, ignored -> new Slot(tunnelId));
    synchronized (slot) {
      if (slot.runtime.desiredState() == DesiredState.RUNNING
          && slot.runtime.actualState() != ActualState.ERROR
          && slot.runtime.actualState() != ActualState.CLOSED) return;
      if (slot.runtime.actualState() == ActualState.ERROR) slot.generation++;
      slot.runtime =
          newRuntime(
              slot,
              DesiredState.RUNNING,
              ActualState.QUEUED,
              slot.runtime.startedAt() == null ? Instant.now() : slot.runtime.startedAt(),
              0,
              null,
              null);
      publishState(slot);
      submitAttempt(slot, false);
    }
  }

  public void stop(UUID tunnelId) {
    Slot slot = slots.computeIfAbsent(tunnelId, ignored -> new Slot(tunnelId));
    TunnelHandle handle;
    synchronized (slot) {
      slot.generation++;
      slot.runtime =
          newRuntime(slot, DesiredState.STOPPED, ActualState.STOPPING, null, 0, null, null);
      publishState(slot);
      Future<?> task = slot.task;
      if (task != null) task.cancel(true);
      handle = slot.runtime.handle();
      if (handle == null) handle = slot.lastHandle;
      slot.lastHandle = null;
    }
    closeHandle(handle);
    synchronized (slot) {
      slot.runtime =
          newRuntime(slot, DesiredState.STOPPED, ActualState.CLOSED, null, 0, null, null);
      slot.events.clear();
      slot.events.add("Parada manual");
      publishState(slot);
      events.publish(new TunnelEvent.TunnelClosed(tunnelId, Instant.now()));
    }
  }

  public void retry(UUID tunnelId) {
    stop(tunnelId);
    start(tunnelId);
  }

  public TunnelRuntime runtime(UUID tunnelId) {
    Slot slot = slots.computeIfAbsent(tunnelId, ignored -> new Slot(tunnelId));
    synchronized (slot) {
      return newRuntime(
          slot,
          slot.runtime.desiredState(),
          slot.runtime.actualState(),
          slot.runtime.startedAt(),
          slot.runtime.retryAttempt(),
          slot.runtime.lastError(),
          slot.runtime.handle());
    }
  }

  public void subscribe(java.util.function.Consumer<TunnelEvent> listener) {
    events.subscribe(listener);
  }

  private void submitAttempt(Slot slot, boolean reconnect) {
    long generation = slot.generation;
    slot.task = executor.submit(() -> attempt(slot, generation, reconnect));
  }

  private void attempt(Slot slot, long generation, boolean reconnect) {
    boolean acquired = false;
    try {
      if (!isCurrent(slot, generation)) return;
      connectionSlots.acquire();
      acquired = true;
      if (!isCurrent(slot, generation)) return;
      setState(slot, reconnect ? ActualState.RECONNECTING : ActualState.CONNECTING);
      TunnelConnectionRequest request = requestProvider.requestFor(slot.tunnelId);
      synchronized (slot) {
        slot.retryLimit = request.retryCount() == null ? defaultRetryCount : request.retryCount();
      }
      TunnelHandle handle = engine.openTunnel(request);
      AtomicBoolean lossHandled = new AtomicBoolean();
      handle.onClosed(
          () -> {
            if (lossHandled.compareAndSet(false, true)) onConnectionLost(slot, generation, handle);
          });
      synchronized (slot) {
        if (!isCurrentLocked(slot, generation)) {
          closeHandle(handle);
          return;
        }
        slot.lastHandle = handle;
        slot.runtime =
            newRuntime(
                slot,
                DesiredState.RUNNING,
                ActualState.OPEN,
                slot.runtime.startedAt(),
                slot.runtime.retryAttempt(),
                null,
                handle);
        slot.events.add("Conexión abierta");
        publishState(slot);
        events.publish(new TunnelEvent.TunnelOpened(slot.tunnelId, Instant.now()));
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    } catch (Throwable exception) {
      if (isCurrent(slot, generation)) handleFailure(slot, generation, exception);
    } finally {
      if (acquired) connectionSlots.release();
    }
  }

  private void onConnectionLost(Slot slot, long generation, TunnelHandle handle) {
    if (!isCurrent(slot, generation)) return;
    closeHandle(handle);
    synchronized (slot) {
      if (!isCurrentLocked(slot, generation) || slot.runtime.desiredState() != DesiredState.RUNNING)
        return;
      int attempt = slot.runtime.retryAttempt() + 1;
      slot.lastHandle = null;
      if (attempt > slot.retryLimit) {
        handleFailureLocked(
            slot, generation, new IllegalStateException("Se agotaron los reintentos"));
        return;
      }
      slot.runtime =
          newRuntime(
              slot,
              DesiredState.RUNNING,
              ActualState.RECONNECTING,
              slot.runtime.startedAt(),
              attempt,
              null,
              null);
      slot.events.add("Desconexión; reconexión " + attempt + "/" + slot.retryLimit);
      publishState(slot);
      events.publish(new TunnelEvent.TunnelReconnectStarted(slot.tunnelId, attempt, Instant.now()));
      submitAfterBackoff(slot, generation, attempt);
    }
  }

  private void handleFailure(Slot slot, long generation, Throwable exception) {
    synchronized (slot) {
      if (isCurrentLocked(slot, generation)) handleFailureLocked(slot, generation, exception);
    }
  }

  private void handleFailureLocked(Slot slot, long generation, Throwable exception) {
    int attempt = slot.runtime.retryAttempt() + 1;
    if (slot.runtime.desiredState() == DesiredState.RUNNING && attempt <= slot.retryLimit) {
      slot.runtime =
          newRuntime(
              slot,
              DesiredState.RUNNING,
              ActualState.RECONNECTING,
              slot.runtime.startedAt(),
              attempt,
              exception,
              null);
      slot.events.add("Error; reconexión " + attempt + "/" + slot.retryLimit);
      publishState(slot);
      events.publish(new TunnelEvent.TunnelReconnectStarted(slot.tunnelId, attempt, Instant.now()));
      submitAfterBackoff(slot, generation, attempt);
    } else {
      slot.runtime =
          newRuntime(
              slot,
              slot.runtime.desiredState(),
              ActualState.ERROR,
              slot.runtime.startedAt(),
              attempt - 1,
              exception,
              null);
      slot.events.add("Error: " + exception.getMessage());
      publishState(slot);
      events.publish(new TunnelEvent.TunnelError(slot.tunnelId, exception, Instant.now()));
      LOGGER.warn(
          "Tunnel connection failed tunnelId={} retryAttempt={}",
          slot.tunnelId,
          attempt - 1,
          exception);
    }
  }

  private void submitAfterBackoff(Slot slot, long generation, int attempt) {
    slot.task =
        executor.submit(
            () -> {
              try {
                sleeper.sleep(BACKOFF[Math.min(attempt - 1, BACKOFF.length - 1)].toMillis());
                if (isCurrent(slot, generation)) attempt(slot, generation, true);
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
              }
            });
  }

  private boolean isCurrent(Slot slot, long generation) {
    synchronized (slot) {
      return isCurrentLocked(slot, generation);
    }
  }

  private boolean isCurrentLocked(Slot slot, long generation) {
    return slot.generation == generation && slot.runtime.desiredState() == DesiredState.RUNNING;
  }

  private void setState(Slot slot, ActualState state) {
    synchronized (slot) {
      if (slot.runtime.actualState() == state) return;
      slot.runtime =
          newRuntime(
              slot,
              slot.runtime.desiredState(),
              state,
              slot.runtime.startedAt(),
              slot.runtime.retryAttempt(),
              slot.runtime.lastError(),
              slot.runtime.handle());
      publishState(slot);
    }
  }

  private void publishState(Slot slot) {
    events.publish(
        new TunnelEvent.TunnelStateChanged(
            slot.tunnelId, slot.runtime.actualState(), Instant.now()));
  }

  private TunnelRuntime newRuntime(
      Slot slot,
      DesiredState desired,
      ActualState state,
      Instant started,
      int retry,
      Throwable error,
      TunnelHandle handle) {
    return new TunnelRuntime(
        slot.tunnelId,
        desired,
        state,
        started,
        state == ActualState.OPEN ? Instant.now() : slot.runtime.connectionEstablishedAt(),
        retry,
        error,
        slot.generation,
        handle,
        new ArrayList<>(slot.events));
  }

  private void closeHandle(TunnelHandle handle) {
    if (handle == null) return;
    try {
      engine.closeTunnel(handle);
    } catch (RuntimeException exception) {
      LOGGER.warn("Error cerrando handle SSH", exception);
    }
  }

  @Override
  public void close() {
    slots.keySet().forEach(this::stop);
    executor.close();
  }

  @FunctionalInterface
  public interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }

  private static final class Slot {
    private final UUID tunnelId;
    private final List<String> events = new ArrayList<>();
    private TunnelRuntime runtime;
    private TunnelHandle lastHandle;
    private Future<?> task;
    private long generation;
    private int retryLimit;

    private Slot(UUID tunnelId) {
      this.tunnelId = tunnelId;
      this.runtime =
          new TunnelRuntime(
              tunnelId,
              DesiredState.STOPPED,
              ActualState.CLOSED,
              null,
              null,
              0,
              null,
              0,
              null,
              List.of());
      this.retryLimit = 5;
    }
  }
}

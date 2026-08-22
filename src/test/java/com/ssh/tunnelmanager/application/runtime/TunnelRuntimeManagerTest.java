package com.ssh.tunnelmanager.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssh.tunnelmanager.application.ssh.ConnectionTestResult;
import com.ssh.tunnelmanager.application.ssh.SshTunnelEngine;
import com.ssh.tunnelmanager.application.ssh.TunnelConnectionRequest;
import com.ssh.tunnelmanager.application.ssh.TunnelHandle;
import com.ssh.tunnelmanager.application.tunnel.TunnelRequestProvider;
import com.ssh.tunnelmanager.domain.tunnel.ActualState;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TunnelRuntimeManagerTest {
  private TunnelRuntimeManager manager;

  @AfterEach
  void closeManager() {
    if (manager != null) manager.close();
  }

  @Test
  void startIsIdempotentAndStopResetsStartedAt() throws Exception {
    FakeEngine engine = new FakeEngine();
    manager = newManager(engine, ignored -> request());
    UUID tunnelId = UUID.randomUUID();

    manager.start(tunnelId);
    awaitState(tunnelId, ActualState.OPEN);
    InstantHolder started = new InstantHolder(manager.runtime(tunnelId).startedAt());
    manager.start(tunnelId);
    assertThat(engine.openCount()).isEqualTo(1);

    manager.stop(tunnelId);
    assertThat(manager.runtime(tunnelId).actualState()).isEqualTo(ActualState.CLOSED);
    manager.start(tunnelId);
    awaitState(tunnelId, ActualState.OPEN);
    assertThat(manager.runtime(tunnelId).startedAt()).isAfter(started.value);
  }

  @Test
  void connectionLossRebuildsAndKeepsStartedAt() throws Exception {
    FakeEngine engine = new FakeEngine();
    manager = newManager(engine, ignored -> request());
    UUID tunnelId = UUID.randomUUID();

    manager.start(tunnelId);
    awaitState(tunnelId, ActualState.OPEN);
    var startedAt = manager.runtime(tunnelId).startedAt();
    engine.current().simulateClose();
    awaitState(tunnelId, ActualState.OPEN);

    assertThat(engine.openCount()).isEqualTo(2);
    assertThat(manager.runtime(tunnelId).startedAt()).isEqualTo(startedAt);
    assertThat(manager.runtime(tunnelId).retryAttempt()).isEqualTo(1);
  }

  @Test
  void staleGenerationCannotReopenAfterManualStop() throws Exception {
    FakeEngine engine = new FakeEngine();
    manager = newManager(engine, ignored -> request());
    UUID tunnelId = UUID.randomUUID();

    manager.start(tunnelId);
    awaitState(tunnelId, ActualState.OPEN);
    engine.current().simulateClose();
    manager.stop(tunnelId);
    Thread.sleep(50);

    assertThat(manager.runtime(tunnelId).actualState()).isEqualTo(ActualState.CLOSED);
    assertThat(engine.openCount()).isEqualTo(1);
  }

  @Test
  void connectionLossUsesConfiguredBackoffBeforeRebuildingRoute() throws Exception {
    FakeEngine engine = new FakeEngine();
    List<Long> sleeps = new CopyOnWriteArrayList<>();
    manager =
        new TunnelRuntimeManager(
            engine, ignored -> request(), new ApplicationEventPublisher(), 1, 1, sleeps::add);
    UUID tunnelId = UUID.randomUUID();

    manager.start(tunnelId);
    awaitState(tunnelId, ActualState.OPEN);
    engine.current().simulateClose();
    awaitState(tunnelId, ActualState.OPEN);

    assertThat(sleeps).containsExactly(2_000L);
    assertThat(engine.openCount()).isEqualTo(2);
  }

  @Test
  void resumeUsesTheNormalReconnectPolicyAndKeepsStartedAt() throws Exception {
    FakeEngine engine = new FakeEngine();
    List<Long> sleeps = new CopyOnWriteArrayList<>();
    manager =
        new TunnelRuntimeManager(
            engine, ignored -> request(), new ApplicationEventPublisher(), 1, 1, sleeps::add);
    UUID tunnelId = UUID.randomUUID();

    manager.start(tunnelId);
    awaitState(tunnelId, ActualState.OPEN);
    var startedAt = manager.runtime(tunnelId).startedAt();
    manager.onResume();
    awaitState(tunnelId, ActualState.OPEN);

    assertThat(sleeps).containsExactly(2_000L);
    assertThat(manager.runtime(tunnelId).startedAt()).isEqualTo(startedAt);
    assertThat(engine.openCount()).isEqualTo(2);
  }

  @Test
  void aClosedManagerRejectsNewStarts() {
    manager = newManager(new FakeEngine(), ignored -> request());
    manager.close();

    assertThatThrownBy(() -> manager.start(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void retryExhaustionLeavesErrorAndManualRetryStartsNewGeneration() throws Exception {
    FailingEngine engine = new FailingEngine(2);
    manager = newManager(engine, ignored -> request());
    UUID tunnelId = UUID.randomUUID();

    manager.start(tunnelId);
    awaitState(tunnelId, ActualState.ERROR);
    assertThat(engine.openCount()).isEqualTo(2);
    long failedGeneration = manager.runtime(tunnelId).executionGeneration();

    manager.retry(tunnelId);
    awaitState(tunnelId, ActualState.OPEN);
    assertThat(manager.runtime(tunnelId).retryAttempt()).isZero();
    assertThat(manager.runtime(tunnelId).executionGeneration()).isGreaterThan(failedGeneration);
  }

  @Test
  void stopWhileQueuedRemovesTheTunnelWithoutOpeningIt() throws Exception {
    BlockingEngine engine = new BlockingEngine();
    manager = newManager(engine, ignored -> request());
    UUID first = UUID.randomUUID();
    UUID queued = UUID.randomUUID();

    manager.start(first);
    engine.awaitEntered();
    awaitState(first, ActualState.CONNECTING);
    manager.start(queued);
    awaitState(queued, ActualState.QUEUED);
    manager.stop(queued);
    assertThat(manager.runtime(queued).actualState()).isEqualTo(ActualState.CLOSED);
    engine.release();
    awaitState(first, ActualState.OPEN);
    assertThat(engine.openCount()).isEqualTo(1);
  }

  @Test
  void stopWhileConnectingCancelsTheBlockedAttempt() throws Exception {
    BlockingEngine engine = new BlockingEngine();
    manager = newManager(engine, ignored -> request());
    UUID tunnelId = UUID.randomUUID();

    manager.start(tunnelId);
    engine.awaitEntered();
    awaitState(tunnelId, ActualState.CONNECTING);
    manager.stop(tunnelId);
    assertThat(manager.runtime(tunnelId).actualState()).isEqualTo(ActualState.CLOSED);
    engine.release();
    Thread.sleep(50);
    assertThat(manager.runtime(tunnelId).actualState()).isEqualTo(ActualState.CLOSED);
  }

  private TunnelRuntimeManager newManager(SshTunnelEngine engine, TunnelRequestProvider provider) {
    return new TunnelRuntimeManager(
        engine, provider, new ApplicationEventPublisher(), 1, 1, ignored -> {});
  }

  private void awaitState(UUID tunnelId, ActualState expected) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    while (System.nanoTime() < deadline) {
      if (manager.runtime(tunnelId).actualState() == expected) return;
      Thread.sleep(10);
    }
    assertThat(manager.runtime(tunnelId).actualState()).isEqualTo(expected);
  }

  private static TunnelConnectionRequest request() {
    return new TunnelConnectionRequest(
        new com.ssh.tunnelmanager.domain.tunnel.LocalForward("127.0.0.1", 12345),
        new com.ssh.tunnelmanager.domain.tunnel.RemoteDestination("localhost", 5432),
        List.of(
            new TunnelConnectionRequest.RouteHop(
                "localhost",
                22,
                new com.ssh.tunnelmanager.domain.credential.Credential("user", "password"),
                "final")),
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        java.util.Map.of());
  }

  private static final class InstantHolder {
    private final java.time.Instant value;

    private InstantHolder(java.time.Instant value) {
      this.value = value;
    }
  }

  private static final class FakeEngine implements SshTunnelEngine {
    private final AtomicInteger opens = new AtomicInteger();
    private final List<FakeHandle> handles = new CopyOnWriteArrayList<>();

    @Override
    public TunnelHandle openTunnel(TunnelConnectionRequest request) {
      FakeHandle handle = new FakeHandle();
      handles.add(handle);
      opens.incrementAndGet();
      return handle;
    }

    @Override
    public ConnectionTestResult testConnection(TunnelConnectionRequest request) {
      return ConnectionTestResult.success();
    }

    @Override
    public void closeTunnel(TunnelHandle handle) {
      handle.close();
    }

    int openCount() {
      return opens.get();
    }

    FakeHandle current() {
      return handles.get(handles.size() - 1);
    }
  }

  private static final class FakeHandle implements TunnelHandle {
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean open = true;

    @Override
    public boolean isOpen() {
      return open;
    }

    @Override
    public void onClosed(Runnable listener) {
      listeners.add(listener);
    }

    @Override
    public void close() {
      open = false;
    }

    void simulateClose() {
      open = false;
      listeners.forEach(Runnable::run);
    }
  }

  private static final class FailingEngine implements SshTunnelEngine {
    private final AtomicInteger failuresRemaining;
    private final AtomicInteger opens = new AtomicInteger();
    private final FakeEngine delegate = new FakeEngine();

    private FailingEngine(int failures) {
      failuresRemaining = new AtomicInteger(failures);
    }

    @Override
    public TunnelHandle openTunnel(TunnelConnectionRequest request) {
      opens.incrementAndGet();
      if (failuresRemaining.getAndDecrement() > 0)
        throw new IllegalStateException("synthetic connection failure");
      return delegate.openTunnel(request);
    }

    @Override
    public ConnectionTestResult testConnection(TunnelConnectionRequest request) {
      return ConnectionTestResult.success();
    }

    @Override
    public void closeTunnel(TunnelHandle handle) {
      handle.close();
    }

    int openCount() {
      return opens.get();
    }
  }

  private static final class BlockingEngine implements SshTunnelEngine {
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger opens = new AtomicInteger();

    @Override
    public TunnelHandle openTunnel(TunnelConnectionRequest request) {
      opens.incrementAndGet();
      entered.countDown();
      try {
        release.await();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("connection cancelled", exception);
      }
      return new FakeHandle();
    }

    @Override
    public ConnectionTestResult testConnection(TunnelConnectionRequest request) {
      return ConnectionTestResult.success();
    }

    @Override
    public void closeTunnel(TunnelHandle handle) {
      handle.close();
    }

    void awaitEntered() throws InterruptedException {
      assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
    }

    void release() {
      release.countDown();
    }

    int openCount() {
      return opens.get();
    }
  }
}

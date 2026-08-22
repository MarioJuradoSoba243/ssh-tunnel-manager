package com.ssh.tunnelmanager.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

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
}

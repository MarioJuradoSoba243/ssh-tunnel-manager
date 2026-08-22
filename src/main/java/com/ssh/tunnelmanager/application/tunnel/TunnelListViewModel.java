package com.ssh.tunnelmanager.application.tunnel;

import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import com.ssh.tunnelmanager.application.runtime.TunnelEvent;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntime;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager;
import com.ssh.tunnelmanager.application.ssh.ConnectionTestResult;
import java.util.List;
import java.util.UUID;

public final class TunnelListViewModel {
  private final TunnelRepository tunnels;
  private final TunnelRuntimeManager runtime;
  private final StartTunnelUseCase start;
  private final StopTunnelUseCase stop;
  private final RetryTunnelUseCase retry;
  private final TestTunnelConnectionUseCase test;

  public TunnelListViewModel(
      TunnelRepository tunnels,
      TunnelRuntimeManager runtime,
      StartTunnelUseCase start,
      StopTunnelUseCase stop,
      RetryTunnelUseCase retry,
      TestTunnelConnectionUseCase test) {
    this.tunnels = tunnels;
    this.runtime = runtime;
    this.start = start;
    this.stop = stop;
    this.retry = retry;
    this.test = test;
  }

  public List<TunnelRecord> tunnels() {
    return tunnels.findAll();
  }

  public TunnelRuntime runtime(UUID tunnelId) {
    return runtime.runtime(tunnelId);
  }

  public void start(UUID tunnelId) {
    start.execute(tunnelId);
  }

  public void stop(UUID tunnelId) {
    stop.execute(tunnelId);
  }

  public void retry(UUID tunnelId) {
    retry.execute(tunnelId);
  }

  public ConnectionTestResult test(UUID tunnelId) {
    return test.execute(tunnelId);
  }

  public void subscribe(java.util.function.Consumer<TunnelEvent> listener) {
    runtime.subscribe(listener);
  }
}

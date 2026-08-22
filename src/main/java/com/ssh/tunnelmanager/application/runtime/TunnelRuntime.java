package com.ssh.tunnelmanager.application.runtime;

import com.ssh.tunnelmanager.application.ssh.TunnelHandle;
import com.ssh.tunnelmanager.domain.tunnel.ActualState;
import com.ssh.tunnelmanager.domain.tunnel.DesiredState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TunnelRuntime(
    UUID tunnelId,
    DesiredState desiredState,
    ActualState actualState,
    Instant startedAt,
    Instant connectionEstablishedAt,
    int retryAttempt,
    Throwable lastError,
    long executionGeneration,
    TunnelHandle handle,
    List<String> sessionEvents) {
  public TunnelRuntime {
    sessionEvents = List.copyOf(sessionEvents == null ? List.of() : sessionEvents);
  }
}

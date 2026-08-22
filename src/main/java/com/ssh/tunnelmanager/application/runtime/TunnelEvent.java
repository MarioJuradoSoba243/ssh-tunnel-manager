package com.ssh.tunnelmanager.application.runtime;

import com.ssh.tunnelmanager.domain.tunnel.ActualState;
import java.time.Instant;
import java.util.UUID;

public sealed interface TunnelEvent
    permits TunnelEvent.TunnelStateChanged,
        TunnelEvent.TunnelOpened,
        TunnelEvent.TunnelClosed,
        TunnelEvent.TunnelReconnectStarted,
        TunnelEvent.TunnelError {
  UUID tunnelId();

  Instant at();

  record TunnelStateChanged(UUID tunnelId, ActualState state, Instant at) implements TunnelEvent {}

  record TunnelOpened(UUID tunnelId, Instant at) implements TunnelEvent {}

  record TunnelClosed(UUID tunnelId, Instant at) implements TunnelEvent {}

  record TunnelReconnectStarted(UUID tunnelId, int attempt, Instant at) implements TunnelEvent {}

  record TunnelError(UUID tunnelId, Throwable error, Instant at) implements TunnelEvent {}
}

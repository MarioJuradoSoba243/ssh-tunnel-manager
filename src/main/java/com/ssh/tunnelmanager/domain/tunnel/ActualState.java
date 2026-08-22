package com.ssh.tunnelmanager.domain.tunnel;

public enum ActualState {
  CLOSED,
  QUEUED,
  CONNECTING,
  OPEN,
  RECONNECTING,
  STOPPING,
  ERROR;

  public boolean isActive() {
    return this == QUEUED || this == CONNECTING || this == OPEN || this == RECONNECTING;
  }
}

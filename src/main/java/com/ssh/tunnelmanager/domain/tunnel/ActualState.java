package com.ssh.tunnelmanager.domain.tunnel;

public enum ActualState {
  CLOSED,
  QUEUED,
  CONNECTING,
  OPEN,
  RECONNECTING,
  STOPPING,
  ERROR
}

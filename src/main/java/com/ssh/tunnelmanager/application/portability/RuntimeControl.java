package com.ssh.tunnelmanager.application.portability;

public interface RuntimeControl {
  boolean hasActiveTunnels();

  void stopAllAndAwait();
}

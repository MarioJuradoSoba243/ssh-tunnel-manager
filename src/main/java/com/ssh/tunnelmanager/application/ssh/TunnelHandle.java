package com.ssh.tunnelmanager.application.ssh;

public interface TunnelHandle extends AutoCloseable {
  boolean isOpen();

  void onClosed(Runnable listener);

  @Override
  void close();
}

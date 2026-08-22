package com.ssh.tunnelmanager.application.notifications;

public interface NotificationPort extends AutoCloseable {
  void notify(String title, String message);

  @Override
  default void close() {}
}

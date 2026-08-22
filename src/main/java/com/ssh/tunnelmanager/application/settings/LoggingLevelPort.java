package com.ssh.tunnelmanager.application.settings;

@FunctionalInterface
public interface LoggingLevelPort {
  void apply(String level);
}

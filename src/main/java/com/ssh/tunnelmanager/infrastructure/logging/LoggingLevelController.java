package com.ssh.tunnelmanager.infrastructure.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

public final class LoggingLevelController {
  private LoggingLevelController() {}

  public static void apply(String level) {
    Configurator.setLevel("", Level.valueOf(level));
  }
}

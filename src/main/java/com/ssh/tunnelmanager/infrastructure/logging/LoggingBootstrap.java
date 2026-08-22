package com.ssh.tunnelmanager.infrastructure.logging;

import com.ssh.tunnelmanager.infrastructure.persistence.DataDirectories;
import com.ssh.tunnelmanager.infrastructure.persistence.DataDirectoryResolver;

/** Prepares the data-dependent Log4j path before JavaFX creates the application. */
public final class LoggingBootstrap {
  private static final String LOG_DIRECTORY_PROPERTY = "ssh.tunnel.manager.log.dir";

  private LoggingBootstrap() {}

  public static void configure(DataDirectories directories) {
    DataDirectoryResolver.createDirectories(directories);
    System.setProperty(LOG_DIRECTORY_PROPERTY, directories.logs().toAbsolutePath().toString());
  }
}

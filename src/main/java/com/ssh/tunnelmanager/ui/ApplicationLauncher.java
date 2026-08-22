package com.ssh.tunnelmanager.ui;

import com.ssh.tunnelmanager.application.bootstrap.ApplicationBootstrap;
import com.ssh.tunnelmanager.infrastructure.logging.LoggingBootstrap;
import javafx.application.Application;

/** Plain Java launcher used by Maven, IntelliJ IDEA, and jpackage. */
public final class ApplicationLauncher {
  private ApplicationLauncher() {}

  public static void main(String[] args) {
    LoggingBootstrap.configure(ApplicationBootstrap.defaultDirectories());
    Application.launch(SshTunnelManagerApplication.class, args);
  }
}

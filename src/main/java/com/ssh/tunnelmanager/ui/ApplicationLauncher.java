package com.ssh.tunnelmanager.ui;

import javafx.application.Application;

/** Plain Java launcher used by Maven, IntelliJ IDEA, and jpackage. */
public final class ApplicationLauncher {
  private ApplicationLauncher() {}

  public static void main(String[] args) {
    Application.launch(SshTunnelManagerApplication.class, args);
  }
}

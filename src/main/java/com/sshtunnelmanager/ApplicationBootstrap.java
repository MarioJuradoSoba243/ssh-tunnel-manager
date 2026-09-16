package com.sshtunnelmanager;

import com.sshtunnelmanager.application.ApplicationInfo;
import com.sshtunnelmanager.ui.shell.ShellController;

/** Explicit composition root for the application object graph. */
public final class ApplicationBootstrap {
  private final ApplicationInfo applicationInfo;

  public ApplicationBootstrap() {
    this(new ApplicationInfo());
  }

  ApplicationBootstrap(ApplicationInfo applicationInfo) {
    this.applicationInfo = applicationInfo;
  }

  public ShellController createShellController() {
    return new ShellController(applicationInfo);
  }
}

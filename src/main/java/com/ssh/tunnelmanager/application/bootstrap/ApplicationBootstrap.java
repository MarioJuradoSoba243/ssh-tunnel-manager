package com.ssh.tunnelmanager.application.bootstrap;

import com.ssh.tunnelmanager.application.shell.NavigationItem;
import com.ssh.tunnelmanager.application.shell.ShellViewModel;
import java.util.List;

/** Explicit composition root for the application object graph. */
public final class ApplicationBootstrap {
  private final ApplicationInfo applicationInfo;

  public ApplicationBootstrap(ApplicationInfo applicationInfo) {
    this.applicationInfo = applicationInfo;
  }

  public ApplicationInfo applicationInfo() {
    return applicationInfo;
  }

  public ShellViewModel createShellViewModel() {
    return new ShellViewModel(
        List.of(
            new NavigationItem("home", "Inicio"),
            new NavigationItem("tunnels", "Túneles"),
            new NavigationItem("groups", "Grupos"),
            new NavigationItem("hosts", "Hosts SSH"),
            new NavigationItem("organization", "Organización"),
            new NavigationItem("settings", "Ajustes")));
  }
}

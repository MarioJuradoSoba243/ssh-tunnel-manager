package com.ssh.tunnelmanager.application.shell;

import java.util.List;

public final class ShellViewModel {
  private final List<NavigationItem> navigationItems;

  public ShellViewModel(List<NavigationItem> navigationItems) {
    this.navigationItems = List.copyOf(navigationItems);
  }

  public List<NavigationItem> navigationItems() {
    return navigationItems;
  }
}

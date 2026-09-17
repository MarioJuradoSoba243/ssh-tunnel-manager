package com.ssh.tunnelmanager.ui.shell;

import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.layout.VBox;

/** Replaces the shell's primary content instead of stacking navigable views. */
final class MainContentNavigator {
  private final VBox container;

  MainContentNavigator(VBox container) {
    this.container = Objects.requireNonNull(container);
  }

  void replace(Node... views) {
    container.getChildren().setAll(views);
  }
}

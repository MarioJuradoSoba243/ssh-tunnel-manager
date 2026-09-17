package com.ssh.tunnelmanager.ui.shell;

import java.util.Locale;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/** Small text-and-dot state indicator that remains readable without colour alone. */
public final class StatusBadge extends HBox {
  public StatusBadge(String text, String stateClass) {
    String normalizedState = stateClass == null ? "" : stateClass.toUpperCase(Locale.ROOT);
    Label icon = new Label(iconFor(normalizedState));
    icon.getStyleClass().addAll("status-dot", "status-icon");
    Label value = new Label(text);
    value.getStyleClass().add("status-label");
    getStyleClass().addAll("status-badge", normalizedState.toLowerCase(Locale.ROOT));
    getChildren().addAll(icon, value);
  }

  private static String iconFor(String state) {
    return switch (state) {
      case "OPEN" -> "●";
      case "CONNECTING", "STOPPING" -> "◌";
      case "QUEUED" -> "⋯";
      case "RECONNECTING" -> "↻";
      case "ERROR" -> "!";
      case "CLOSED" -> "○";
      default -> "•";
    };
  }
}

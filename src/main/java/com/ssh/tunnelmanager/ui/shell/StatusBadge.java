package com.ssh.tunnelmanager.ui.shell;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/** Small text-and-dot state indicator that remains readable without colour alone. */
public final class StatusBadge extends HBox {
  public StatusBadge(String text, String stateClass) {
    Label dot = new Label("●");
    dot.getStyleClass().add("status-dot");
    Label value = new Label(text);
    value.getStyleClass().add("status-label");
    getStyleClass().addAll("status-badge", stateClass.toLowerCase());
    getChildren().addAll(dot, value);
  }
}

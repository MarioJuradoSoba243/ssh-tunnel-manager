package com.ssh.tunnelmanager.ui.shell;

import java.util.Objects;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/** Removable, keyboard-accessible representation of an active filter. */
public final class FilterChip extends HBox {
  public FilterChip(String text, Runnable onRemove) {
    Label label = new Label(Objects.requireNonNull(text));
    Button remove = new Button("×");
    remove.setAccessibleText("Quitar filtro " + text);
    remove.getStyleClass().add("chip-remove");
    remove.setOnAction(event -> onRemove.run());
    getStyleClass().add("filter-chip");
    getChildren().addAll(label, remove);
  }
}

package com.ssh.tunnelmanager.ui.shell;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Non-modal feedback surface owned by the presentation layer. */
public final class ToastHost extends VBox {
  private static final int MAX_TOASTS = 4;

  public ToastHost() {
    setMouseTransparent(false);
    setPickOnBounds(false);
    getStyleClass().add("toast-host");
  }

  public void show(String message, String severity) {
    Runnable action =
        () -> {
          String style = severity == null ? "info" : severity.toLowerCase();
          Label text = new Label(message);
          text.getStyleClass().add("toast-message");
          text.setWrapText(true);
          Button close = new Button("×");
          close.getStyleClass().add("toast-close");
          close.setAccessibleText("Cerrar aviso");
          HBox toast = new HBox(8, text, close);
          toast.setAlignment(Pos.CENTER_LEFT);
          HBox.setHgrow(text, Priority.ALWAYS);
          toast.getStyleClass().addAll("toast", style);
          close.setOnAction(event -> getChildren().remove(toast));
          while (getChildren().size() >= MAX_TOASTS) getChildren().remove(0);
          getChildren().add(toast);
          PauseTransition expiry = new PauseTransition(javafx.util.Duration.seconds(4));
          expiry.setOnFinished(event -> getChildren().remove(toast));
          expiry.play();
        };
    if (Platform.isFxApplicationThread()) action.run();
    else Platform.runLater(action);
  }
}

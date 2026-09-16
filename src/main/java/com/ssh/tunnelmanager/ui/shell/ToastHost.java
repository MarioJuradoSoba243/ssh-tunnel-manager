package com.ssh.tunnelmanager.ui.shell;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Non-modal feedback surface owned by the presentation layer. */
public final class ToastHost extends VBox {
  public ToastHost() {
    setMouseTransparent(true);
    getStyleClass().add("toast-host");
  }

  public void show(String message, String severity) {
    Runnable action =
        () -> {
          Label toast = new Label(message);
          toast.getStyleClass().addAll("toast", severity.toLowerCase());
          toast.setWrapText(true);
          getChildren().add(toast);
          PauseTransition expiry = new PauseTransition(javafx.util.Duration.seconds(4));
          expiry.setOnFinished(event -> getChildren().remove(toast));
          expiry.play();
        };
    if (Platform.isFxApplicationThread()) action.run();
    else Platform.runLater(action);
  }
}

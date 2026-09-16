package com.sshtunnelmanager.ui;

import com.sshtunnelmanager.ApplicationBootstrap;
import com.sshtunnelmanager.ui.shell.ShellController;
import java.io.IOException;
import java.net.URL;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** JavaFX entry point. */
public final class SshTunnelManagerApplication extends Application {
  static final double INITIAL_WIDTH = 1024;
  static final double INITIAL_HEIGHT = 700;

  @Override
  public void start(Stage stage) throws IOException {
    ApplicationBootstrap bootstrap = new ApplicationBootstrap();
    ShellController controller = bootstrap.createShellController();
    FXMLLoader loader = new FXMLLoader(resource("/com/sshtunnelmanager/ui/shell/shell.fxml"));
    loader.setController(controller);
    Parent root = loader.load();

    Scene scene = new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
    scene
        .getStylesheets()
        .add(resource("/com/sshtunnelmanager/ui/styles/base.css").toExternalForm());
    scene
        .getStylesheets()
        .add(resource("/com/sshtunnelmanager/ui/styles/light.css").toExternalForm());
    stage.setTitle(controller.applicationTitle());
    stage.setScene(scene);
    stage.show();
  }

  private static URL resource(String path) {
    URL resource = SshTunnelManagerApplication.class.getResource(path);
    if (resource == null) {
      throw new IllegalStateException("Required UI resource is missing: " + path);
    }
    return resource;
  }

  public static void main(String[] args) {
    launch(args);
  }
}

package com.ssh.tunnelmanager.ui;

import com.ssh.tunnelmanager.application.bootstrap.ApplicationBootstrap;
import com.ssh.tunnelmanager.application.bootstrap.ApplicationInfo;
import com.ssh.tunnelmanager.application.shell.ShellViewModel;
import com.ssh.tunnelmanager.ui.shell.ShellController;
import com.ssh.tunnelmanager.ui.ssh.FxHostKeyApprovalCoordinator;
import com.ssh.tunnelmanager.ui.theme.ThemeManager;
import com.ssh.tunnelmanager.ui.theme.ThemePreference;
import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SshTunnelManagerApplication extends Application {
  private static final Logger LOGGER = LoggerFactory.getLogger(SshTunnelManagerApplication.class);
  private static final String VERSION = "0.1.0";

  private ApplicationBootstrap bootstrap;

  public SshTunnelManagerApplication() {
    this(new ApplicationBootstrap(new ApplicationInfo(VERSION)));
  }

  public SshTunnelManagerApplication(ApplicationBootstrap bootstrap) {
    this.bootstrap = bootstrap;
  }

  @Override
  public void init() {
    if (!bootstrap.isProductionConfigured()) {
      bootstrap =
          ApplicationBootstrap.productionDefault(
              new ApplicationInfo(VERSION), new FxHostKeyApprovalCoordinator());
    }
  }

  @Override
  public void start(Stage stage) throws IOException {
    Scene scene = createScene();
    stage.setTitle("Gestor de túneles SSH");
    stage.setMinWidth(800);
    stage.setMinHeight(560);
    stage.setScene(scene);
    stage.show();
    LOGGER.info("Aplicación iniciada version={}", bootstrap.applicationInfo().version());
  }

  Scene createScene() throws IOException {
    ShellViewModel viewModel = bootstrap.createShellViewModel();
    FXMLLoader loader =
        new FXMLLoader(
            SshTunnelManagerApplication.class.getResource(
                "/com/ssh/tunnelmanager/ui/shell/shell.fxml"));
    loader.setControllerFactory(
        type -> {
          if (type == ShellController.class) {
            return new ShellController(
                viewModel, bootstrap.isProductionConfigured() ? bootstrap.components() : null);
          }
          throw new IllegalArgumentException("Controlador no registrado: " + type.getName());
        });
    Parent root = loader.load();
    Scene scene = new Scene(root, 1024, 700);
    new ThemeManager(() -> ThemePreference.LIGHT).apply(scene, ThemePreference.SYSTEM);
    return scene;
  }

  @Override
  public void stop() {
    if (bootstrap.isProductionConfigured()) bootstrap.components().runtimeManager().close();
  }

  public static void main(String[] args) {
    launch(args);
  }
}

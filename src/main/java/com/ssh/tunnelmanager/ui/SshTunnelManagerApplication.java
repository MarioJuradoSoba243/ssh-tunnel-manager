package com.ssh.tunnelmanager.ui;

import com.ssh.tunnelmanager.application.bootstrap.ApplicationBootstrap;
import com.ssh.tunnelmanager.application.bootstrap.ApplicationInfo;
import com.ssh.tunnelmanager.application.shell.ShellViewModel;
import com.ssh.tunnelmanager.ui.shell.ShellController;
import com.ssh.tunnelmanager.ui.ssh.FxHostKeyApprovalCoordinator;
import com.ssh.tunnelmanager.ui.theme.ThemeManager;
import com.ssh.tunnelmanager.ui.theme.ThemePreference;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SshTunnelManagerApplication extends Application {
  private static final Logger LOGGER = LoggerFactory.getLogger(SshTunnelManagerApplication.class);
  private static final String VERSION = "0.4.0";

  private ApplicationBootstrap bootstrap;
  private ScheduledExecutorService maintenance;
  private final java.util.concurrent.ExecutorService recoveryWorker =
      Executors.newVirtualThreadPerTaskExecutor();

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
    if (bootstrap.isRecoveryMode()) {
      showRecovery(stage);
      return;
    }
    Scene scene = createScene();
    stage.setTitle("Gestor de túneles SSH");
    stage.setMinWidth(800);
    stage.setMinHeight(560);
    stage.setScene(scene);
    stage.show();
    if (bootstrap.isProductionConfigured()) {
      maintenance = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
      maintenance.scheduleAtFixedRate(
          () -> bootstrap.components().backups().backupIfDue(), 30, 30, TimeUnit.MINUTES);
    }
    LOGGER.info("Aplicación iniciada version={}", bootstrap.applicationInfo().version());
  }

  private void showRecovery(Stage stage) {
    List<Path> available = bootstrap.recoveryBackups();
    javafx.scene.control.ListView<Path> backups =
        new javafx.scene.control.ListView<>(
            javafx.collections.FXCollections.observableArrayList(available));
    backups.setPlaceholder(new javafx.scene.control.Label("No hay backups SQLite utilizables."));
    javafx.scene.control.Button restore =
        new javafx.scene.control.Button("Restaurar backup seleccionado");
    javafx.scene.control.Label details =
        new javafx.scene.control.Label(
            "La base principal no se puede abrir y no se sobrescribirá automáticamente.\n"
                + bootstrap.recoveryMessage());
    restore.setOnAction(
        event -> {
          Path selected = backups.getSelectionModel().getSelectedItem();
          if (selected == null) return;
          restore.setDisable(true);
          recoveryWorker.submit(
              () -> {
                try {
                  bootstrap.restoreRecovery(selected);
                  Platform.runLater(
                      () -> {
                        try {
                          stage.setScene(createScene());
                          stage.setTitle("Gestor de túneles SSH");
                        } catch (IOException exception) {
                          showRecoveryError(exception);
                        }
                      });
                } catch (RuntimeException exception) {
                  Platform.runLater(
                      () -> {
                        restore.setDisable(false);
                        showRecoveryError(exception);
                      });
                }
              });
        });
    javafx.scene.layout.VBox content =
        new javafx.scene.layout.VBox(
            12,
            new javafx.scene.control.Label("Recuperación de configuración"),
            details,
            backups,
            restore);
    javafx.scene.layout.VBox.setVgrow(backups, javafx.scene.layout.Priority.ALWAYS);
    stage.setTitle("Gestor de túneles SSH — recuperación");
    stage.setScene(new Scene(content, 800, 520));
    stage.show();
  }

  private void showRecoveryError(Throwable exception) {
    javafx.scene.control.Alert alert =
        new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.ERROR,
            exception.getMessage() == null ? "Error de recuperación" : exception.getMessage());
    alert.setHeaderText("No se pudo restaurar el backup");
    alert.showAndWait();
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
    recoveryWorker.close();
    if (maintenance != null) maintenance.close();
    if (bootstrap.isProductionConfigured()) bootstrap.components().runtimeManager().close();
  }

  public static void main(String[] args) {
    launch(args);
  }
}

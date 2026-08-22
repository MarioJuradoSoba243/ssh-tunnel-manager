package com.ssh.tunnelmanager.ui;

import com.ssh.tunnelmanager.application.bootstrap.ApplicationBootstrap;
import com.ssh.tunnelmanager.application.bootstrap.ApplicationInfo;
import com.ssh.tunnelmanager.application.shell.ShellViewModel;
import com.ssh.tunnelmanager.infrastructure.logging.LoggingLevelController;
import com.ssh.tunnelmanager.infrastructure.persistence.DataDirectories;
import com.ssh.tunnelmanager.infrastructure.persistence.DataDirectoryResolver;
import com.ssh.tunnelmanager.infrastructure.windows.ExecutionMarker;
import com.ssh.tunnelmanager.infrastructure.windows.ResumeDetector;
import com.ssh.tunnelmanager.infrastructure.windows.SingleInstanceCoordinator;
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
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SshTunnelManagerApplication extends Application {
  private static final Logger LOGGER = LoggerFactory.getLogger(SshTunnelManagerApplication.class);
  private static final String VERSION = "0.5.0";
  private static final String EXECUTION_MARKER = "application.run.marker";

  private ApplicationBootstrap bootstrap;
  private ScheduledExecutorService maintenance;
  private ScheduledExecutorService lifecycleMonitor;
  private java.util.concurrent.ExecutorService shutdownWorker;
  private final java.util.concurrent.ExecutorService recoveryWorker =
      Executors.newVirtualThreadPerTaskExecutor();
  private SingleInstanceCoordinator singleInstance;
  private ExecutionMarker executionMarker;
  private ResumeDetector resumeDetector;
  private com.ssh.tunnelmanager.ui.theme.WindowsSystemThemeProvider systemThemeProvider;
  private ThemeManager themeManager;
  private Scene activeScene;
  private ThemePreference activeThemePreference;
  private Stage stage;
  private ShellController shellController;
  private boolean secondaryInstance;
  private boolean previousRunAbnormal;
  private boolean closing;
  private boolean allowStageClose;

  public SshTunnelManagerApplication() {
    this(new ApplicationBootstrap(new ApplicationInfo(VERSION)));
  }

  public SshTunnelManagerApplication(ApplicationBootstrap bootstrap) {
    this.bootstrap = bootstrap;
  }

  @Override
  public void init() {
    DataDirectories directories = ApplicationBootstrap.defaultDirectories();
    DataDirectoryResolver.createDirectories(directories);
    singleInstance =
        SingleInstanceCoordinator.acquire(directories.dataDirectory(), this::bringToFront);
    if (!singleInstance.isPrimary()) {
      secondaryInstance = true;
      return;
    }
    previousRunAbnormal =
        ExecutionMarker.previousRunWasAbnormal(
            directories.dataDirectory().resolve(EXECUTION_MARKER));
    executionMarker = ExecutionMarker.open(directories.dataDirectory().resolve(EXECUTION_MARKER));
    if (!bootstrap.isProductionConfigured()) {
      bootstrap =
          ApplicationBootstrap.productionDefault(
              new ApplicationInfo(VERSION), new FxHostKeyApprovalCoordinator());
    }
    if (bootstrap.isProductionConfigured()) {
      LoggingLevelController.apply(bootstrap.components().settings().loggingLevel());
    }
  }

  @Override
  public void start(Stage stage) throws IOException {
    this.stage = stage;
    if (secondaryInstance) {
      Platform.exit();
      return;
    }
    if (bootstrap.isRecoveryMode()) {
      stage.setOnCloseRequest(this::handleCloseRequest);
      showRecovery(stage);
      return;
    }
    Scene scene = createScene();
    stage.setTitle("Gestor de túneles SSH");
    stage.setMinWidth(800);
    stage.setMinHeight(560);
    stage.setScene(scene);
    stage.setOnCloseRequest(this::handleCloseRequest);
    stage.show();
    if (bootstrap.isProductionConfigured()) {
      maintenance = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
      maintenance.scheduleAtFixedRate(
          () -> bootstrap.components().backups().backupIfDue(), 30, 30, TimeUnit.MINUTES);
      resumeDetector = new ResumeDetector(java.time.Duration.ofSeconds(30));
      lifecycleMonitor = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
      lifecycleMonitor.scheduleAtFixedRate(
          () -> {
            if (resumeDetector.poll()) bootstrap.components().runtimeManager().onResume();
            if (activeThemePreference == ThemePreference.SYSTEM && systemThemeProvider != null) {
              ThemePreference current = systemThemeProvider.currentTheme();
              Platform.runLater(() -> themeManager.apply(activeScene, current));
            }
          },
          30,
          30,
          TimeUnit.SECONDS);
    }
    LOGGER.info("Aplicación iniciada version={}", bootstrap.applicationInfo().version());
    if (previousRunAbnormal) {
      new Alert(
              Alert.AlertType.WARNING,
              "La ejecución anterior terminó de forma anómala. No se han reabierto túneles automáticamente.")
          .showAndWait();
    }
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
            shellController =
                new ShellController(
                    viewModel, bootstrap.isProductionConfigured() ? bootstrap.components() : null);
            return shellController;
          }
          throw new IllegalArgumentException("Controlador no registrado: " + type.getName());
        });
    Parent root = loader.load();
    Scene scene = new Scene(root, 1024, 700);
    systemThemeProvider = new com.ssh.tunnelmanager.ui.theme.WindowsSystemThemeProvider();
    themeManager =
        bootstrap.isProductionConfigured()
            ? new ThemeManager(systemThemeProvider)
            : new ThemeManager(() -> ThemePreference.LIGHT);
    ThemePreference preference =
        bootstrap.isProductionConfigured()
            ? ThemePreference.valueOf(bootstrap.components().settings().theme())
            : ThemePreference.SYSTEM;
    activeScene = scene;
    activeThemePreference = preference;
    themeManager.apply(scene, preference);
    if (shellController != null) {
      shellController.setThemeApplier(
          value -> {
            activeThemePreference = value;
            themeManager.apply(scene, value);
          });
    }
    return scene;
  }

  private void bringToFront() {
    Platform.runLater(
        () -> {
          if (stage != null) {
            stage.toFront();
            stage.requestFocus();
          }
        });
  }

  private void handleCloseRequest(WindowEvent event) {
    if (allowStageClose || closing) return;
    event.consume();
    if (hasActiveTunnels()) {
      Alert confirmation =
          new Alert(
              Alert.AlertType.CONFIRMATION,
              "Hay túneles activos. Se detendrán antes de cerrar la aplicación.");
      confirmation.setHeaderText("Confirmar cierre");
      confirmation.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
      if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
    }
    closing = true;
    shutdownWorker = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    shutdownWorker.submit(this::shutdownOrdered);
  }

  private boolean hasActiveTunnels() {
    return bootstrap.isProductionConfigured()
        && bootstrap.components().runtimeManager().runtimes().values().stream()
            .anyMatch(
                runtime ->
                    runtime.actualState()
                        != com.ssh.tunnelmanager.domain.tunnel.ActualState.CLOSED);
  }

  private void shutdownOrdered() {
    try {
      if (bootstrap.isProductionConfigured()) bootstrap.components().runtimeManager().close();
      if (bootstrap.isProductionConfigured()) bootstrap.components().notifications().close();
      if (shellController != null) shellController.close();
      if (maintenance != null) maintenance.close();
      if (lifecycleMonitor != null) lifecycleMonitor.close();
      if (singleInstance != null) singleInstance.close();
      if (executionMarker != null) executionMarker.close();
    } finally {
      Platform.runLater(
          () -> {
            allowStageClose = true;
            if (stage != null) stage.close();
          });
    }
  }

  @Override
  public void stop() {
    if (!closing) {
      closing = true;
      if (bootstrap.isProductionConfigured()) bootstrap.components().runtimeManager().close();
      if (bootstrap.isProductionConfigured()) bootstrap.components().notifications().close();
      if (shellController != null) shellController.close();
      if (maintenance != null) maintenance.close();
      if (lifecycleMonitor != null) lifecycleMonitor.close();
      if (singleInstance != null) singleInstance.close();
      if (executionMarker != null) executionMarker.close();
    }
    recoveryWorker.close();
    if (shutdownWorker != null) shutdownWorker.close();
  }

  public static void main(String[] args) {
    launch(args);
  }
}

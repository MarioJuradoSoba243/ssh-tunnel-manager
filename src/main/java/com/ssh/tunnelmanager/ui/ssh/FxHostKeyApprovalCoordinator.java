package com.ssh.tunnelmanager.ui.ssh;

import com.ssh.tunnelmanager.application.ssh.HostKeyApprovalCoordinator;
import com.ssh.tunnelmanager.application.ssh.HostKeyApprovalRequest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public final class FxHostKeyApprovalCoordinator implements HostKeyApprovalCoordinator {
  @Override
  public boolean approve(HostKeyApprovalRequest request) {
    if (Platform.isFxApplicationThread()) return show(request);
    CountDownLatch completed = new CountDownLatch(1);
    AtomicReference<Boolean> result = new AtomicReference<>(false);
    Platform.runLater(
        () -> {
          try {
            result.set(show(request));
          } finally {
            completed.countDown();
          }
        });
    try {
      completed.await();
      return result.get();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static boolean show(HostKeyApprovalRequest request) {
    if (request.previousFingerprintSha256() != null) {
      Alert changed = new Alert(Alert.AlertType.ERROR);
      changed.setTitle("Fingerprint SSH cambiada");
      changed.setHeaderText("Conexión bloqueada por cambio de host key");
      changed.setContentText(
          request.hostname()
              + ":"
              + request.sshPort()
              + " ("
              + request.routePosition()
              + ")\nFingerprint anterior: "
              + request.previousFingerprintSha256()
              + "\nFingerprint nueva: "
              + request.fingerprintSha256()
              + "\n\nElimina la confianza desde Hosts SSH solo si el cambio es esperado.");
      changed.showAndWait();
      return false;
    }
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle("Aprobar fingerprint SSH");
    alert.setHeaderText("Host SSH desconocido");
    alert.setContentText(
        request.hostname()
            + ":"
            + request.sshPort()
            + " ("
            + request.routePosition()
            + ")\nAlgoritmo: "
            + request.keyAlgorithm()
            + "\nFingerprint SHA-256: "
            + request.fingerprintSha256());
    return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
  }
}

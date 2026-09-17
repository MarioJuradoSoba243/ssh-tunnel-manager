package com.ssh.tunnelmanager.ui.shell;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.Test;

class ToastHostTest {
  @Test
  void limitsToastsAndAllowsManualDismissal() throws Exception {
    AtomicReference<ToastHost> host = new AtomicReference<>();
    runOnJavaFx(
        () -> {
          ToastHost created = new ToastHost();
          host.set(created);
          for (int index = 0; index < 5; index++) created.show("Aviso " + index, "info");
          assertThat(created.getChildren()).hasSize(4);
          HBox toast = (HBox) created.getChildren().getFirst();
          ((Button) toast.getChildren().get(1)).fire();
          assertThat(created.getChildren()).hasSize(3);
        });
    assertThat(host.get()).isNotNull();
  }

  private static void runOnJavaFx(Runnable action) throws Exception {
    try {
      Platform.startup(() -> {});
    } catch (IllegalStateException alreadyStarted) {
      // The shared JavaFX toolkit may have been initialized by another test.
    }
    CountDownLatch completed = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Platform.runLater(
        () -> {
          try {
            action.run();
          } catch (Throwable throwable) {
            failure.set(throwable);
          } finally {
            completed.countDown();
          }
        });
    assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
    if (failure.get() != null) throw new AssertionError(failure.get());
  }
}

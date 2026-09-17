package com.ssh.tunnelmanager.ui.shell;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;

class StatusBadgeTest {
  @Test
  void usesDistinctReadableIconsForRuntimeStates() throws Exception {
    runOnJavaFx(
        () -> {
          assertThat(icon("OPEN")).isEqualTo("●");
          assertThat(icon("RECONNECTING")).isEqualTo("↻");
          assertThat(icon("ERROR")).isEqualTo("!");
          assertThat(icon("CLOSED")).isEqualTo("○");
        });
  }

  private static String icon(String state) {
    return ((Label) new StatusBadge(state, state).getChildren().getFirst()).getText();
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

package com.ssh.tunnelmanager.ui.shell;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssh.tunnelmanager.application.persistence.model.TagRecord;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.Test;

class TagSelectionControlTest {
  @Test
  void selectedTagsAreDisplayedAsRemovableChips() throws Exception {
    UUID selectedId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    AtomicReference<TagSelectionControl> control = new AtomicReference<>();

    runOnJavaFx(
        () ->
            control.set(
                new TagSelectionControl(
                    List.of(new TagRecord(selectedId, "producción"), new TagRecord(otherId, "db")),
                    Set.of(selectedId))));

    TagSelectionControl created = control.get();
    assertThat(created).isNotNull();
    HBox chip = (HBox) ((FlowPane) created.getChildren().get(1)).getChildren().get(0);
    assertThat(chip.getStyleClass()).contains("tag-chip");
    assertThat(((Button) chip.getChildren().get(1)).getAccessibleText())
        .isEqualTo("Quitar etiqueta producción");

    runOnJavaFx(() -> ((Button) chip.getChildren().get(1)).fire());

    assertThat(created.selectedTagIds()).isEmpty();
  }

  private static void runOnJavaFx(Runnable action) throws Exception {
    try {
      Platform.startup(() -> {});
    } catch (IllegalStateException alreadyStarted) {
      // Another JavaFX smoke test may have initialized the shared toolkit.
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

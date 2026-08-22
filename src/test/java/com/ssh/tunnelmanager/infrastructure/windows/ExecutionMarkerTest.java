package com.ssh.tunnelmanager.infrastructure.windows;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutionMarkerTest {
  @Test
  void normalCloseRemovesMarker(@TempDir Path directory) {
    Path marker = directory.resolve("run.marker");
    try (ExecutionMarker ignored = ExecutionMarker.open(marker)) {
      assertThat(ExecutionMarker.previousRunWasAbnormal(marker)).isTrue();
    }
    assertThat(ExecutionMarker.previousRunWasAbnormal(marker)).isFalse();
  }

  @Test
  void anUnclosedMarkerSignalsAnAbnormalPreviousRun(@TempDir Path directory) {
    Path marker = directory.resolve("run.marker");
    ExecutionMarker.open(marker);
    assertThat(ExecutionMarker.previousRunWasAbnormal(marker)).isTrue();
  }
}

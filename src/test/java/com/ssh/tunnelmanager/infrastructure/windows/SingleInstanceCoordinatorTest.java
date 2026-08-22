package com.ssh.tunnelmanager.infrastructure.windows;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SingleInstanceCoordinatorTest {
  @Test
  void secondCoordinatorSignalsThePrimary(@TempDir Path directory) throws Exception {
    CountDownLatch activated = new CountDownLatch(1);
    try (SingleInstanceCoordinator primary =
        SingleInstanceCoordinator.acquire(directory, activated::countDown)) {
      try (SingleInstanceCoordinator secondary =
          SingleInstanceCoordinator.acquire(directory, activated::countDown)) {
        assertThat(primary.isPrimary()).isTrue();
        assertThat(secondary.isPrimary()).isFalse();
        assertThat(activated.await(2, TimeUnit.SECONDS)).isTrue();
      }
    }
  }
}

package com.ssh.tunnelmanager.infrastructure.windows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ResumeDetectorTest {
  @Test
  void reportsAClockGapOnlyAfterTheThreshold() {
    AtomicLong clock = new AtomicLong(100);
    ResumeDetector detector = new ResumeDetector(Duration.ofNanos(10), clock::get);
    clock.set(109);
    assertThat(detector.poll()).isFalse();
    clock.set(119);
    assertThat(detector.poll()).isTrue();
  }
}

package com.ssh.tunnelmanager.infrastructure.windows;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Detects a long monotonic clock gap without adding a VPN-specific recovery policy. */
public final class ResumeDetector {
  private final long thresholdNanos;
  private final LongSupplier clock;
  private long lastNanos;

  public ResumeDetector(Duration threshold) {
    this(threshold, System::nanoTime);
  }

  public ResumeDetector(Duration threshold, LongSupplier clock) {
    if (threshold.isNegative() || threshold.isZero())
      throw new IllegalArgumentException("El umbral de resume debe ser positivo");
    this.clock = Objects.requireNonNull(clock);
    thresholdNanos = threshold.toNanos();
    lastNanos = clock.getAsLong();
  }

  public boolean poll() {
    long now = clock.getAsLong();
    long elapsed = now - lastNanos;
    lastNanos = now;
    return elapsed >= thresholdNanos;
  }
}

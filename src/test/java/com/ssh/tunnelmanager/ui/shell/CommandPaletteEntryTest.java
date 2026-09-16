package com.ssh.tunnelmanager.ui.shell;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CommandPaletteEntryTest {
  @Test
  void matchesTitleAndSubtitleWithoutBeingCaseSensitive() {
    CommandPaletteEntry entry =
        new CommandPaletteEntry("Nuevo túnel", "Túneles · Ctrl+N", () -> {});

    assertThat(entry.matches("TÚNEL")).isTrue();
    assertThat(entry.matches("ctrl+n")).isTrue();
    assertThat(entry.matches("host")).isFalse();
  }

  @Test
  void keepsTheActionOwnedByTheCaller() {
    AtomicBoolean invoked = new AtomicBoolean();
    CommandPaletteEntry entry =
        new CommandPaletteEntry("Acción", "Prueba", () -> invoked.set(true));

    entry.action().run();

    assertThat(invoked).isTrue();
  }
}

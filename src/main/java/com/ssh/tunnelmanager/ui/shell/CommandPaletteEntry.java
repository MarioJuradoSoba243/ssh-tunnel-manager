package com.ssh.tunnelmanager.ui.shell;

import java.util.Locale;
import java.util.Objects;

public record CommandPaletteEntry(String title, String subtitle, Runnable action) {
  public CommandPaletteEntry {
    Objects.requireNonNull(title);
    Objects.requireNonNull(subtitle);
    Objects.requireNonNull(action);
  }

  public boolean matches(String query) {
    String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    return normalized.isEmpty()
        || title.toLowerCase(Locale.ROOT).contains(normalized)
        || subtitle.toLowerCase(Locale.ROOT).contains(normalized);
  }
}

package com.ssh.tunnelmanager.ui.theme;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class WindowsSystemThemeProvider implements SystemThemeProvider {
  @Override
  public ThemePreference currentTheme() {
    if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
      return ThemePreference.LIGHT;
    }
    try {
      Process process =
          new ProcessBuilder(
                  "reg",
                  "query",
                  "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                  "/v",
                  "AppsUseLightTheme")
              .redirectErrorStream(true)
              .start();
      if (!process.waitFor(2, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return ThemePreference.LIGHT;
      }
      String output = new String(process.getInputStream().readAllBytes());
      return output.matches("(?s).*0x0.*") ? ThemePreference.DARK : ThemePreference.LIGHT;
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
      return ThemePreference.LIGHT;
    }
  }
}

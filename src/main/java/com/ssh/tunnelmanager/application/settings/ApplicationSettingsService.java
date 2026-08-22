package com.ssh.tunnelmanager.application.settings;

import com.ssh.tunnelmanager.application.persistence.ApplicationSettingsRepository;
import java.util.Locale;
import java.util.Map;

public final class ApplicationSettingsService {
  public static final String THEME_KEY = "ui.theme";
  public static final String NOTIFICATIONS_KEY = "ui.notifications.enabled";
  public static final String LOGGING_LEVEL_KEY = "logging.level";

  private final ApplicationSettingsRepository settings;
  private final ShortcutRegistry shortcuts;

  public ApplicationSettingsService(
      ApplicationSettingsRepository settings, ShortcutRegistry shortcuts) {
    this.settings = settings;
    this.shortcuts = shortcuts;
  }

  public String theme() {
    String theme = settings.findValue(THEME_KEY).orElse("SYSTEM").toUpperCase(Locale.ROOT);
    return SetOfThemes.contains(theme) ? theme : "SYSTEM";
  }

  public void setTheme(String theme) {
    String normalized = theme.toUpperCase(Locale.ROOT);
    if (!SetOfThemes.contains(normalized)) {
      throw new IllegalArgumentException("Tema no soportado: " + theme);
    }
    settings.put(THEME_KEY, normalized);
  }

  public boolean notificationsEnabled() {
    return settings.findValue(NOTIFICATIONS_KEY).map(Boolean::parseBoolean).orElse(true);
  }

  public void setNotificationsEnabled(boolean enabled) {
    settings.put(NOTIFICATIONS_KEY, Boolean.toString(enabled));
  }

  public String loggingLevel() {
    String level = settings.findValue(LOGGING_LEVEL_KEY).orElse("INFO").toUpperCase(Locale.ROOT);
    return SetOfLevels.contains(level) ? level : "INFO";
  }

  public void setLoggingLevel(String level) {
    String normalized = level.toUpperCase(Locale.ROOT);
    if (!SetOfLevels.contains(normalized)) {
      throw new IllegalArgumentException("Nivel de logging no soportado: " + level);
    }
    settings.put(LOGGING_LEVEL_KEY, normalized);
  }

  public Map<ShortcutAction, String> shortcuts() {
    return shortcuts.load();
  }

  public void setShortcuts(Map<ShortcutAction, String> values) {
    shortcuts.replace(values);
  }

  private static final class SetOfThemes {
    private static boolean contains(String value) {
      return value.equals("LIGHT") || value.equals("DARK") || value.equals("SYSTEM");
    }
  }

  private static final class SetOfLevels {
    private static boolean contains(String value) {
      return value.equals("TRACE")
          || value.equals("DEBUG")
          || value.equals("INFO")
          || value.equals("WARN")
          || value.equals("ERROR");
    }
  }
}

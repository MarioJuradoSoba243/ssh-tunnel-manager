package com.ssh.tunnelmanager.ui.theme;

import java.util.Objects;
import javafx.scene.Scene;

public final class ThemeManager {
  private static final String BASE_STYLESHEET = "/com/ssh/tunnelmanager/ui/theme/base.css";
  private static final String LIGHT_STYLESHEET = "/com/ssh/tunnelmanager/ui/theme/light.css";
  private static final String DARK_STYLESHEET = "/com/ssh/tunnelmanager/ui/theme/dark.css";

  private final SystemThemeProvider systemThemeProvider;

  public ThemeManager(SystemThemeProvider systemThemeProvider) {
    this.systemThemeProvider = Objects.requireNonNull(systemThemeProvider);
  }

  public void apply(Scene scene, ThemePreference preference) {
    Objects.requireNonNull(scene);
    Objects.requireNonNull(preference);

    ThemePreference effectivePreference = preference;
    if (preference == ThemePreference.SYSTEM) {
      effectivePreference = systemThemeProvider.currentTheme();
    }
    if (effectivePreference == ThemePreference.SYSTEM) {
      effectivePreference = ThemePreference.LIGHT;
    }

    scene.getStylesheets().setAll(BASE_STYLESHEET);
    scene
        .getStylesheets()
        .add(effectivePreference == ThemePreference.DARK ? DARK_STYLESHEET : LIGHT_STYLESHEET);
  }
}

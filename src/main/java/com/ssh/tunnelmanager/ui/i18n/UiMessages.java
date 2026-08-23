package com.ssh.tunnelmanager.ui.i18n;

import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

/** Presentation-only access to the v1 Spanish message catalog. */
public final class UiMessages {
  private static final String BASE_NAME = "com.ssh.tunnelmanager.ui.i18n.messages";
  private static final Locale V1_LOCALE = Locale.forLanguageTag("es");
  private static final ResourceBundle SPANISH = ResourceBundle.getBundle(BASE_NAME, V1_LOCALE);

  private UiMessages() {}

  public static String get(String key) {
    return SPANISH.getString(Objects.requireNonNull(key));
  }

  public static ResourceBundle spanishBundle() {
    return SPANISH;
  }
}

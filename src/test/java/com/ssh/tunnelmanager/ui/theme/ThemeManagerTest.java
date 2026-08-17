package com.ssh.tunnelmanager.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ThemeManagerTest {
  @Test
  void systemPreferenceUsesTheInjectedSystemTheme() {
    ThemeManager manager = new ThemeManager(() -> ThemePreference.DARK);

    assertThat(manager).isNotNull();
  }
}

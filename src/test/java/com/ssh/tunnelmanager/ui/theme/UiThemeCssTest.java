package com.ssh.tunnelmanager.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UiThemeCssTest {
  @Test
  void bothThemesDefineTheContrastTokensUsedBySharedControls() throws IOException {
    String light = read("/com/ssh/tunnelmanager/ui/theme/light.css");
    String dark = read("/com/ssh/tunnelmanager/ui/theme/dark.css");

    for (String stylesheet : new String[] {light, dark}) {
      assertThat(stylesheet)
          .contains(
              "-app-border",
              "-app-placeholder",
              "-app-hover",
              "-app-focus",
              "-app-selection",
              "-app-disabled-text",
              "-fx-text-inner-color",
              "-fx-control-inner-background-alt");
    }
  }

  @Test
  void sharedStylesCoverTheControlsThatPreviouslyInheritedUnreadableDefaults() throws IOException {
    String base = read("/com/ssh/tunnelmanager/ui/theme/base.css");

    assertThat(base)
        .contains(
            ".button:disabled",
            ".text-input:disabled",
            ".combo-box-base:disabled",
            ".table-row-cell:selected",
            ".combo-box-popup .list-cell:hover",
            ".dialog-pane",
            ".filter-bar",
            ".action-bar");
  }

  private static String read(String resource) throws IOException {
    try (InputStream stream = UiThemeCssTest.class.getResourceAsStream(resource)) {
      assertThat(stream).as("stylesheet %s", resource).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}

package com.ssh.tunnelmanager.ui.shell;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SidebarCellStyleTest {
  @Test
  void emptySidebarCellsAreExplicitlyNonInteractive() throws IOException {
    String css = read("/com/ssh/tunnelmanager/ui/theme/base.css");

    assertThat(css)
        .contains(
            ".navigation-pane .list-cell:empty",
            ".navigation-pane .list-cell:empty:hover",
            ".navigation-pane .list-cell:empty:focused",
            "-fx-background-color: transparent");
  }

  private static String read(String resource) throws IOException {
    try (InputStream stream = SidebarCellStyleTest.class.getResourceAsStream(resource)) {
      assertThat(stream).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}

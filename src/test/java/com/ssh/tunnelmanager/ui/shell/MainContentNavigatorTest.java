package com.ssh.tunnelmanager.ui.shell;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;

class MainContentNavigatorTest {
  @Test
  void replacingAnEditorWithTheCachedTunnelViewDoesNotStackViews() {
    VBox container = new VBox();
    MainContentNavigator navigator = new MainContentNavigator(container);
    Pane tunnelView = new Pane();
    Pane editor = new Pane();

    navigator.replace(tunnelView);
    navigator.replace(editor);
    navigator.replace(tunnelView);

    assertThat(container.getChildren()).containsExactly(tunnelView);
  }

  @Test
  void replacingAfterCancelLeavesOnlyThePreviousScreen() {
    VBox container = new VBox();
    MainContentNavigator navigator = new MainContentNavigator(container);
    Pane tunnelView = new Pane();
    Pane editor = new Pane();

    for (int attempt = 0; attempt < 5; attempt++) {
      navigator.replace(tunnelView);
      navigator.replace(editor);
      navigator.replace(tunnelView);
    }

    assertThat(container.getChildren()).doesNotContain(editor);
    assertThat(container.getChildren()).hasSize(1);
  }
}

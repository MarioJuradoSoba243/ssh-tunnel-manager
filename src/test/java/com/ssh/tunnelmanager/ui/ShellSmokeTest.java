package com.ssh.tunnelmanager.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssh.tunnelmanager.application.bootstrap.ApplicationBootstrap;
import com.ssh.tunnelmanager.application.bootstrap.ApplicationInfo;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import org.junit.jupiter.api.Test;

class ShellSmokeTest {
  @Test
  void createsTheInitialSceneWithoutStartingAnySshConnection() throws Exception {
    CountDownLatch toolkitStarted = new CountDownLatch(1);
    Platform.startup(toolkitStarted::countDown);
    assertThat(toolkitStarted.await(5, TimeUnit.SECONDS)).isTrue();

    SshTunnelManagerApplication application =
        new SshTunnelManagerApplication(
            new ApplicationBootstrap(new ApplicationInfo("smoke-test")));

    Scene scene = application.createScene();

    assertThat(scene.getWidth()).isEqualTo(1024);
    assertThat(scene.getHeight()).isEqualTo(700);
    assertThat(scene.getRoot().getStyleClass()).contains("app-shell");
    assertThat(scene.getStylesheets())
        .containsExactly(
            "/com/ssh/tunnelmanager/ui/theme/base.css",
            "/com/ssh/tunnelmanager/ui/theme/light.css");
  }
}

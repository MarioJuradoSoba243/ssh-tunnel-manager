package com.sshtunnelmanager.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.sshtunnelmanager.ApplicationBootstrap;
import javafx.application.Application;
import org.junit.jupiter.api.Test;

class JavaFxStartupSmokeIT {
  @Test
  void applicationEntryPointAndShellResourcesAreAvailable() {
    var controller = new ApplicationBootstrap().createShellController();

    assertThat(Application.class).isAssignableFrom(SshTunnelManagerApplication.class);
    assertThat(controller.applicationTitle()).isEqualTo("SSH Tunnel Manager");
    assertThat(
            SshTunnelManagerApplication.class.getResource(
                "/com/sshtunnelmanager/ui/shell/shell.fxml"))
        .isNotNull();
    assertThat(
            SshTunnelManagerApplication.class.getResource(
                "/com/sshtunnelmanager/ui/styles/base.css"))
        .isNotNull();
  }
}

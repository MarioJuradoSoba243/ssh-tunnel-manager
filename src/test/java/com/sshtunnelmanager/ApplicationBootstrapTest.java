package com.sshtunnelmanager;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApplicationBootstrapTest {
  @Test
  void createsShellControllerUsingTheExplicitObjectGraph() {
    var controller = new ApplicationBootstrap().createShellController();

    assertThat(controller.applicationTitle()).isEqualTo("SSH Tunnel Manager");
  }
}

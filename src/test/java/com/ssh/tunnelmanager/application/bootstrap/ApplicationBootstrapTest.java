package com.ssh.tunnelmanager.application.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssh.tunnelmanager.application.shell.ShellViewModel;
import org.junit.jupiter.api.Test;

class ApplicationBootstrapTest {
  @Test
  void createsTheInitialNavigationThroughConstructorInjectedCompositionRoot() {
    ApplicationBootstrap bootstrap = new ApplicationBootstrap(new ApplicationInfo("test-version"));

    ShellViewModel viewModel = bootstrap.createShellViewModel();

    assertThat(bootstrap.applicationInfo().version()).isEqualTo("test-version");
    assertThat(viewModel.navigationItems())
        .extracting(item -> item.label())
        .containsExactly("Inicio", "Túneles", "Grupos", "Hosts SSH", "Organización", "Ajustes");
  }
}

package com.ssh.tunnelmanager.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class ApplicationLauncherTest {
  @Test
  void exposesAPlainVoidMainMethodForJavafxLaunchers() throws NoSuchMethodException {
    Method main = ApplicationLauncher.class.getMethod("main", String[].class);

    assertThat(Modifier.isStatic(main.getModifiers())).isTrue();
    assertThat(main.getReturnType()).isEqualTo(void.class);
  }
}

package com.ssh.tunnelmanager.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UiMessagesTest {
  @Test
  void v1SpanishCatalogContainsStableShellAndEditorMessages() {
    assertThat(UiMessages.get("navigation.home")).isEqualTo("Inicio");
    assertThat(UiMessages.get("tunnel.editor.tab.forwarding")).isEqualTo("Reenvío de puerto local");
    assertThat(UiMessages.get("tunnel.editor.password")).isEqualTo("Contraseña");
    assertThat(UiMessages.spanishBundle().getLocale().getLanguage()).isEqualTo("es");
  }
}

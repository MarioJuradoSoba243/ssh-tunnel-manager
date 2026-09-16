package com.ssh.tunnelmanager.ui.shell;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TunnelViewStateTest {
  @Test
  void keepsTheLoadedCollectionWhenTheShellReusesTheTunnelView() {
    TunnelViewState state = new TunnelViewState();
    TunnelRecord tunnel = tunnel("backend");
    state.replaceAll(java.util.List.of(tunnel));
    state.filteredRows().setPredicate(value -> value.name().contains("back"));

    state.replaceAll(java.util.List.of(tunnel));

    assertThat(state.rows()).containsExactly(tunnel);
    assertThat(state.filteredRows()).containsExactly(tunnel);
  }

  private static TunnelRecord tunnel(String name) {
    return new TunnelRecord(
        UUID.randomUUID(),
        name,
        "127.0.0.1",
        8080,
        "target",
        80,
        UUID.randomUUID(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        Set.of(),
        Map.of());
  }
}

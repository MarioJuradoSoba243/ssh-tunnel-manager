package com.ssh.tunnelmanager.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssh.tunnelmanager.domain.credential.Credential;
import com.ssh.tunnelmanager.domain.host.SshHost;
import com.ssh.tunnelmanager.domain.tunnel.SshRoute;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TunnelDomainTest {
  @Test
  void rejectsMoreThanTwoJumps() {
    Credential credential = new Credential("user", "password");
    SshHost first = host("first", credential);
    SshHost second = host("second", credential);
    SshHost third = host("third", credential);
    SshHost finalHost = host("final", credential);

    assertThatThrownBy(() -> new SshRoute(List.of(first, second, third), finalHost))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsInvalidPortsAndRepeatedFinalHost() {
    Credential credential = new Credential("user", "password");
    SshHost first = host("first", credential);

    assertThatThrownBy(() -> new SshHost(UUID.randomUUID(), "host", "localhost", 0, credential))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SshRoute(List.of(first), first))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static SshHost host(String name, Credential credential) {
    return new SshHost(UUID.randomUUID(), name, "localhost", 22, credential);
  }
}

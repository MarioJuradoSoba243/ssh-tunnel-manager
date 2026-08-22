package com.ssh.tunnelmanager.domain.tunnel;

import com.ssh.tunnelmanager.domain.host.SshHost;
import java.util.List;
import java.util.Objects;

public record SshRoute(List<SshHost> jumps, SshHost finalHost) {
  public SshRoute {
    Objects.requireNonNull(jumps, "jumps");
    jumps = List.copyOf(jumps);
    if (jumps.size() > 2)
      throw new IllegalArgumentException("Una ruta admite como máximo dos jumps");
    Objects.requireNonNull(finalHost, "finalHost");
    if (jumps.stream().anyMatch(host -> host.id().equals(finalHost.id()))) {
      throw new IllegalArgumentException("El host final no puede repetirse como jump");
    }
    if (jumps.size() == 2 && jumps.get(0).id().equals(jumps.get(1).id())) {
      throw new IllegalArgumentException("Los jumps deben ser distintos");
    }
  }

  public List<SshHost> hostsInOrder() {
    var hosts = new java.util.ArrayList<SshHost>(jumps);
    hosts.add(finalHost);
    return List.copyOf(hosts);
  }
}

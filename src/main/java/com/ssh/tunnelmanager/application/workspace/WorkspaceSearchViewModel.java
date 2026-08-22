package com.ssh.tunnelmanager.application.workspace;

import com.ssh.tunnelmanager.application.persistence.SshHostRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelGroupRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.persistence.model.SshHostRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelGroupRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Keeps a local workspace index so search does not issue a SQLite query per keystroke. */
public final class WorkspaceSearchViewModel {
  private final TunnelRepository tunnels;
  private final TunnelGroupRepository groups;
  private final SshHostRepository hosts;
  private volatile Index index = new Index(List.of(), List.of(), List.of());

  public WorkspaceSearchViewModel(
      TunnelRepository tunnels, TunnelGroupRepository groups, SshHostRepository hosts) {
    this.tunnels = tunnels;
    this.groups = groups;
    this.hosts = hosts;
  }

  public void refresh() {
    index = new Index(tunnels.findAll(), groups.findAll(), hosts.findAll());
  }

  public List<SearchResult> search(String query) {
    String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) return List.of();
    List<SearchResult> results = new ArrayList<>();
    for (TunnelRecord tunnel : index.tunnels()) {
      if (contains(normalized, tunnel.name(), tunnel.destinationHost())) {
        results.add(
            new SearchResult(
                SearchResult.Kind.TUNNEL,
                tunnel.id(),
                tunnel.name(),
                tunnel.bindAddress()
                    + ":"
                    + tunnel.localPort()
                    + " → "
                    + tunnel.destinationHost()));
      }
    }
    for (TunnelGroupRecord group : index.groups()) {
      if (contains(normalized, group.name())) {
        results.add(
            new SearchResult(
                SearchResult.Kind.GROUP,
                group.id(),
                group.name(),
                group.tunnelIds().size() + " túneles"));
      }
    }
    for (SshHostRecord host : index.hosts()) {
      if (contains(normalized, host.name(), host.hostname())) {
        results.add(
            new SearchResult(
                SearchResult.Kind.HOST,
                host.id(),
                host.name(),
                host.hostname() + ":" + host.sshPort()));
      }
    }
    return List.copyOf(results);
  }

  public void upsertTunnel(TunnelRecord tunnel) {
    List<TunnelRecord> values = new ArrayList<>(index.tunnels());
    values.removeIf(existing -> existing.id().equals(tunnel.id()));
    values.add(tunnel);
    index = new Index(values, index.groups(), index.hosts());
  }

  public void removeTunnel(UUID id) {
    index =
        new Index(
            index.tunnels().stream().filter(item -> !item.id().equals(id)).toList(),
            index.groups(),
            index.hosts());
  }

  private static boolean contains(String query, String... values) {
    for (String value : values) {
      if (value != null && value.toLowerCase(Locale.ROOT).contains(query)) return true;
    }
    return false;
  }

  private record Index(
      List<TunnelRecord> tunnels, List<TunnelGroupRecord> groups, List<SshHostRecord> hosts) {
    private Index {
      tunnels = List.copyOf(tunnels);
      groups = List.copyOf(groups);
      hosts = List.copyOf(hosts);
    }
  }
}

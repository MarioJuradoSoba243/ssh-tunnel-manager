package com.ssh.tunnelmanager.application.workspace;

import com.ssh.tunnelmanager.application.persistence.RecentItemRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelGroupRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.persistence.model.TunnelGroupRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager;
import com.ssh.tunnelmanager.domain.tunnel.ActualState;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class HubViewModel {
  private final TunnelRepository tunnels;
  private final TunnelGroupRepository groups;
  private final RecentItemRepository recents;
  private final TunnelRuntimeManager runtime;
  private volatile HubSnapshot snapshot =
      new HubSnapshot(List.of(), List.of(), List.of(), 0, 0, 0, 0);

  public HubViewModel(
      TunnelRepository tunnels,
      TunnelGroupRepository groups,
      RecentItemRepository recents,
      TunnelRuntimeManager runtime) {
    this.tunnels = tunnels;
    this.groups = groups;
    this.recents = recents;
    this.runtime = runtime;
  }

  public void refresh() {
    List<TunnelRecord> tunnelValues = tunnels.findAll();
    List<TunnelGroupRecord> groupValues = groups.findAll();
    Map<UUID, TunnelRecord> tunnelsById =
        tunnelValues.stream()
            .collect(Collectors.toUnmodifiableMap(TunnelRecord::id, Function.identity()));
    Map<UUID, TunnelGroupRecord> groupsById =
        groupValues.stream()
            .collect(Collectors.toUnmodifiableMap(TunnelGroupRecord::id, Function.identity()));
    List<HubItem> favorites =
        tunnelValues.stream()
            .filter(TunnelRecord::favorite)
            .map(tunnel -> new HubItem(SearchResult.Kind.TUNNEL, tunnel.id(), tunnel.name()))
            .collect(Collectors.toCollection(java.util.ArrayList::new));
    favorites.addAll(
        groupValues.stream()
            .filter(TunnelGroupRecord::favorite)
            .map(group -> new HubItem(SearchResult.Kind.GROUP, group.id(), group.name()))
            .toList());
    List<HubItem> recentItems =
        recents.findRecent(8).stream()
            .map(
                item ->
                    item.itemType()
                            == com.ssh.tunnelmanager.application.persistence.model.RecentItemType
                                .TUNNEL
                        ? tunnelsById.get(item.itemId())
                        : groupsById.get(item.itemId()))
            .filter(java.util.Objects::nonNull)
            .map(
                value ->
                    value instanceof TunnelRecord tunnel
                        ? new HubItem(SearchResult.Kind.TUNNEL, tunnel.id(), tunnel.name())
                        : new HubItem(
                            SearchResult.Kind.GROUP,
                            ((TunnelGroupRecord) value).id(),
                            ((TunnelGroupRecord) value).name()))
            .toList();
    List<HubItem> active =
        runtime.runtimes().values().stream()
            .filter(item -> item.actualState() != ActualState.CLOSED)
            .map(
                item ->
                    new HubItem(
                        SearchResult.Kind.TUNNEL,
                        item.tunnelId(),
                        tunnelsById.getOrDefault(item.tunnelId(), null) == null
                            ? item.tunnelId().toString()
                            : tunnelsById.get(item.tunnelId()).name()))
            .toList();
    long open =
        runtime.runtimes().values().stream()
            .filter(item -> item.actualState() == ActualState.OPEN)
            .count();
    long reconnecting =
        runtime.runtimes().values().stream()
            .filter(item -> item.actualState() == ActualState.RECONNECTING)
            .count();
    long errors =
        runtime.runtimes().values().stream()
            .filter(item -> item.actualState() == ActualState.ERROR)
            .count();
    snapshot =
        new HubSnapshot(
            favorites,
            recentItems,
            active,
            Math.toIntExact(open),
            Math.toIntExact(reconnecting),
            Math.toIntExact(errors),
            tunnelValues.size());
  }

  public HubSnapshot snapshot() {
    return snapshot;
  }

  public record HubSnapshot(
      List<HubItem> favorites,
      List<HubItem> recent,
      List<HubItem> active,
      int openTunnels,
      int reconnectingTunnels,
      int errorTunnels,
      int configuredTunnels) {
    public HubSnapshot {
      favorites = List.copyOf(favorites);
      recent = List.copyOf(recent);
      active = List.copyOf(active);
    }
  }

  public record HubItem(SearchResult.Kind kind, UUID id, String name) {}
}

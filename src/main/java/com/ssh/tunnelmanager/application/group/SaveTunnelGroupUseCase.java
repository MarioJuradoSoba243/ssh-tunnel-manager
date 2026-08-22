package com.ssh.tunnelmanager.application.group;

import com.ssh.tunnelmanager.application.persistence.TunnelGroupRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.persistence.model.TunnelGroupRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import com.ssh.tunnelmanager.domain.group.TunnelGroup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SaveTunnelGroupUseCase {
  private final TunnelGroupRepository groups;
  private final TunnelRepository tunnels;

  public SaveTunnelGroupUseCase(TunnelGroupRepository groups, TunnelRepository tunnels) {
    this.groups = groups;
    this.tunnels = tunnels;
  }

  public void execute(TunnelGroup group) {
    execute(group, false);
  }

  public void execute(TunnelGroup group, boolean confirmEndpointConflicts) {
    if (groups.findAll().stream()
        .anyMatch(
            existing ->
                existing.name().equalsIgnoreCase(group.name())
                    && !existing.id().equals(group.id())))
      throw new GroupNameConflictException(group.name());
    List<GroupEndpointConflict> conflicts = findConflicts(group);
    if (!confirmEndpointConflicts && !conflicts.isEmpty())
      throw new GroupConflictException(conflicts);
    groups.save(
        new TunnelGroupRecord(
            group.id(),
            group.name(),
            group.projectId(),
            group.environmentId(),
            group.favorite(),
            group.tunnelIds(),
            group.tagIds()));
  }

  public List<GroupEndpointConflict> findConflicts(TunnelGroup group) {
    List<TunnelRecord> members =
        group.tunnelIds().stream()
            .map(
                id ->
                    tunnels
                        .findById(id)
                        .orElseThrow(
                            () -> new IllegalArgumentException("Túnel no encontrado: " + id)))
            .sorted(Comparator.comparing(TunnelRecord::name))
            .toList();
    List<GroupEndpointConflict> conflicts = new ArrayList<>();
    for (int first = 0; first < members.size(); first++) {
      TunnelRecord left = members.get(first);
      for (int second = first + 1; second < members.size(); second++) {
        TunnelRecord right = members.get(second);
        if (left.localPort() == right.localPort() && left.bindAddress().equals(right.bindAddress()))
          conflicts.add(
              new GroupEndpointConflict(
                  left.id(),
                  left.name(),
                  right.id(),
                  right.name(),
                  left.bindAddress(),
                  left.localPort()));
      }
    }
    return List.copyOf(conflicts);
  }
}

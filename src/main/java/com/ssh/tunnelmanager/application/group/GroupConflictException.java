package com.ssh.tunnelmanager.application.group;

import java.util.List;

public final class GroupConflictException extends IllegalStateException {
  private final List<GroupEndpointConflict> conflicts;

  public GroupConflictException(List<GroupEndpointConflict> conflicts) {
    super(
        "El grupo contiene endpoints locales en conflicto: "
            + conflicts.stream().map(GroupEndpointConflict::endpoint).toList());
    this.conflicts = List.copyOf(conflicts);
  }

  public List<GroupEndpointConflict> conflicts() {
    return conflicts;
  }
}

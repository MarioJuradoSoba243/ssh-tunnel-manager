package com.ssh.tunnelmanager.application.group;

import com.ssh.tunnelmanager.application.persistence.TunnelGroupRepository;
import com.ssh.tunnelmanager.application.persistence.model.TunnelGroupRecord;
import java.util.List;
import java.util.UUID;

public final class GroupListViewModel {
  private final TunnelGroupRepository groups;
  private final GroupStatusService statuses;
  private final SaveTunnelGroupUseCase save;
  private final DeleteTunnelGroupUseCase delete;
  private final DuplicateTunnelGroupUseCase duplicate;
  private final StartGroupUseCase start;
  private final StopGroupUseCase stop;

  public GroupListViewModel(
      TunnelGroupRepository groups,
      GroupStatusService statuses,
      SaveTunnelGroupUseCase save,
      DeleteTunnelGroupUseCase delete,
      DuplicateTunnelGroupUseCase duplicate,
      StartGroupUseCase start,
      StopGroupUseCase stop) {
    this.groups = groups;
    this.statuses = statuses;
    this.save = save;
    this.delete = delete;
    this.duplicate = duplicate;
    this.start = start;
    this.stop = stop;
  }

  public List<TunnelGroupRecord> groups() {
    return groups.findAll();
  }

  public GroupRuntimeStatus status(UUID groupId) {
    return statuses.status(groupId);
  }

  public void save(com.ssh.tunnelmanager.domain.group.TunnelGroup group, boolean confirmConflicts) {
    save.execute(group, confirmConflicts);
  }

  public void delete(UUID groupId) {
    delete.execute(groupId);
  }

  public TunnelGroupRecord duplicate(UUID groupId, String name) {
    return duplicate.execute(groupId, name);
  }

  public GroupStartOperation start(UUID groupId) {
    return start.execute(groupId);
  }

  public UUID stop(UUID groupId) {
    return stop.execute(groupId);
  }
}

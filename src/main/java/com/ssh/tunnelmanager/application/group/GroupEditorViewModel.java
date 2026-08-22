package com.ssh.tunnelmanager.application.group;

import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.persistence.model.TunnelGroupRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import com.ssh.tunnelmanager.domain.group.TunnelGroup;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class GroupEditorViewModel {
  private final TunnelRepository tunnels;
  private final SaveTunnelGroupUseCase save;

  public GroupEditorViewModel(TunnelRepository tunnels, SaveTunnelGroupUseCase save) {
    this.tunnels = tunnels;
    this.save = save;
  }

  public List<TunnelRecord> availableTunnels() {
    return tunnels.findAll();
  }

  public Form fromRecord(TunnelGroupRecord record) {
    return new Form(record.id(), record.name(), record.tunnelIds());
  }

  public List<GroupEndpointConflict> conflicts(Form form) {
    return save.findConflicts(toDomain(form));
  }

  public void save(Form form, boolean confirmConflicts) {
    save.execute(toDomain(form), confirmConflicts);
  }

  private static TunnelGroup toDomain(Form form) {
    return new TunnelGroup(form.id(), form.name(), form.tunnelIds());
  }

  public record Form(UUID id, String name, Set<UUID> tunnelIds) {
    public Form {
      id = id == null ? UUID.randomUUID() : id;
      tunnelIds = Set.copyOf(tunnelIds == null ? Set.of() : tunnelIds);
    }
  }
}

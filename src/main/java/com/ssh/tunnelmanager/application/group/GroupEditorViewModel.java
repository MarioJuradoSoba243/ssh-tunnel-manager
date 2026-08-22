package com.ssh.tunnelmanager.application.group;

import com.ssh.tunnelmanager.application.persistence.EnvironmentRepository;
import com.ssh.tunnelmanager.application.persistence.ProjectRepository;
import com.ssh.tunnelmanager.application.persistence.TagRepository;
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
  private final ProjectRepository projects;
  private final EnvironmentRepository environments;
  private final TagRepository tags;

  public GroupEditorViewModel(TunnelRepository tunnels, SaveTunnelGroupUseCase save) {
    this(tunnels, save, null, null, null);
  }

  public GroupEditorViewModel(
      TunnelRepository tunnels,
      SaveTunnelGroupUseCase save,
      ProjectRepository projects,
      EnvironmentRepository environments,
      TagRepository tags) {
    this.tunnels = tunnels;
    this.save = save;
    this.projects = projects;
    this.environments = environments;
    this.tags = tags;
  }

  public List<TunnelRecord> availableTunnels() {
    return tunnels.findAll();
  }

  public List<com.ssh.tunnelmanager.application.persistence.model.ProjectRecord> projects() {
    return projects == null ? List.of() : projects.findAll();
  }

  public List<com.ssh.tunnelmanager.application.persistence.model.EnvironmentRecord>
      environments() {
    return environments == null ? List.of() : environments.findAll();
  }

  public List<com.ssh.tunnelmanager.application.persistence.model.TagRecord> tags() {
    return tags == null ? List.of() : tags.findAll();
  }

  public Form fromRecord(TunnelGroupRecord record) {
    return new Form(
        record.id(),
        record.name(),
        record.tunnelIds(),
        record.projectId(),
        record.environmentId(),
        record.favorite(),
        record.tagIds());
  }

  public List<GroupEndpointConflict> conflicts(Form form) {
    return save.findConflicts(toDomain(form));
  }

  public void save(Form form, boolean confirmConflicts) {
    save.execute(toDomain(form), confirmConflicts);
  }

  private static TunnelGroup toDomain(Form form) {
    return new TunnelGroup(
        form.id(),
        form.name(),
        form.tunnelIds(),
        form.projectId(),
        form.environmentId(),
        form.favorite(),
        form.tagIds());
  }

  public record Form(
      UUID id,
      String name,
      Set<UUID> tunnelIds,
      UUID projectId,
      UUID environmentId,
      boolean favorite,
      Set<UUID> tagIds) {
    public Form(UUID id, String name, Set<UUID> tunnelIds) {
      this(id, name, tunnelIds, null, null, false, Set.of());
    }

    public Form {
      id = id == null ? UUID.randomUUID() : id;
      tunnelIds = Set.copyOf(tunnelIds == null ? Set.of() : tunnelIds);
      tagIds = Set.copyOf(tagIds == null ? Set.of() : tagIds);
    }
  }
}

package com.ssh.tunnelmanager.application.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssh.tunnelmanager.application.persistence.EnvironmentRepository;
import com.ssh.tunnelmanager.application.persistence.ProjectRepository;
import com.ssh.tunnelmanager.application.persistence.TagRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelGroupRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.persistence.model.EnvironmentRecord;
import com.ssh.tunnelmanager.application.persistence.model.ProjectRecord;
import com.ssh.tunnelmanager.application.persistence.model.TagRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelGroupRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import com.ssh.tunnelmanager.domain.organization.Environment;
import com.ssh.tunnelmanager.domain.organization.Project;
import com.ssh.tunnelmanager.domain.organization.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationUseCaseTest {
  @Test
  void savesNamesCaseInsensitivelyAndRequiresConfirmationForUsedProject() {
    InMemoryProjects projects = new InMemoryProjects();
    InMemoryTunnels tunnels = new InMemoryTunnels();
    InMemoryGroups groups = new InMemoryGroups();
    UUID projectId = UUID.randomUUID();
    projects.save(new ProjectRecord(projectId, "Backend"));
    tunnels.save(tunnel(projectId));
    ClassificationUsageService usage = new ClassificationUsageService(tunnels, groups);

    assertThatThrownBy(
            () ->
                new SaveProjectUseCase(projects).execute(new Project(UUID.randomUUID(), "backend")))
        .isInstanceOf(IllegalArgumentException.class);
    DeleteProjectUseCase delete = new DeleteProjectUseCase(projects, usage);
    assertThatThrownBy(() -> delete.execute(projectId, false))
        .isInstanceOf(ClassificationInUseException.class)
        .satisfies(
            error ->
                assertThat(((ClassificationInUseException) error).usage().tunnelCount()).isOne());
    delete.execute(projectId, true);
    assertThat(projects.findById(projectId)).isEmpty();
  }

  @Test
  void supportsEnvironmentAndTagModelsAndReportsGroupUsage() {
    InMemoryEnvironments environments = new InMemoryEnvironments();
    InMemoryTags tags = new InMemoryTags();
    InMemoryTunnels tunnels = new InMemoryTunnels();
    InMemoryGroups groups = new InMemoryGroups();
    UUID environmentId = UUID.randomUUID();
    UUID tagId = UUID.randomUUID();
    environments.save(new EnvironmentRecord(environmentId, "dev"));
    tags.save(new TagRecord(tagId, "ssh"));
    groups.save(
        new TunnelGroupRecord(
            UUID.randomUUID(), "ops", null, environmentId, false, Set.of(), Set.of(tagId)));
    new SaveEnvironmentUseCase(environments).execute(new Environment(environmentId, "development"));
    new SaveTagUseCase(tags).execute(new Tag(tagId, "secure"));

    ClassificationUsageService usage = new ClassificationUsageService(tunnels, groups);
    assertThat(usage.environment(environmentId).groupCount()).isOne();
    assertThat(usage.tag(tagId).groupCount()).isOne();
  }

  private static TunnelRecord tunnel(UUID projectId) {
    return new TunnelRecord(
        UUID.randomUUID(),
        "tunnel",
        "127.0.0.1",
        10000,
        "target",
        80,
        UUID.randomUUID(),
        null,
        null,
        null,
        null,
        null,
        projectId,
        null,
        false,
        Set.of(),
        java.util.Map.of());
  }

  private static final class InMemoryProjects implements ProjectRepository {
    private final List<ProjectRecord> values = new ArrayList<>();

    public Optional<ProjectRecord> findById(UUID id) {
      return values.stream().filter(value -> value.id().equals(id)).findFirst();
    }

    public List<ProjectRecord> findAll() {
      return List.copyOf(values);
    }

    public void save(ProjectRecord value) {
      values.removeIf(existing -> existing.id().equals(value.id()));
      values.add(value);
    }

    public void delete(UUID id) {
      values.removeIf(value -> value.id().equals(id));
    }
  }

  private static final class InMemoryEnvironments implements EnvironmentRepository {
    private final List<EnvironmentRecord> values = new ArrayList<>();

    public Optional<EnvironmentRecord> findById(UUID id) {
      return values.stream().filter(value -> value.id().equals(id)).findFirst();
    }

    public List<EnvironmentRecord> findAll() {
      return List.copyOf(values);
    }

    public void save(EnvironmentRecord value) {
      values.removeIf(existing -> existing.id().equals(value.id()));
      values.add(value);
    }

    public void delete(UUID id) {
      values.removeIf(value -> value.id().equals(id));
    }
  }

  private static final class InMemoryTags implements TagRepository {
    private final List<TagRecord> values = new ArrayList<>();

    public Optional<TagRecord> findById(UUID id) {
      return values.stream().filter(value -> value.id().equals(id)).findFirst();
    }

    public List<TagRecord> findAll() {
      return List.copyOf(values);
    }

    public void save(TagRecord value) {
      values.removeIf(existing -> existing.id().equals(value.id()));
      values.add(value);
    }

    public void delete(UUID id) {
      values.removeIf(value -> value.id().equals(id));
    }
  }

  private static final class InMemoryTunnels implements TunnelRepository {
    private final List<TunnelRecord> values = new ArrayList<>();

    public Optional<TunnelRecord> findById(UUID id) {
      return values.stream().filter(value -> value.id().equals(id)).findFirst();
    }

    public List<TunnelRecord> findAll() {
      return List.copyOf(values);
    }

    public void save(TunnelRecord value) {
      values.removeIf(existing -> existing.id().equals(value.id()));
      values.add(value);
    }

    public void delete(UUID id) {
      values.removeIf(value -> value.id().equals(id));
    }
  }

  private static final class InMemoryGroups implements TunnelGroupRepository {
    private final List<TunnelGroupRecord> values = new ArrayList<>();

    public Optional<TunnelGroupRecord> findById(UUID id) {
      return values.stream().filter(value -> value.id().equals(id)).findFirst();
    }

    public List<TunnelGroupRecord> findAll() {
      return List.copyOf(values);
    }

    public void save(TunnelGroupRecord value) {
      values.removeIf(existing -> existing.id().equals(value.id()));
      values.add(value);
    }

    public void delete(UUID id) {
      values.removeIf(value -> value.id().equals(id));
    }
  }
}

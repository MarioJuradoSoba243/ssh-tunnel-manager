package com.ssh.tunnelmanager.application.group;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssh.tunnelmanager.application.persistence.TunnelGroupRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.persistence.model.TunnelGroupRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroupEditorViewModelTest {
  @Test
  void loadsTunnelSelectionAndSavesTheSelectedMembers() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    TunnelStore tunnels =
        new TunnelStore(List.of(tunnel(first, "Frontend"), tunnel(second, "Backend")));
    GroupStore groups = new GroupStore();
    SaveTunnelGroupUseCase save = new SaveTunnelGroupUseCase(groups, tunnels);
    GroupEditorViewModel editor = new GroupEditorViewModel(tunnels, save);
    UUID groupId = UUID.randomUUID();

    editor.save(new GroupEditorViewModel.Form(groupId, "Development", Set.of(second)), false);

    assertThat(editor.availableTunnels())
        .extracting(TunnelRecord::name)
        .containsExactly("Frontend", "Backend");
    assertThat(groups.findById(groupId))
        .get()
        .extracting(TunnelGroupRecord::tunnelIds)
        .isEqualTo(Set.of(second));
  }

  private static TunnelRecord tunnel(UUID id, String name) {
    return new TunnelRecord(
        id,
        name,
        "127.0.0.1",
        15000 + Math.abs(id.hashCode() % 1000),
        "destination",
        80,
        UUID.randomUUID(),
        null,
        null,
        null,
        null,
        0,
        null,
        null,
        false,
        Set.of(),
        Map.of());
  }

  private static final class TunnelStore implements TunnelRepository {
    private final List<TunnelRecord> values;

    private TunnelStore(List<TunnelRecord> values) {
      this.values = new ArrayList<>(values);
    }

    @Override
    public Optional<TunnelRecord> findById(UUID id) {
      return values.stream().filter(tunnel -> tunnel.id().equals(id)).findFirst();
    }

    @Override
    public List<TunnelRecord> findAll() {
      return List.copyOf(values);
    }

    @Override
    public void save(TunnelRecord tunnel) {
      values.add(tunnel);
    }

    @Override
    public void delete(UUID id) {
      values.removeIf(tunnel -> tunnel.id().equals(id));
    }
  }

  private static final class GroupStore implements TunnelGroupRepository {
    private TunnelGroupRecord saved;

    @Override
    public Optional<TunnelGroupRecord> findById(UUID id) {
      return Optional.ofNullable(saved).filter(group -> group.id().equals(id));
    }

    @Override
    public List<TunnelGroupRecord> findAll() {
      return saved == null ? List.of() : List.of(saved);
    }

    @Override
    public void save(TunnelGroupRecord group) {
      saved = group;
    }

    @Override
    public void delete(UUID id) {
      saved = null;
    }
  }
}

package com.ssh.tunnelmanager.application.tunnel;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssh.tunnelmanager.application.persistence.CredentialRepository;
import com.ssh.tunnelmanager.application.persistence.SshHostRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.persistence.model.CredentialRecord;
import com.ssh.tunnelmanager.application.persistence.model.SshHostRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TunnelEditorViewModelTest {
  private final HostRepository hosts = new HostRepository();
  private final CredentialStore credentials = new CredentialStore();
  private final TunnelStore tunnels = new TunnelStore();
  private TunnelEditorViewModel editor;
  private final UUID jump1Id = UUID.randomUUID();
  private final UUID jump2Id = UUID.randomUUID();
  private final UUID finalId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    editor = new TunnelEditorViewModel(hosts, credentials, tunnels, new SaveTunnelUseCase(tunnels));
    hosts.add(new SshHostRecord(jump1Id, "Jump 1", "jump-1", 22, jump1Id));
    hosts.add(new SshHostRecord(jump2Id, "Jump 2", "jump-2", 22, jump2Id));
    hosts.add(new SshHostRecord(finalId, "Final", "ssh-final", 22, finalId));
    credentials.add(new CredentialRecord(jump1Id, "jump1", "jump1-password"));
    credentials.add(new CredentialRecord(jump2Id, "jump2", "jump2-password"));
    credentials.add(new CredentialRecord(finalId, "final", "final-password"));
  }

  @Test
  void rejectsInvalidPortsAndIncoherentJumpRouteBeforeSave() {
    TunnelEditorViewModel.Form form =
        new TunnelEditorViewModel.Form(
            null,
            "Database",
            "127.0.0.1",
            "70000",
            "db.internal",
            "5432",
            finalId,
            null,
            jump2Id,
            "",
            "",
            "",
            "");

    assertThat(editor.validate(form))
        .contains("Puerto local debe estar entre 1 y 65535", "Jump 2 requiere Jump 1");
  }

  @Test
  void rejectsStructuredFieldsInFreeSshOptions() {
    TunnelEditorViewModel.Form form = validForm();
    form =
        new TunnelEditorViewModel.Form(
            form.id(),
            form.name(),
            form.bindAddress(),
            form.localPort(),
            form.destinationHost(),
            form.destinationPort(),
            form.finalHostId(),
            form.jump1HostId(),
            form.jump2HostId(),
            form.keepAliveSeconds(),
            form.connectionTimeoutSeconds(),
            form.retryCount(),
            "LocalForward=127.0.0.1:1");

    assertThat(editor.validate(form))
        .anyMatch(message -> message.contains("contradice un campo estructurado"));
  }

  @Test
  void savesACompleteTwoJumpSnapshotWithOverridesAndOptions() {
    editor.save(validForm());

    TunnelRecord saved = tunnels.saved;
    assertThat(saved.finalHostId()).isEqualTo(finalId);
    assertThat(saved.jump1HostId()).isEqualTo(jump1Id);
    assertThat(saved.jump2HostId()).isEqualTo(jump2Id);
    assertThat(saved.keepAliveOverrideSeconds()).isEqualTo(45L);
    assertThat(saved.connectionTimeoutOverrideSeconds()).isEqualTo(20L);
    assertThat(saved.retryCountOverride()).isEqualTo(3);
    assertThat(saved.sshOptions()).isEqualTo(Map.of("ServerAliveCountMax", "3"));
  }

  private TunnelEditorViewModel.Form validForm() {
    return new TunnelEditorViewModel.Form(
        null,
        "Database",
        "127.0.0.1",
        "15432",
        "db.internal",
        "5432",
        finalId,
        jump1Id,
        jump2Id,
        "45",
        "20",
        "3",
        "ServerAliveCountMax=3");
  }

  private static final class HostRepository implements SshHostRepository {
    private final List<SshHostRecord> values = new ArrayList<>();

    void add(SshHostRecord value) {
      values.add(value);
    }

    @Override
    public Optional<SshHostRecord> findById(UUID id) {
      return values.stream().filter(value -> value.id().equals(id)).findFirst();
    }

    @Override
    public List<SshHostRecord> findAll() {
      return List.copyOf(values);
    }

    @Override
    public void save(SshHostRecord host) {
      values.add(host);
    }

    @Override
    public void delete(UUID id) {
      values.removeIf(value -> value.id().equals(id));
    }
  }

  private static final class CredentialStore implements CredentialRepository {
    private final List<CredentialRecord> values = new ArrayList<>();

    void add(CredentialRecord value) {
      values.add(value);
    }

    @Override
    public Optional<CredentialRecord> findById(UUID id) {
      return values.stream().filter(value -> value.id().equals(id)).findFirst();
    }

    @Override
    public List<CredentialRecord> findAll() {
      return List.copyOf(values);
    }

    @Override
    public void save(CredentialRecord credential) {
      values.add(credential);
    }

    @Override
    public void delete(UUID id) {
      values.removeIf(value -> value.id().equals(id));
    }
  }

  private static final class TunnelStore implements TunnelRepository {
    private TunnelRecord saved;

    @Override
    public Optional<TunnelRecord> findById(UUID id) {
      return Optional.ofNullable(saved).filter(value -> value.id().equals(id));
    }

    @Override
    public List<TunnelRecord> findAll() {
      return saved == null ? List.of() : List.of(saved);
    }

    @Override
    public void save(TunnelRecord tunnel) {
      saved = tunnel;
    }

    @Override
    public void delete(UUID id) {
      saved = null;
    }
  }
}

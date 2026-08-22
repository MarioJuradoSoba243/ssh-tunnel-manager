package com.ssh.tunnelmanager.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssh.tunnelmanager.application.persistence.model.ConfigurationSnapshot;
import com.ssh.tunnelmanager.application.persistence.model.CredentialRecord;
import com.ssh.tunnelmanager.application.persistence.model.EnvironmentRecord;
import com.ssh.tunnelmanager.application.persistence.model.KeyboardShortcutRecord;
import com.ssh.tunnelmanager.application.persistence.model.ProjectRecord;
import com.ssh.tunnelmanager.application.persistence.model.RecentItemRecord;
import com.ssh.tunnelmanager.application.persistence.model.RecentItemType;
import com.ssh.tunnelmanager.application.persistence.model.SettingRecord;
import com.ssh.tunnelmanager.application.persistence.model.SshHostRecord;
import com.ssh.tunnelmanager.application.persistence.model.TagRecord;
import com.ssh.tunnelmanager.application.persistence.model.TrustedHostKeyRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelGroupRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import com.ssh.tunnelmanager.application.portability.BackupPort;
import com.ssh.tunnelmanager.application.portability.ConfigurationJsonCodec;
import com.ssh.tunnelmanager.application.portability.ConflictAction;
import com.ssh.tunnelmanager.application.portability.ExportConfigurationUseCase;
import com.ssh.tunnelmanager.application.portability.ImportConfigurationUseCase;
import com.ssh.tunnelmanager.application.portability.ImportSession;
import com.ssh.tunnelmanager.application.portability.PortabilityException;
import com.ssh.tunnelmanager.application.portability.RuntimeControl;
import com.ssh.tunnelmanager.application.portability.UnsupportedExportFormatException;
import com.ssh.tunnelmanager.infrastructure.persistence.backup.BackupService;
import com.ssh.tunnelmanager.infrastructure.persistence.migration.DatabaseMigrator;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcConfigurationStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PortabilityIntegrationIT {
  @Test
  void exportsAndImportsARepresentativeHundredTunnelDataset(@TempDir Path directory) {
    ConfigurationSnapshot expected = dataset(100);
    DatabaseFactory sourceDatabase = database(directory.resolve("source.db"));
    JdbcConfigurationStore source = new JdbcConfigurationStore(sourceDatabase);
    source.replace(expected);
    Path export = directory.resolve("configuration.json");

    new ExportConfigurationUseCase(source, new ConfigurationJsonCodec(), "0.4.0").execute(export);

    String json = read(export);
    assertThat(json).contains("\"formatVersion\" : 1").contains("portable-secret");
    var document = new ConfigurationJsonCodec().read(export);
    assertThat(document.formatVersion()).isEqualTo(1);
    assertThat(document.applicationVersion()).isEqualTo("0.4.0");
    assertThat(document.databaseSchemaVersion()).isEqualTo(1);
    DatabaseFactory targetDatabase = database(directory.resolve("target.db"));
    JdbcConfigurationStore target = new JdbcConfigurationStore(targetDatabase);
    CountingBackups backups = new CountingBackups();
    ImportConfigurationUseCase importer =
        new ImportConfigurationUseCase(
            target, new ConfigurationJsonCodec(), backups, new InactiveRuntime());

    ImportSession session = importer.analyze(export);
    assertThat(session.preview().newEntities()).isGreaterThanOrEqualTo(100);
    assertThat(session.preview().conflictCount()).isZero();
    importer.commit(session, Map.of(), Map.of(), false);

    assertSemanticallyEqual(target.snapshot(), expected);
    assertThat(backups.beforeImportCalls).hasValue(1);
  }

  @Test
  void classifiesAndResolvesConflictByKeepingReplacingOrCopying(@TempDir Path directory) {
    ConfigurationSnapshot imported = dataset(1);
    Path export = directory.resolve("configuration.json");
    DatabaseFactory sourceDatabase = database(directory.resolve("source.db"));
    JdbcConfigurationStore source = new JdbcConfigurationStore(sourceDatabase);
    source.replace(imported);
    new ExportConfigurationUseCase(source, new ConfigurationJsonCodec(), "0.4.0").execute(export);

    TunnelRecord original = imported.tunnels().getFirst();
    TunnelRecord changed =
        new TunnelRecord(
            original.id(),
            "Changed locally",
            original.bindAddress(),
            original.localPort(),
            original.destinationHost(),
            original.destinationPort(),
            original.finalHostId(),
            original.jump1HostId(),
            original.jump2HostId(),
            original.keepAliveOverrideSeconds(),
            original.connectionTimeoutOverrideSeconds(),
            original.retryCountOverride(),
            original.projectId(),
            original.environmentId(),
            original.favorite(),
            original.tagIds(),
            original.sshOptions());
    ConfigurationSnapshot local =
        new ConfigurationSnapshot(
            imported.databaseSchemaVersion(),
            imported.credentials(),
            imported.hosts(),
            imported.projects(),
            imported.environments(),
            imported.tags(),
            List.of(changed),
            imported.groups(),
            imported.trustedHostKeys(),
            imported.settings(),
            imported.recentItems(),
            imported.keyboardShortcuts());
    JdbcConfigurationStore target =
        new JdbcConfigurationStore(database(directory.resolve("target.db")));
    target.replace(local);
    CountingBackups backups = new CountingBackups();
    ImportConfigurationUseCase importer =
        new ImportConfigurationUseCase(
            target, new ConfigurationJsonCodec(), backups, new InactiveRuntime());
    ImportSession session = importer.analyze(export);
    assertThat(session.preview().conflictCount()).isEqualTo(1);

    importer.commit(
        session,
        Map.of(session.preview().conflicts().getFirst().key(), ConflictAction.CREATE_COPY),
        Map.of(),
        false);
    assertThat(target.snapshot().tunnels()).hasSize(2);
    assertThat(target.snapshot().tunnels())
        .extracting(TunnelRecord::name)
        .contains("Changed locally", "Tunnel 0");
  }

  @Test
  void rejectsFutureFormatBeforeChangingCurrentDatabase(@TempDir Path directory) throws Exception {
    JdbcConfigurationStore store =
        new JdbcConfigurationStore(database(directory.resolve("db.sqlite")));
    ConfigurationSnapshot expected = dataset(1);
    store.replace(expected);
    Path future = directory.resolve("future.json");
    Files.writeString(future, "{\"formatVersion\":99,\"hosts\":[]}");
    ImportConfigurationUseCase importer =
        new ImportConfigurationUseCase(
            store, new ConfigurationJsonCodec(), new CountingBackups(), new InactiveRuntime());

    assertThatThrownBy(() -> importer.analyze(future))
        .isInstanceOf(UnsupportedExportFormatException.class)
        .hasMessageContaining("99");
    assertSemanticallyEqual(store.snapshot(), expected);
  }

  @Test
  void refusesImportWithActiveTunnelsUnlessStopWasRequested(@TempDir Path directory) {
    JdbcConfigurationStore store =
        new JdbcConfigurationStore(database(directory.resolve("db.sqlite")));
    ConfigurationSnapshot expected = dataset(1);
    store.replace(expected);
    Path export = directory.resolve("configuration.json");
    new ExportConfigurationUseCase(store, new ConfigurationJsonCodec(), "0.4.0").execute(export);
    ActiveRuntime runtime = new ActiveRuntime();
    ImportConfigurationUseCase importer =
        new ImportConfigurationUseCase(
            store, new ConfigurationJsonCodec(), new CountingBackups(), runtime);
    ImportSession session = importer.analyze(export);

    assertThatThrownBy(() -> importer.commit(session, Map.of(), Map.of(), false))
        .isInstanceOf(PortabilityException.class)
        .hasMessageContaining("activos");
    importer.commit(session, Map.of(), Map.of(), true);
    assertThat(runtime.stopCalls).hasValue(1);
  }

  @Test
  void rollsBackAnInvalidReplacement(@TempDir Path directory) {
    JdbcConfigurationStore store =
        new JdbcConfigurationStore(database(directory.resolve("db.sqlite")));
    ConfigurationSnapshot expected = dataset(1);
    store.replace(expected);
    TunnelRecord invalid =
        new TunnelRecord(
            expected.tunnels().getFirst().id(),
            "invalid",
            "127.0.0.1",
            45000,
            "target",
            5432,
            UUID.randomUUID(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            Set.of(),
            Map.of());
    ConfigurationSnapshot invalidSnapshot =
        new ConfigurationSnapshot(
            1,
            expected.credentials(),
            expected.hosts(),
            expected.projects(),
            expected.environments(),
            expected.tags(),
            List.of(invalid),
            expected.groups(),
            expected.trustedHostKeys(),
            expected.settings(),
            expected.recentItems(),
            expected.keyboardShortcuts());

    assertThatThrownBy(() -> store.replace(invalidSnapshot)).isInstanceOf(RuntimeException.class);
    assertSemanticallyEqual(store.snapshot(), expected);
  }

  @Test
  void createsConsistentBackupsRetainsTenAndRestores(@TempDir Path directory) throws Exception {
    DatabaseFactory database = database(directory.resolve("db.sqlite"));
    JdbcConfigurationStore store = new JdbcConfigurationStore(database);
    store.replace(dataset(1));
    BackupService backups = new BackupService(database, directory.resolve("backups"));
    List<Path> created = new ArrayList<>();
    for (int index = 0; index < 11; index++) {
      backups.markDirty();
      created.add(backups.createBackup("test"));
    }
    assertThat(backups.listBackups()).hasSize(10);
    assertThat(created.getLast()).isRegularFile();
    BackupServiceTestSupport.assertValid(created.getLast());

    store.replace(
        new ConfigurationSnapshot(
            1,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(new SettingRecord("changed", "true")),
            List.of(),
            List.of()));
    backups.beforeRestore();
    backups.restore(created.getLast());
    assertThat(store.snapshot().tunnels()).hasSize(1);
    assertThat(store.snapshot().settings())
        .extracting(SettingRecord::key)
        .doesNotContain("changed");
  }

  @Test
  void detectsCorruptionListsOnlyValidBackupsAndRestoresWithoutOverwritingSilently(
      @TempDir Path directory) throws Exception {
    DatabaseFactory database = database(directory.resolve("db.sqlite"));
    JdbcConfigurationStore store = new JdbcConfigurationStore(database);
    ConfigurationSnapshot expected = dataset(1);
    store.replace(expected);
    BackupService backups = new BackupService(database, directory.resolve("backups"));
    Path valid = backups.createBackup("corruption-test");
    Path invalid = directory.resolve("backups").resolve("backup-corrupt.db");
    Files.writeString(invalid, "not a sqlite database");
    Files.writeString(database.databasePath(), "not a sqlite database");

    assertThatThrownBy(() -> new DatabaseHealthChecker(database).assertHealthy())
        .isInstanceOf(DatabaseCorruptionException.class);
    assertThat(backups.listBackups()).containsExactly(valid);

    backups.restoreAfterCorruption(valid);
    assertSemanticallyEqual(new JdbcConfigurationStore(database).snapshot(), expected);
    try (var files = Files.list(directory)) {
      assertThat(files.map(Path::getFileName).map(Path::toString).toList())
          .anyMatch(name -> name.startsWith("db.sqlite.corrupt-"));
    }
  }

  private static DatabaseFactory database(Path path) {
    DatabaseFactory database = new DatabaseFactory(path);
    new DatabaseMigrator(database).migrate();
    return database;
  }

  private static ConfigurationSnapshot dataset(int tunnelCount) {
    UUID credentialId = UUID.randomUUID();
    UUID jump1CredentialId = UUID.randomUUID();
    UUID jump2CredentialId = UUID.randomUUID();
    UUID finalCredentialId = UUID.randomUUID();
    UUID jump1Id = UUID.randomUUID();
    UUID jump2Id = UUID.randomUUID();
    UUID finalId = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();
    UUID environmentId = UUID.randomUUID();
    UUID tagId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    List<CredentialRecord> credentials =
        List.of(
            new CredentialRecord(credentialId, "user", "portable-secret"),
            new CredentialRecord(jump1CredentialId, "jump1", "jump1-secret"),
            new CredentialRecord(jump2CredentialId, "jump2", "jump2-secret"),
            new CredentialRecord(finalCredentialId, "final", "final-secret"));
    List<SshHostRecord> hosts =
        List.of(
            new SshHostRecord(jump1Id, "Jump 1", "jump1.example", 22, jump1CredentialId),
            new SshHostRecord(jump2Id, "Jump 2", "jump2.example", 22, jump2CredentialId),
            new SshHostRecord(finalId, "Final", "final.example", 22, finalCredentialId));
    List<TunnelRecord> tunnels = new ArrayList<>();
    for (int index = 0; index < tunnelCount; index++) {
      tunnels.add(
          new TunnelRecord(
              UUID.randomUUID(),
              "Tunnel " + index,
              "127.0.0.1",
              15000 + index,
              "database.internal",
              5432,
              finalId,
              index % 3 == 1 || index % 3 == 2 ? jump1Id : null,
              index % 3 == 2 ? jump2Id : null,
              30L,
              15L,
              5,
              projectId,
              environmentId,
              index == 0,
              Set.of(tagId),
              Map.of("ServerAliveCountMax", "3")));
    }
    return new ConfigurationSnapshot(
        1,
        credentials,
        hosts,
        List.of(new ProjectRecord(projectId, "Project")),
        List.of(new EnvironmentRecord(environmentId, "Production")),
        List.of(new TagRecord(tagId, "database")),
        tunnels,
        List.of(
            new TunnelGroupRecord(
                groupId,
                "All database tunnels",
                projectId,
                environmentId,
                true,
                Set.of(tunnels.getFirst().id()),
                Set.of(tagId))),
        List.of(
            new TrustedHostKeyRecord(
                UUID.randomUUID(),
                "final.example",
                22,
                "ssh-rsa",
                "public-key",
                "SHA256:fingerprint",
                Instant.parse("2026-08-23T00:00:00Z"))),
        List.of(new SettingRecord("theme", "dark")),
        List.of(
            new RecentItemRecord(RecentItemType.TUNNEL, tunnels.getFirst().id(), Instant.now())),
        List.of(new KeyboardShortcutRecord("search", "CTRL+K")));
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }

  private static void assertSemanticallyEqual(
      ConfigurationSnapshot actual, ConfigurationSnapshot expected) {
    assertThat(actual.databaseSchemaVersion()).isEqualTo(expected.databaseSchemaVersion());
    assertThat(actual.credentials()).containsExactlyInAnyOrderElementsOf(expected.credentials());
    assertThat(actual.hosts()).containsExactlyInAnyOrderElementsOf(expected.hosts());
    assertThat(actual.projects()).containsExactlyInAnyOrderElementsOf(expected.projects());
    assertThat(actual.environments()).containsExactlyInAnyOrderElementsOf(expected.environments());
    assertThat(actual.tags()).containsExactlyInAnyOrderElementsOf(expected.tags());
    assertThat(actual.tunnels()).containsExactlyInAnyOrderElementsOf(expected.tunnels());
    assertThat(actual.groups()).containsExactlyInAnyOrderElementsOf(expected.groups());
    assertThat(actual.trustedHostKeys())
        .containsExactlyInAnyOrderElementsOf(expected.trustedHostKeys());
    assertThat(actual.settings()).containsExactlyInAnyOrderElementsOf(expected.settings());
    assertThat(actual.recentItems()).containsExactlyInAnyOrderElementsOf(expected.recentItems());
    assertThat(actual.keyboardShortcuts())
        .containsExactlyInAnyOrderElementsOf(expected.keyboardShortcuts());
  }

  private static class CountingBackups implements BackupPort {
    private final AtomicInteger beforeImportCalls = new AtomicInteger();

    @Override
    public void markDirty() {}

    @Override
    public Path backupIfDue() {
      return null;
    }

    @Override
    public Path beforeImport() {
      beforeImportCalls.incrementAndGet();
      return null;
    }

    @Override
    public Path beforeRestore() {
      return null;
    }

    @Override
    public List<Path> listBackups() {
      return List.of();
    }

    @Override
    public void restore(Path backup) {}
  }

  private static class InactiveRuntime implements RuntimeControl {
    @Override
    public boolean hasActiveTunnels() {
      return false;
    }

    @Override
    public void stopAllAndAwait() {}
  }

  private static final class ActiveRuntime extends InactiveRuntime {
    private final AtomicInteger stopCalls = new AtomicInteger();

    @Override
    public boolean hasActiveTunnels() {
      return stopCalls.get() == 0;
    }

    @Override
    public void stopAllAndAwait() {
      stopCalls.incrementAndGet();
    }
  }

  private static final class BackupServiceTestSupport {
    private static void assertValid(Path backup) {
      com.ssh.tunnelmanager.infrastructure.persistence.backup.SQLiteSnapshotService.validate(
          backup);
    }
  }
}

package com.ssh.tunnelmanager.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssh.tunnelmanager.application.persistence.model.CredentialRecord;
import com.ssh.tunnelmanager.application.persistence.model.KeyboardShortcutRecord;
import com.ssh.tunnelmanager.application.persistence.model.RecentItemRecord;
import com.ssh.tunnelmanager.application.persistence.model.RecentItemType;
import com.ssh.tunnelmanager.application.persistence.model.SshHostRecord;
import com.ssh.tunnelmanager.application.persistence.model.TrustedHostKeyRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelGroupRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import com.ssh.tunnelmanager.infrastructure.persistence.backup.BackupService;
import com.ssh.tunnelmanager.infrastructure.persistence.migration.DatabaseMigrator;
import com.ssh.tunnelmanager.infrastructure.persistence.migration.MigrationScript;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcApplicationSettingsRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcCredentialRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcEnvironmentRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcKeyboardShortcutRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcProjectRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcRecentItemRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcSshHostRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcTagRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcTrustedHostKeyRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcTunnelGroupRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcTunnelRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceIntegrationIT {
  @TempDir Path temporaryDirectory;

  private DatabaseFactory database;
  private BackupService backups;
  private JdbcCredentialRepository credentials;
  private JdbcSshHostRepository hosts;
  private JdbcTunnelRepository tunnels;
  private JdbcProjectRepository projects;
  private JdbcEnvironmentRepository environments;
  private JdbcTagRepository tags;
  private JdbcTunnelGroupRepository groups;
  private JdbcApplicationSettingsRepository settings;
  private JdbcRecentItemRepository recent;

  @BeforeEach
  void setUp() {
    database = new DatabaseFactory(temporaryDirectory.resolve("ssh-tunnel-manager.db"));
    new DatabaseMigrator(database).migrate();
    backups = new BackupService(database, temporaryDirectory.resolve("backups"));
    Runnable dirty = backups::markDirty;
    credentials = new JdbcCredentialRepository(database, dirty);
    hosts = new JdbcSshHostRepository(database, dirty);
    tunnels = new JdbcTunnelRepository(database, dirty);
    projects = new JdbcProjectRepository(database, dirty);
    environments = new JdbcEnvironmentRepository(database, dirty);
    tags = new JdbcTagRepository(database, dirty);
    groups = new JdbcTunnelGroupRepository(database, dirty);
    settings = new JdbcApplicationSettingsRepository(database, dirty);
    recent = new JdbcRecentItemRepository(database, dirty);
  }

  @Test
  void migratesEmptyDatabaseWithRequiredPragmasAndTables() {
    assertThat(tableNames())
        .contains(
            "schema_version",
            "ssh_host",
            "credential",
            "tunnel",
            "tunnel_group",
            "trusted_host_key",
            "application_setting");
    database.withConnection(
        connection -> {
          try (var statement = connection.createStatement();
              var foreignKeys = statement.executeQuery("PRAGMA foreign_keys")) {
            foreignKeys.next();
            assertThat(foreignKeys.getInt(1)).isEqualTo(1);
          }
          try (var statement = connection.createStatement();
              var journalMode = statement.executeQuery("PRAGMA journal_mode")) {
            journalMode.next();
            assertThat(journalMode.getString(1)).isEqualToIgnoringCase("wal");
          }
          return null;
        });
  }

  @Test
  void failedMigrationRollsBackAndKeepsPreviousVersion() {
    MigrationScript broken =
        new MigrationScript(2, "V002__broken", "CREATE TABLE partial_table(id TEXT); INVALID SQL;");
    assertThatThrownBy(
            () ->
                new DatabaseMigrator(database, List.of(broken), () -> {}, Clock.systemUTC())
                    .migrate())
        .isInstanceOf(PersistenceException.class);
    assertThat(tableNames()).doesNotContain("partial_table");
    assertThat(schemaVersion()).isEqualTo(1);
  }

  @Test
  void repositoriesPersistUuidRelationsOptionsAndDeletePolicies() {
    UUID credentialId = UUID.randomUUID();
    UUID hostId = UUID.randomUUID();
    UUID jumpId = UUID.randomUUID();
    credentials.save(new CredentialRecord(credentialId, "alice", "secret-value"));
    hosts.save(new SshHostRecord(hostId, "main", "main.example", 22, credentialId));
    hosts.save(new SshHostRecord(jumpId, "jump", "jump.example", 2200, credentialId));

    UUID projectId = UUID.randomUUID();
    UUID environmentId = UUID.randomUUID();
    UUID tagId = UUID.randomUUID();
    projects.save(
        new com.ssh.tunnelmanager.application.persistence.model.ProjectRecord(
            projectId, "project"));
    environments.save(
        new com.ssh.tunnelmanager.application.persistence.model.EnvironmentRecord(
            environmentId, "dev"));
    tags.save(new com.ssh.tunnelmanager.application.persistence.model.TagRecord(tagId, "ssh"));

    UUID tunnelId = UUID.randomUUID();
    TunnelRecord tunnel =
        new TunnelRecord(
            tunnelId,
            "web",
            "127.0.0.1",
            8080,
            "10.0.0.5",
            80,
            hostId,
            jumpId,
            null,
            30L,
            10_000L,
            3,
            projectId,
            environmentId,
            true,
            Set.of(tagId),
            Map.of("compression", "yes"));
    tunnels.save(tunnel);
    assertThat(tunnels.findById(tunnelId)).contains(tunnel);
    assertThatThrownBy(() -> hosts.delete(hostId))
        .isInstanceOf(PersistenceConstraintException.class);

    UUID groupId = UUID.randomUUID();
    groups.save(
        new TunnelGroupRecord(
            groupId, "ops", projectId, environmentId, true, Set.of(tunnelId), Set.of(tagId)));
    assertThat(groups.findById(groupId))
        .get()
        .satisfies(group -> assertThat(group.tunnelIds()).containsExactly(tunnelId));
    UUID sharedGroupId = UUID.randomUUID();
    groups.save(
        new TunnelGroupRecord(
            sharedGroupId, "shared", null, null, false, Set.of(tunnelId), Set.of()));
    assertThat(groups.findById(sharedGroupId))
        .get()
        .satisfies(group -> assertThat(group.tunnelIds()).containsExactly(tunnelId));
    assertThatThrownBy(
            () ->
                groups.save(
                    new TunnelGroupRecord(
                        UUID.randomUUID(), "ops", null, null, false, Set.of(tunnelId), Set.of())))
        .isInstanceOf(PersistenceConstraintException.class);

    projects.delete(projectId);
    environments.delete(environmentId);
    assertThat(tunnels.findById(tunnelId))
        .get()
        .satisfies(
            updated -> {
              assertThat(updated.projectId()).isNull();
              assertThat(updated.environmentId()).isNull();
            });
    tunnels.delete(tunnelId);
    assertThat(groups.findById(groupId))
        .get()
        .satisfies(updated -> assertThat(updated.tunnelIds()).isEmpty());
    assertThat(groups.findById(sharedGroupId))
        .get()
        .satisfies(updated -> assertThat(updated.tunnelIds()).isEmpty());
    groups.delete(groupId);
    groups.delete(sharedGroupId);
    hosts.delete(hostId);
    hosts.delete(jumpId);
    assertThat(credentials.findById(credentialId)).isPresent();
  }

  @Test
  void repositoriesCoverTrustedKeysSettingsRecentsAndShortcuts() {
    UUID keyId = UUID.randomUUID();
    JdbcTrustedHostKeyRepository trustedKeys =
        new JdbcTrustedHostKeyRepository(database, backups::markDirty);
    TrustedHostKeyRecord key =
        new TrustedHostKeyRecord(
            keyId,
            "host.example",
            22,
            "ssh-ed25519",
            "public",
            "SHA256:fingerprint",
            Instant.now());
    trustedKeys.save(key);
    assertThat(trustedKeys.findByHost("host.example", 22)).contains(key);
    settings.put("theme", "dark");
    assertThat(settings.findValue("theme")).contains("dark");
    JdbcKeyboardShortcutRepository shortcuts =
        new JdbcKeyboardShortcutRepository(database, backups::markDirty);
    shortcuts.save(new KeyboardShortcutRecord("start", "CTRL+ENTER"));
    assertThat(shortcuts.findByAction("start"))
        .get()
        .extracting(KeyboardShortcutRecord::shortcut)
        .isEqualTo("CTRL+ENTER");
    for (int index = 0; index < 10; index++) {
      recent.record(
          new RecentItemRecord(
              RecentItemType.TUNNEL, UUID.randomUUID(), Instant.now().plusSeconds(index)));
    }
    assertThat(recent.findRecent(20)).hasSize(8);
    trustedKeys.deleteByHost("host.example", 22);
    shortcuts.delete("start");
    settings.delete("theme");
    recent.deleteAll();
    assertThat(recent.findRecent(8)).isEmpty();
  }

  @Test
  void transactionRollbackLeavesNoPartialRecord() {
    UUID id = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                database.inTransaction(
                    connection -> {
                      try (var statement =
                          connection.prepareStatement(
                              "INSERT INTO credential(id, username, password) VALUES (?, ?, ?)")) {
                        statement.setString(1, id.toString());
                        statement.setString(2, "rollback");
                        statement.setString(3, "not-logged");
                        statement.executeUpdate();
                      }
                      throw new IllegalStateException("force rollback");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(credentials.findById(id)).isEmpty();
  }

  @Test
  void groupSaveRollsBackParentWhenMemberForeignKeyFails() {
    UUID groupId = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                groups.save(
                    new TunnelGroupRecord(
                        groupId,
                        "invalid-member",
                        null,
                        null,
                        false,
                        Set.of(UUID.randomUUID()),
                        Set.of())))
        .isInstanceOf(PersistenceConstraintException.class);
    assertThat(groups.findById(groupId)).isEmpty();
  }

  @Test
  void snapshotRetentionAndRestoreUseConsistentSQLiteFiles() throws Exception {
    settings.put("state", "before");
    Path first = backups.createBackup("test");
    settings.put("state", "after");
    assertThat(first).isRegularFile();
    backups.restore(first);
    assertThat(new JdbcApplicationSettingsRepository(database).findValue("state"))
        .contains("before");
    for (int index = 0; index < 11; index++) backups.createBackup("retention");
    assertThat(backups.listBackups()).hasSize(10);
    assertThat(backups.isDirty()).isFalse();
  }

  @Test
  void dirtyBackupsAreLimitedToOnePerThirtyMinutes() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    BackupService timedBackups =
        new BackupService(database, temporaryDirectory.resolve("timed-backups"), clock);
    timedBackups.markDirty();
    assertThat(timedBackups.backupIfDue()).isNotNull();
    timedBackups.markDirty();
    clock.advance(Duration.ofMinutes(29));
    assertThat(timedBackups.backupIfDue()).isNull();
    clock.advance(Duration.ofMinutes(2));
    assertThat(timedBackups.backupIfDue()).isNotNull();
  }

  @Test
  void corruptionIsReportedWithoutOverwritingTheDatabase() throws Exception {
    Path databasePath = database.databasePath();
    Files.writeString(databasePath, "not a sqlite database");
    byte[] before = Files.readAllBytes(databasePath);
    assertThatThrownBy(() -> new DatabaseHealthChecker(database).assertHealthy())
        .isInstanceOfAny(DatabaseCorruptionException.class, PersistenceException.class);
    assertThat(Files.readAllBytes(databasePath)).isEqualTo(before);
  }

  @Test
  void installedAndPortablePathsAreSeparated() {
    DataDirectories installed =
        DataDirectoryResolver.forInstalled(temporaryDirectory.resolve("local-app-data"));
    DataDirectories portable =
        DataDirectoryResolver.forPortable(temporaryDirectory.resolve("portable"));
    assertThat(installed.database())
        .isEqualTo(
            temporaryDirectory
                .resolve("local-app-data/SSH Tunnel Manager/ssh-tunnel-manager.db")
                .toAbsolutePath()
                .normalize());
    assertThat(portable.database())
        .isEqualTo(
            temporaryDirectory
                .resolve("portable/data/ssh-tunnel-manager.db")
                .toAbsolutePath()
                .normalize());
    assertThat(installed.database()).isNotEqualTo(portable.database());
  }

  private List<String> tableNames() {
    return database.withConnection(
        connection -> {
          try (var statement = connection.createStatement();
              var result =
                  statement.executeQuery(
                      "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")) {
            var names = new java.util.ArrayList<String>();
            while (result.next()) names.add(result.getString(1));
            return names;
          }
        });
  }

  private int schemaVersion() {
    return database.withConnection(
        connection -> {
          try (var statement = connection.createStatement();
              var result = statement.executeQuery("SELECT MAX(version) FROM schema_version")) {
            result.next();
            return result.getInt(1);
          }
        });
  }

  private static final class MutableClock extends Clock {
    private Instant current;

    private MutableClock(Instant current) {
      this.current = current;
    }

    private void advance(Duration duration) {
      current = current.plus(duration);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return current;
    }
  }
}

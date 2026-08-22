package com.ssh.tunnelmanager.application.bootstrap;

import com.ssh.tunnelmanager.application.diagnostics.DiagnosticPort;
import com.ssh.tunnelmanager.application.group.DeleteTunnelGroupUseCase;
import com.ssh.tunnelmanager.application.group.DuplicateTunnelGroupUseCase;
import com.ssh.tunnelmanager.application.group.GroupStatusService;
import com.ssh.tunnelmanager.application.group.SaveTunnelGroupUseCase;
import com.ssh.tunnelmanager.application.group.StartGroupUseCase;
import com.ssh.tunnelmanager.application.group.StopGroupUseCase;
import com.ssh.tunnelmanager.application.notifications.NotificationPort;
import com.ssh.tunnelmanager.application.organization.ClassificationUsageService;
import com.ssh.tunnelmanager.application.organization.DeleteEnvironmentUseCase;
import com.ssh.tunnelmanager.application.organization.DeleteProjectUseCase;
import com.ssh.tunnelmanager.application.organization.DeleteTagUseCase;
import com.ssh.tunnelmanager.application.organization.SaveEnvironmentUseCase;
import com.ssh.tunnelmanager.application.organization.SaveProjectUseCase;
import com.ssh.tunnelmanager.application.organization.SaveTagUseCase;
import com.ssh.tunnelmanager.application.persistence.ApplicationSettingsRepository;
import com.ssh.tunnelmanager.application.persistence.BulkWorkspaceRepository;
import com.ssh.tunnelmanager.application.persistence.ConfigurationStore;
import com.ssh.tunnelmanager.application.persistence.CredentialRepository;
import com.ssh.tunnelmanager.application.persistence.EnvironmentRepository;
import com.ssh.tunnelmanager.application.persistence.KeyboardShortcutRepository;
import com.ssh.tunnelmanager.application.persistence.ProjectRepository;
import com.ssh.tunnelmanager.application.persistence.RecentItemRepository;
import com.ssh.tunnelmanager.application.persistence.SshHostRepository;
import com.ssh.tunnelmanager.application.persistence.TagRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelGroupRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.portability.BackupPort;
import com.ssh.tunnelmanager.application.portability.ConfigurationJsonCodec;
import com.ssh.tunnelmanager.application.portability.ExportConfigurationUseCase;
import com.ssh.tunnelmanager.application.portability.ImportConfigurationUseCase;
import com.ssh.tunnelmanager.application.portability.RestoreBackupUseCase;
import com.ssh.tunnelmanager.application.portability.RuntimeControl;
import com.ssh.tunnelmanager.application.recent.RecentItemService;
import com.ssh.tunnelmanager.application.runtime.ApplicationEventPublisher;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager;
import com.ssh.tunnelmanager.application.settings.ApplicationSettingsService;
import com.ssh.tunnelmanager.application.settings.LoggingLevelPort;
import com.ssh.tunnelmanager.application.settings.ShortcutRegistry;
import com.ssh.tunnelmanager.application.shell.NavigationItem;
import com.ssh.tunnelmanager.application.shell.ShellViewModel;
import com.ssh.tunnelmanager.application.ssh.HostKeyApprovalCoordinator;
import com.ssh.tunnelmanager.application.ssh.SshTunnelEngine;
import com.ssh.tunnelmanager.application.tunnel.DeleteSshHostUseCase;
import com.ssh.tunnelmanager.application.tunnel.DeleteTunnelUseCase;
import com.ssh.tunnelmanager.application.tunnel.DuplicateTunnelUseCase;
import com.ssh.tunnelmanager.application.tunnel.PersistentTunnelRequestProvider;
import com.ssh.tunnelmanager.application.tunnel.RetryTunnelUseCase;
import com.ssh.tunnelmanager.application.tunnel.SaveSshHostUseCase;
import com.ssh.tunnelmanager.application.tunnel.SaveTunnelUseCase;
import com.ssh.tunnelmanager.application.tunnel.StartTunnelUseCase;
import com.ssh.tunnelmanager.application.tunnel.StopTunnelUseCase;
import com.ssh.tunnelmanager.application.tunnel.TestTunnelConnectionUseCase;
import com.ssh.tunnelmanager.infrastructure.diagnostics.ZipDiagnosticService;
import com.ssh.tunnelmanager.infrastructure.logging.LoggingLevelController;
import com.ssh.tunnelmanager.infrastructure.persistence.DataDirectories;
import com.ssh.tunnelmanager.infrastructure.persistence.DataDirectoryResolver;
import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceException;
import com.ssh.tunnelmanager.infrastructure.persistence.StorageMode;
import com.ssh.tunnelmanager.infrastructure.persistence.backup.BackupService;
import com.ssh.tunnelmanager.infrastructure.persistence.migration.DatabaseMigrator;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcApplicationSettingsRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcBulkWorkspaceRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcConfigurationStore;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcCredentialRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcEnvironmentRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcKeyboardShortcutRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcProjectRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcRecentItemRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcSshHostRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcTagRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcTrustedHostKeyRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcTunnelRepository;
import com.ssh.tunnelmanager.infrastructure.ssh.MinaSshTunnelEngine;
import com.ssh.tunnelmanager.infrastructure.windows.WindowsNotificationService;
import java.nio.file.Path;
import java.util.List;

/** Explicit composition root for the application object graph. */
public final class ApplicationBootstrap {
  private final ApplicationInfo applicationInfo;

  public ApplicationBootstrap(ApplicationInfo applicationInfo) {
    this.applicationInfo = applicationInfo;
  }

  public ApplicationInfo applicationInfo() {
    return applicationInfo;
  }

  public ShellViewModel createShellViewModel() {
    return new ShellViewModel(
        List.of(
            new NavigationItem("home", "Inicio"),
            new NavigationItem("tunnels", "Túneles"),
            new NavigationItem("groups", "Grupos"),
            new NavigationItem("hosts", "Hosts SSH"),
            new NavigationItem("organization", "Organización"),
            new NavigationItem("settings", "Ajustes")));
  }

  public ApplicationComponents createComponents(
      DatabaseFactory database,
      SshTunnelEngine engine,
      TunnelRepository tunnelRepository,
      SshHostRepository hostRepository,
      CredentialRepository credentialRepository,
      com.ssh.tunnelmanager.application.persistence.TrustedHostKeyRepository trustedHostKeys,
      TunnelGroupRepository groupRepository) {
    return createComponents(
        database,
        engine,
        tunnelRepository,
        hostRepository,
        credentialRepository,
        trustedHostKeys,
        groupRepository,
        noOpBackups(),
        noOpDiagnostics(),
        null,
        noOpNotifications());
  }

  private ApplicationComponents createComponents(
      DatabaseFactory database,
      SshTunnelEngine engine,
      TunnelRepository tunnelRepository,
      SshHostRepository hostRepository,
      CredentialRepository credentialRepository,
      com.ssh.tunnelmanager.application.persistence.TrustedHostKeyRepository trustedHostKeys,
      TunnelGroupRepository groupRepository,
      BackupPort backups,
      DiagnosticPort diagnostics,
      DataDirectories diagnosticDirectories,
      NotificationPort notifications) {
    var requestProvider =
        new PersistentTunnelRequestProvider(tunnelRepository, hostRepository, credentialRepository);
    var events = new ApplicationEventPublisher();
    var runtime = new TunnelRuntimeManager(engine, requestProvider, events, 8);
    ProjectRepository projects = new JdbcProjectRepository(database, backups::markDirty);
    EnvironmentRepository environments =
        new JdbcEnvironmentRepository(database, backups::markDirty);
    TagRepository tags = new JdbcTagRepository(database, backups::markDirty);
    RecentItemRepository recentItems = new JdbcRecentItemRepository(database, backups::markDirty);
    BulkWorkspaceRepository bulkWorkspace =
        new JdbcBulkWorkspaceRepository(database, backups::markDirty);
    ConfigurationStore configurationStore =
        new JdbcConfigurationStore(database, backups::markDirty);
    ApplicationSettingsRepository settingsRepository =
        new JdbcApplicationSettingsRepository(database, backups::markDirty);
    KeyboardShortcutRepository keyboardShortcutRepository =
        new JdbcKeyboardShortcutRepository(database, backups::markDirty);
    ShortcutRegistry shortcuts = new ShortcutRegistry(keyboardShortcutRepository);
    ApplicationSettingsService settings =
        new ApplicationSettingsService(settingsRepository, shortcuts);
    NotificationPort effectiveNotifications =
        diagnosticDirectories == null
            ? notifications
            : new WindowsNotificationService(settings::notificationsEnabled);
    LoggingLevelPort loggingLevel =
        diagnosticDirectories == null ? level -> {} : LoggingLevelController::apply;
    events.subscribe(
        event -> {
          if (event
              instanceof com.ssh.tunnelmanager.application.runtime.TunnelEvent.TunnelError error) {
            effectiveNotifications.notify(
                "Error en túnel", "Túnel " + error.tunnelId() + ": " + safeMessage(error.error()));
          }
        });
    DiagnosticPort effectiveDiagnostics =
        diagnosticDirectories == null
            ? diagnostics
            : new ZipDiagnosticService(
                configurationStore, runtime, diagnosticDirectories, applicationInfo.version());
    var recents = new RecentItemService(recentItems);
    var usage = new ClassificationUsageService(tunnelRepository, groupRepository);
    var saveGroup = new SaveTunnelGroupUseCase(groupRepository, tunnelRepository);
    return new ApplicationComponents(
        new SaveSshHostUseCase(hostRepository, credentialRepository),
        new DeleteSshHostUseCase(hostRepository, tunnelRepository),
        new SaveTunnelUseCase(tunnelRepository),
        new DeleteTunnelUseCase(tunnelRepository, runtime),
        new DuplicateTunnelUseCase(tunnelRepository),
        new StartTunnelUseCase(runtime, recents),
        new StopTunnelUseCase(runtime),
        new RetryTunnelUseCase(runtime),
        new TestTunnelConnectionUseCase(engine, requestProvider),
        runtime,
        engine,
        hostRepository,
        tunnelRepository,
        credentialRepository,
        trustedHostKeys,
        groupRepository,
        saveGroup,
        new DeleteTunnelGroupUseCase(groupRepository),
        new DuplicateTunnelGroupUseCase(groupRepository),
        new StartGroupUseCase(groupRepository, runtime, events, recents),
        new StopGroupUseCase(groupRepository, runtime),
        new GroupStatusService(groupRepository, runtime),
        projects,
        environments,
        tags,
        recentItems,
        bulkWorkspace,
        new SaveProjectUseCase(projects),
        new DeleteProjectUseCase(projects, usage),
        new SaveEnvironmentUseCase(environments),
        new DeleteEnvironmentUseCase(environments, usage),
        new SaveTagUseCase(tags),
        new DeleteTagUseCase(tags, usage),
        configurationStore,
        settingsRepository,
        keyboardShortcutRepository,
        settings,
        shortcuts,
        loggingLevel,
        effectiveDiagnostics,
        effectiveNotifications,
        new ExportConfigurationUseCase(
            configurationStore, new ConfigurationJsonCodec(), applicationInfo.version()),
        new ImportConfigurationUseCase(
            configurationStore, new ConfigurationJsonCodec(), backups, runtimeControl(runtime)),
        new RestoreBackupUseCase(backups, runtimeControl(runtime)),
        backups);
  }

  public static ApplicationBootstrap production(
      ApplicationInfo info, DataDirectories directories, HostKeyApprovalCoordinator approvals) {
    DataDirectoryResolver.createDirectories(directories);
    DatabaseFactory database = new DatabaseFactory(directories.database());
    BackupService backups = new BackupService(database, directories.backups());
    new DatabaseMigrator(database, backups).migrate();
    JdbcCredentialRepository credentials =
        new JdbcCredentialRepository(database, backups::markDirty);
    JdbcSshHostRepository hosts = new JdbcSshHostRepository(database, backups::markDirty);
    JdbcTunnelRepository tunnels = new JdbcTunnelRepository(database, backups::markDirty);
    com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcTunnelGroupRepository groups =
        new com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcTunnelGroupRepository(
            database, backups::markDirty);
    JdbcTrustedHostKeyRepository trustedKeys =
        new JdbcTrustedHostKeyRepository(database, backups::markDirty);
    SshTunnelEngine engine = new MinaSshTunnelEngine(trustedKeys, approvals);
    ApplicationBootstrap bootstrap = new ApplicationBootstrap(info);
    bootstrap.components =
        bootstrap.createComponents(
            database,
            engine,
            tunnels,
            hosts,
            credentials,
            trustedKeys,
            groups,
            backups,
            noOpDiagnostics(),
            directories,
            noOpNotifications());
    return bootstrap;
  }

  public static ApplicationBootstrap productionDefault(
      ApplicationInfo info, HostKeyApprovalCoordinator approvals) {
    DataDirectories directories = defaultDirectories();
    try {
      return production(info, directories, approvals);
    } catch (PersistenceException exception) {
      ApplicationBootstrap recovery = new ApplicationBootstrap(info);
      recovery.recovery =
          new RecoveryContext(
              directories,
              approvals,
              exception,
              new BackupService(
                  new DatabaseFactory(directories.database()), directories.backups()));
      return recovery;
    }
  }

  private static Path applicationDirectory() {
    Path workingDirectory = Path.of(System.getProperty("user.dir"));
    return ProcessHandle.current()
        .info()
        .command()
        .map(Path::of)
        .map(Path::getParent)
        .filter(path -> java.nio.file.Files.exists(path.resolve("portable.flag")))
        .orElse(workingDirectory);
  }

  public static Path defaultApplicationDirectory() {
    return applicationDirectory();
  }

  public static DataDirectories defaultDirectories() {
    Path applicationDirectory = applicationDirectory();
    String localAppData =
        System.getenv().getOrDefault("LOCALAPPDATA", applicationDirectory.toString());
    StorageMode mode =
        java.nio.file.Files.exists(applicationDirectory.resolve("portable.flag"))
            ? StorageMode.PORTABLE
            : StorageMode.INSTALLED;
    return DataDirectoryResolver.resolve(mode, applicationDirectory, Path.of(localAppData));
  }

  private ApplicationComponents components;
  private RecoveryContext recovery;

  public ApplicationComponents components() {
    if (components == null)
      throw new IllegalStateException("El bootstrap no está configurado para producción");
    return components;
  }

  public boolean isProductionConfigured() {
    return components != null;
  }

  public boolean isRecoveryMode() {
    return recovery != null;
  }

  public String recoveryMessage() {
    if (recovery == null) return null;
    return recovery.failure.getMessage();
  }

  public List<Path> recoveryBackups() {
    if (recovery == null) return List.of();
    return recovery.backups.listBackups();
  }

  public void restoreRecovery(Path backup) {
    if (recovery == null) throw new IllegalStateException("La aplicación no está en recuperación");
    recovery.backups.restoreAfterCorruption(backup);
    ApplicationBootstrap restored =
        production(applicationInfo, recovery.directories, recovery.approvals);
    components = restored.components;
    recovery = null;
  }

  private static RuntimeControl runtimeControl(TunnelRuntimeManager runtime) {
    return new RuntimeControl() {
      @Override
      public boolean hasActiveTunnels() {
        return runtime.runtimes().values().stream()
            .anyMatch(
                value ->
                    value.actualState() != com.ssh.tunnelmanager.domain.tunnel.ActualState.CLOSED);
      }

      @Override
      public void stopAllAndAwait() {
        runtime.stopAll();
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        while (hasActiveTunnels() && System.nanoTime() < deadline) {
          try {
            Thread.sleep(25);
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
          }
        }
      }
    };
  }

  private static DiagnosticPort noOpDiagnostics() {
    return new PlaceholderDiagnosticPort();
  }

  private static NotificationPort noOpNotifications() {
    return new NotificationPort() {
      @Override
      public void notify(String title, String message) {}
    };
  }

  private static String safeMessage(Throwable error) {
    String message = error.getMessage();
    return message == null
        ? error.getClass().getSimpleName()
        : message.replaceAll("(?i)password\\s*[=:]\\s*\\S+", "password=[REDACTED]");
  }

  private static final class PlaceholderDiagnosticPort implements DiagnosticPort {
    @Override
    public Path export(Path target) {
      throw new UnsupportedOperationException("Diagnóstico no configurado");
    }

    @Override
    public Path logsDirectory() {
      return Path.of(".");
    }
  }

  private static BackupPort noOpBackups() {
    return new BackupPort() {
      @Override
      public void markDirty() {}

      @Override
      public Path backupIfDue() {
        return null;
      }

      @Override
      public Path beforeImport() {
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
      public void restore(Path backup) {
        throw new UnsupportedOperationException("Backups no configurados en este bootstrap");
      }
    };
  }

  private record RecoveryContext(
      DataDirectories directories,
      HostKeyApprovalCoordinator approvals,
      PersistenceException failure,
      BackupService backups) {}
}

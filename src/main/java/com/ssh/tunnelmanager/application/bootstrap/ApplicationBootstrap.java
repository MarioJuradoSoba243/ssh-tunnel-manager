package com.ssh.tunnelmanager.application.bootstrap;

import com.ssh.tunnelmanager.application.persistence.CredentialRepository;
import com.ssh.tunnelmanager.application.persistence.SshHostRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.runtime.ApplicationEventPublisher;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager;
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
import com.ssh.tunnelmanager.infrastructure.persistence.DataDirectories;
import com.ssh.tunnelmanager.infrastructure.persistence.DataDirectoryResolver;
import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import com.ssh.tunnelmanager.infrastructure.persistence.StorageMode;
import com.ssh.tunnelmanager.infrastructure.persistence.migration.DatabaseMigrator;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcCredentialRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcSshHostRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcTrustedHostKeyRepository;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcTunnelRepository;
import com.ssh.tunnelmanager.infrastructure.ssh.MinaSshTunnelEngine;
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
      CredentialRepository credentialRepository) {
    var requestProvider =
        new PersistentTunnelRequestProvider(tunnelRepository, hostRepository, credentialRepository);
    var runtime =
        new TunnelRuntimeManager(engine, requestProvider, new ApplicationEventPublisher(), 8);
    return new ApplicationComponents(
        new SaveSshHostUseCase(hostRepository, credentialRepository),
        new DeleteSshHostUseCase(hostRepository, tunnelRepository),
        new SaveTunnelUseCase(tunnelRepository),
        new DeleteTunnelUseCase(tunnelRepository),
        new DuplicateTunnelUseCase(tunnelRepository),
        new StartTunnelUseCase(runtime),
        new StopTunnelUseCase(runtime),
        new RetryTunnelUseCase(runtime),
        new TestTunnelConnectionUseCase(engine, requestProvider),
        runtime,
        engine,
        hostRepository,
        tunnelRepository,
        credentialRepository);
  }

  public static ApplicationBootstrap production(
      ApplicationInfo info, DataDirectories directories, HostKeyApprovalCoordinator approvals) {
    DataDirectoryResolver.createDirectories(directories);
    DatabaseFactory database = new DatabaseFactory(directories.database());
    new DatabaseMigrator(database).migrate();
    JdbcCredentialRepository credentials = new JdbcCredentialRepository(database);
    JdbcSshHostRepository hosts = new JdbcSshHostRepository(database);
    JdbcTunnelRepository tunnels = new JdbcTunnelRepository(database);
    JdbcTrustedHostKeyRepository trustedKeys = new JdbcTrustedHostKeyRepository(database);
    SshTunnelEngine engine = new MinaSshTunnelEngine(trustedKeys, approvals);
    ApplicationBootstrap bootstrap = new ApplicationBootstrap(info);
    bootstrap.components =
        bootstrap.createComponents(database, engine, tunnels, hosts, credentials);
    return bootstrap;
  }

  public static ApplicationBootstrap productionDefault(
      ApplicationInfo info, HostKeyApprovalCoordinator approvals) {
    Path applicationDirectory = applicationDirectory();
    String localAppData =
        System.getenv().getOrDefault("LOCALAPPDATA", applicationDirectory.toString());
    StorageMode mode =
        java.nio.file.Files.exists(applicationDirectory.resolve("portable.flag"))
            ? StorageMode.PORTABLE
            : StorageMode.INSTALLED;
    DataDirectories directories =
        DataDirectoryResolver.resolve(mode, applicationDirectory, Path.of(localAppData));
    return production(info, directories, approvals);
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

  private ApplicationComponents components;

  public ApplicationComponents components() {
    if (components == null)
      throw new IllegalStateException("El bootstrap no está configurado para producción");
    return components;
  }

  public boolean isProductionConfigured() {
    return components != null;
  }
}

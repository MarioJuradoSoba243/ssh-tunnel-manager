package com.ssh.tunnelmanager.infrastructure.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DataDirectoryResolver {
  private static final String APPLICATION_DIRECTORY = "SSH Tunnel Manager";

  private DataDirectoryResolver() {}

  public static DataDirectories forInstalled(Path localAppData) {
    return create(localAppData.resolve(APPLICATION_DIRECTORY));
  }

  public static DataDirectories forPortable(Path applicationDirectory) {
    return create(applicationDirectory.resolve("data"));
  }

  public static DataDirectories resolve(
      StorageMode mode, Path applicationDirectory, Path localAppData) {
    return mode == StorageMode.PORTABLE
        ? forPortable(applicationDirectory)
        : forInstalled(localAppData);
  }

  public static void createDirectories(DataDirectories directories) {
    try {
      Files.createDirectories(directories.dataDirectory());
      Files.createDirectories(directories.backups());
      Files.createDirectories(directories.logs());
    } catch (IOException exception) {
      throw new PersistenceException("No se pudieron crear los directorios de datos", exception);
    }
  }

  private static DataDirectories create(Path dataDirectory) {
    return new DataDirectories(
        dataDirectory,
        dataDirectory.resolve("ssh-tunnel-manager.db"),
        dataDirectory.resolve("backups"),
        dataDirectory.resolve("logs"));
  }
}

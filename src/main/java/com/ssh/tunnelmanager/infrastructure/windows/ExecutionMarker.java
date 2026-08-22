package com.ssh.tunnelmanager.infrastructure.windows;

import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Leaves a small marker while the process is alive; an unremoved marker means an abnormal exit. */
public final class ExecutionMarker implements AutoCloseable {
  private final Path marker;

  private ExecutionMarker(Path marker) {
    this.marker = marker;
  }

  public static ExecutionMarker open(Path marker) {
    try {
      Files.createDirectories(marker.toAbsolutePath().normalize().getParent());
      Files.writeString(
          marker,
          "pid=" + ProcessHandle.current().pid() + System.lineSeparator(),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
      return new ExecutionMarker(marker);
    } catch (IOException exception) {
      throw new PersistenceException("No se pudo preparar el marcador de ejecución", exception);
    }
  }

  public static boolean previousRunWasAbnormal(Path marker) {
    return Files.isRegularFile(marker);
  }

  @Override
  public void close() {
    try {
      Files.deleteIfExists(marker);
    } catch (IOException exception) {
      throw new PersistenceException("No se pudo limpiar el marcador de ejecución", exception);
    }
  }
}

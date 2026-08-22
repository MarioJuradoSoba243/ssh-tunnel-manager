package com.ssh.tunnelmanager.infrastructure.windows;

import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates one process per data directory and signals the primary window. */
public final class SingleInstanceCoordinator implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(SingleInstanceCoordinator.class);
  private static final String ACTIVATION = "ACTIVATE";
  private static final Duration RETRY_DELAY = Duration.ofMillis(50);

  private final FileChannel lockChannel;
  private final FileLock lock;
  private final ServerSocket server;
  private final Path portFile;
  private final ExecutorService listener;
  private final Runnable activate;

  private SingleInstanceCoordinator(
      FileChannel lockChannel,
      FileLock lock,
      ServerSocket server,
      Path portFile,
      ExecutorService listener,
      Runnable activate) {
    this.lockChannel = lockChannel;
    this.lock = lock;
    this.server = server;
    this.portFile = portFile;
    this.listener = listener;
    this.activate = activate;
  }

  public static SingleInstanceCoordinator acquire(Path dataDirectory, Runnable activate) {
    try {
      Files.createDirectories(dataDirectory);
      Path lockPath = dataDirectory.resolve("application.instance.lock");
      Path portPath = dataDirectory.resolve("application.instance.port");
      FileChannel channel =
          FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      FileLock fileLock = tryLock(channel);
      if (fileLock == null) {
        boolean signalled = signal(portPath);
        channel.close();
        if (!signalled) {
          throw new PersistenceException(
              "Ya existe otra instancia del gestor y no se pudo activar su ventana");
        }
        return new SingleInstanceCoordinator(null, null, null, portPath, null, activate);
      }

      ServerSocket socket = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
      Files.writeString(
          portPath,
          Integer.toString(socket.getLocalPort()),
          StandardCharsets.US_ASCII,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
      ExecutorService listener = Executors.newVirtualThreadPerTaskExecutor();
      SingleInstanceCoordinator coordinator =
          new SingleInstanceCoordinator(channel, fileLock, socket, portPath, listener, activate);
      listener.submit(coordinator::listen);
      return coordinator;
    } catch (IOException exception) {
      throw new PersistenceException("No se pudo coordinar la instancia única", exception);
    }
  }

  public boolean isPrimary() {
    return lock != null;
  }

  private static FileLock tryLock(FileChannel channel) throws IOException {
    for (int attempt = 0; attempt < 10; attempt++) {
      try {
        FileLock lock = channel.tryLock();
        if (lock != null) return lock;
      } catch (OverlappingFileLockException exception) {
        // Another process in this JVM owns the same lock.
      }
      try {
        Thread.sleep(RETRY_DELAY.toMillis());
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return null;
      }
    }
    return null;
  }

  private static boolean signal(Path portFile) {
    try {
      if (!Files.isRegularFile(portFile)) return false;
      int port = Integer.parseInt(Files.readString(portFile).trim());
      try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
        socket.getOutputStream().write(ACTIVATION.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
      }
      return true;
    } catch (IOException | NumberFormatException exception) {
      return false;
    }
  }

  private void listen() {
    while (!server.isClosed()) {
      try (Socket ignored = server.accept()) {
        activate.run();
      } catch (IOException exception) {
        if (!server.isClosed()) {
          throw new PersistenceException("Falló el listener de instancia única", exception);
        }
      } catch (RuntimeException exception) {
        LOGGER.warn("No se pudo activar la ventana de la instancia primaria", exception);
      }
    }
  }

  @Override
  public void close() {
    if (!isPrimary()) return;
    try {
      server.close();
      Files.deleteIfExists(portFile);
      lock.release();
      lockChannel.close();
      listener.close();
    } catch (IOException exception) {
      throw new PersistenceException("No se pudo liberar la instancia única", exception);
    }
  }
}

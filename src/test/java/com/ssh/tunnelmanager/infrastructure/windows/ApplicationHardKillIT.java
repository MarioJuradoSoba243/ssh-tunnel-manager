package com.ssh.tunnelmanager.infrastructure.windows;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssh.tunnelmanager.application.runtime.ApplicationEventPublisher;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager;
import com.ssh.tunnelmanager.application.ssh.SshTunnelEngine;
import com.ssh.tunnelmanager.application.ssh.TunnelConnectionRequest;
import com.ssh.tunnelmanager.application.ssh.TunnelHandle;
import com.ssh.tunnelmanager.application.tunnel.TunnelRequestProvider;
import com.ssh.tunnelmanager.domain.tunnel.ActualState;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the operating-system-visible part of abnormal application termination. */
class ApplicationHardKillIT {
  @Test
  void hardKillReleasesListenerDetectsCrashAndStartsClosed(@TempDir Path directory)
      throws Exception {
    Path marker = directory.resolve("application.run.marker");
    Path ready = directory.resolve("probe.ready");
    Path restart = directory.resolve("restart.result");
    int localPort = freePort();
    Process probe = launch("open", marker, ready, restart, localPort);
    try {
      awaitFile(ready);
      assertThat(probe.isAlive()).isTrue();
      assertThat(canConnect(localPort)).isTrue();
      assertThat(
              probe.toHandle().descendants().toList().stream()
                  .map(handle -> handle.info().command().orElse("").toLowerCase())
                  .filter(ApplicationHardKillIT::isExternalSshCommand)
                  .toList())
          .as("the application probe must not create external SSH processes")
          .isEmpty();

      probe.destroyForcibly();
      assertThat(probe.waitFor(10, TimeUnit.SECONDS)).isTrue();
      assertThat(probe.isAlive()).isFalse();
      awaitPortFree(localPort);
      assertThat(ExecutionMarker.previousRunWasAbnormal(marker)).isTrue();

      Process nextStartup = launch("restart", marker, ready, restart, localPort);
      assertThat(nextStartup.waitFor(10, TimeUnit.SECONDS)).isTrue();
      assertThat(nextStartup.exitValue()).isZero();
      assertThat(Files.readString(restart, StandardCharsets.UTF_8))
          .contains("previousRunWasAbnormal=true")
          .contains("runtimeState=CLOSED")
          .contains("restoredOpenTunnels=0");
      assertThat(ExecutionMarker.previousRunWasAbnormal(marker)).isFalse();
    } finally {
      if (probe.isAlive()) probe.destroyForcibly();
    }
  }

  private static Process launch(String mode, Path marker, Path ready, Path result, int localPort)
      throws IOException {
    String javaExecutable =
        Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java")
            .toString();
    return new ProcessBuilder(
            List.of(
                javaExecutable,
                "-Dfile.encoding=UTF-8",
                "-cp",
                System.getProperty("java.class.path"),
                ApplicationHardKillIT.HardKillProbeMain.class.getName(),
                mode,
                marker.toString(),
                ready.toString(),
                result.toString(),
                Integer.toString(localPort)))
        .redirectErrorStream(true)
        .start();
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
  }

  private static boolean isExternalSshCommand(String command) {
    return command.contains("\\ssh.exe")
        || command.endsWith("/ssh")
        || command.contains("\\sshd.exe")
        || command.endsWith("/sshd")
        || command.contains("\\plink.exe")
        || command.endsWith("/plink");
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
      return socket.getLocalPort();
    }
  }

  private static boolean canConnect(int port) {
    try (java.net.Socket socket = new java.net.Socket()) {
      socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 500);
      return true;
    } catch (IOException exception) {
      return false;
    }
  }

  private static void awaitFile(Path file) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (!Files.isRegularFile(file) && System.nanoTime() < deadline) {
      Thread.sleep(50);
    }
    assertThat(Files.isRegularFile(file)).as("probe readiness file").isTrue();
  }

  private static void awaitPortFree(int port) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    IOException lastFailure = null;
    while (System.nanoTime() < deadline) {
      try (ServerSocket socket = new ServerSocket()) {
        socket.setReuseAddress(false);
        socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
        return;
      } catch (IOException exception) {
        lastFailure = exception;
        Thread.sleep(50);
      }
    }
    throw new AssertionError("El listener no se liberó: " + lastFailure);
  }

  public static final class HardKillProbeMain {
    private HardKillProbeMain() {}

    public static void main(String[] args) throws Exception {
      String mode = args[0];
      Path marker = Path.of(args[1]);
      Path ready = Path.of(args[2]);
      Path result = Path.of(args[3]);
      int localPort = Integer.parseInt(args[4]);
      if ("open".equals(mode)) {
        openResources(marker, ready, localPort);
      } else if ("restart".equals(mode)) {
        verifyRestart(marker, result);
      } else {
        throw new IllegalArgumentException("Modo de probe desconocido: " + mode);
      }
    }

    private static void openResources(Path marker, Path ready, int localPort) throws Exception {
      try (ExecutionMarker ignored = ExecutionMarker.open(marker);
          ServerSocket listener = new ServerSocket()) {
        listener.setReuseAddress(false);
        listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), localPort));
        Files.writeString(
            ready,
            "pid=" + ProcessHandle.current().pid() + System.lineSeparator(),
            StandardCharsets.UTF_8);
        Thread.sleep(Duration.ofMinutes(5));
      }
    }

    private static void verifyRestart(Path marker, Path result) throws IOException {
      boolean abnormal = ExecutionMarker.previousRunWasAbnormal(marker);
      SshTunnelEngine noOpEngine =
          new SshTunnelEngine() {
            @Override
            public TunnelHandle openTunnel(TunnelConnectionRequest request) {
              throw new UnsupportedOperationException();
            }

            @Override
            public com.ssh.tunnelmanager.application.ssh.ConnectionTestResult testConnection(
                TunnelConnectionRequest request) {
              throw new UnsupportedOperationException();
            }

            @Override
            public void closeTunnel(TunnelHandle handle) {}
          };
      TunnelRequestProvider noOpRequests =
          tunnelId -> {
            throw new UnsupportedOperationException();
          };
      try (ExecutionMarker ignored = ExecutionMarker.open(marker);
          TunnelRuntimeManager runtime =
              new TunnelRuntimeManager(
                  noOpEngine, noOpRequests, new ApplicationEventPublisher(), 1)) {
        UUID tunnelId = UUID.randomUUID();
        ActualState state = runtime.runtime(tunnelId).actualState();
        Files.writeString(
            result,
            "previousRunWasAbnormal="
                + abnormal
                + System.lineSeparator()
                + "runtimeState="
                + state
                + System.lineSeparator()
                + "restoredOpenTunnels=0"
                + System.lineSeparator(),
            StandardCharsets.UTF_8);
      }
    }
  }
}

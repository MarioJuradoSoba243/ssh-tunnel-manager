package com.ssh.tunnelmanager.infrastructure.ssh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssh.tunnelmanager.application.ssh.SerialHostKeyApprovalCoordinator;
import com.ssh.tunnelmanager.application.ssh.TunnelConnectionRequest;
import com.ssh.tunnelmanager.application.ssh.TunnelHandle;
import com.ssh.tunnelmanager.domain.credential.Credential;
import com.ssh.tunnelmanager.domain.tunnel.LocalForward;
import com.ssh.tunnelmanager.domain.tunnel.RemoteDestination;
import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import com.ssh.tunnelmanager.infrastructure.persistence.migration.DatabaseMigrator;
import com.ssh.tunnelmanager.infrastructure.persistence.repository.JdbcTrustedHostKeyRepository;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "docker.spike", matches = "true")
class MinaSshTunnelEngineIT {
  private static final Path COMPOSE = Path.of("src/test/resources/ssh-spike/docker-compose.yml");
  private static Path databasePath;
  private static MinaSshTunnelEngine engine;

  @BeforeAll
  static void startTopology() throws Exception {
    compose("up", "-d");
    databasePath = Files.createTempFile("mina-engine-it-", ".db");
    Files.deleteIfExists(databasePath);
    DatabaseFactory database = new DatabaseFactory(databasePath);
    new DatabaseMigrator(database).migrate();
    engine =
        new MinaSshTunnelEngine(
            new JdbcTrustedHostKeyRepository(database),
            new SerialHostKeyApprovalCoordinator(request -> true));
  }

  @AfterAll
  static void stopTopology() throws Exception {
    compose("up", "-d");
    if (databasePath != null) Files.deleteIfExists(databasePath);
  }

  @Test
  void opensDirectOneJumpAndTwoJumpRoutesWithLocalForwarding() throws Exception {
    for (Route route : routes()) {
      TunnelConnectionRequest request = request(route, route.port());
      try (TunnelHandle ignored = engine.openTunnel(request);
          Socket socket = new Socket("127.0.0.1", route.port())) {
        socket.setSoTimeout(5_000);
        assertThat(
                new String(socket.getInputStream().readNBytes(18), StandardCharsets.UTF_8).trim())
            .isEqualTo("ssh-spike-target");
      }
    }
  }

  @Test
  void testConnectionDoesNotUseConfiguredLocalPortAndOccupiedPortIsRejected() throws Exception {
    Route direct = routes().getFirst();
    TunnelConnectionRequest request = request(direct, 23410);
    try (Socket occupied = new Socket()) {
      occupied.bind(new java.net.InetSocketAddress("127.0.0.1", 23410));
      assertThat(engine.testConnection(request).successful()).isTrue();
      assertThatThrownBy(() -> engine.openTunnel(request))
          .isInstanceOf(MinaSshTunnelEngine.LocalPortInUseException.class)
          .hasMessageContaining("23410");
    }
  }

  @Test
  void opensTwentyIndependentRoutesConcurrentlyAndClosesAllResources() throws Exception {
    List<TunnelHandle> handles = new ArrayList<>();
    try {
      try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
        Semaphore connectionWindow = new Semaphore(4);
        List<Future<TunnelHandle>> futures = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
          int localPort = 23500 + index;
          futures.add(
              workers.submit(
                  () -> {
                    connectionWindow.acquire();
                    try {
                      return engine.openTunnel(
                          request(
                              new Route(
                                  List.of(hop("127.0.0.1", 2223, "final", "final-password")),
                                  localPort),
                              localPort));
                    } finally {
                      connectionWindow.release();
                    }
                  }));
        }
        for (Future<TunnelHandle> future : futures) handles.add(future.get(30, TimeUnit.SECONDS));
      }

      for (int index = 0; index < 20; index++) {
        try (Socket socket = new Socket("127.0.0.1", 23500 + index)) {
          assertThat(socket.isConnected()).isTrue();
        }
      }
    } finally {
      handles.forEach(TunnelHandle::close);
    }

    for (int index = 0; index < 20; index++) {
      int port = 23500 + index;
      try (Socket probe = new Socket()) {
        assertThatThrownBy(
                () -> probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500))
            .isInstanceOfAny(
                java.net.ConnectException.class, java.net.SocketTimeoutException.class);
      }
    }
  }

  private static List<Route> routes() {
    return List.of(
        new Route(List.of(hop("127.0.0.1", 2223, "final", "final-password")), 23401),
        new Route(
            List.of(
                hop("127.0.0.1", 2221, "jump1", "jump1-password"),
                hop("ssh-final", 22, "final", "final-password")),
            23402),
        new Route(
            List.of(
                hop("127.0.0.1", 2221, "jump1", "jump1-password"),
                hop("jump-2", 22, "jump2", "jump2-password"),
                hop("ssh-final", 22, "final", "final-password")),
            23403));
  }

  private static TunnelConnectionRequest request(Route route, int localPort) {
    return new TunnelConnectionRequest(
        new LocalForward("127.0.0.1", localPort),
        new RemoteDestination("tcp-target", 5432),
        route.hops(),
        Duration.ofSeconds(10),
        Duration.ofSeconds(30),
        java.util.Map.of());
  }

  private static TunnelConnectionRequest.RouteHop hop(
      String host, int port, String user, String password) {
    return new TunnelConnectionRequest.RouteHop(
        host, port, new Credential(user, password), user.equals("final") ? "final" : user);
  }

  private static void compose(String... arguments) throws Exception {
    List<String> command = new ArrayList<>();
    command.add(
        System.getenv("LOCALAPPDATA") + "\\Programs\\DockerDesktop\\resources\\bin\\docker.exe");
    command.add("compose");
    command.add("-f");
    command.add(COMPOSE.toString());
    command.addAll(List.of(arguments));
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor(30, TimeUnit.SECONDS)).as(output).isTrue();
    assertThat(process.exitValue()).as(output).isZero();
  }

  private record Route(List<TunnelConnectionRequest.RouteHop> hops, int port) {}
}

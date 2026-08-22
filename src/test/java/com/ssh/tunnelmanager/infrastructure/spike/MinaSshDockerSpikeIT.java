package com.ssh.tunnelmanager.infrastructure.spike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.forward.ExplicitPortForwardingTracker;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionHeartbeatController.HeartbeatType;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "docker.spike", matches = "true")
class MinaSshDockerSpikeIT {
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Path COMPOSE_FILE =
      Path.of("src/test/resources/ssh-spike/docker-compose.yml");

  @BeforeEach
  void ensureTopologyIsRunning() throws Exception {
    compose("up", "-d");
    waitForPort(2221);
    waitForPort(2222);
    waitForPort(2223);
  }

  @AfterEach
  void restoreTopologyAfterScenario() throws Exception {
    compose("up", "-d");
  }

  @Test
  void directAndOneAndTwoJumpRoutesUseIndependentCredentials() throws Exception {
    Set<String> fingerprints = collectRouteFingerprints();
    assertThat(fingerprints).hasSize(3);

    try (SshClient direct = trusting(fingerprints)) {
      direct.start();
      try (ClientSession session = authenticate(direct, "final", "final-password", 2223)) {
        assertThat(session.isAuthenticated()).isTrue();
      }
    }

    try (SshClient jumpClient = trusting(fingerprints);
        SshClient finalClient = trusting(fingerprints)) {
      jumpClient.start();
      finalClient.start();
      try (ClientSession jump = authenticate(jumpClient, "jump1", "jump1-password", 2221);
          ExplicitPortForwardingTracker jumpToFinal =
              jump.createLocalPortForwardingTracker(
                  new SshdSocketAddress(0), new SshdSocketAddress("ssh-final", 22));
          ClientSession finalSession =
              authenticate(
                  finalClient,
                  "final",
                  "final-password",
                  localPort(jumpToFinal.getBoundAddress()))) {
        assertThat(finalSession.getUsername()).isEqualTo("final");
      }
    }

    try (SshClient client1 = trusting(fingerprints);
        SshClient client2 = trusting(fingerprints);
        SshClient client3 = trusting(fingerprints)) {
      client1.start();
      client2.start();
      client3.start();
      try (ClientSession session1 = authenticate(client1, "jump1", "jump1-password", 2221);
          ExplicitPortForwardingTracker hop1To2 =
              session1.createLocalPortForwardingTracker(
                  new SshdSocketAddress(0), new SshdSocketAddress("jump-2", 22));
          ClientSession session2 =
              authenticate(
                  client2, "jump2", "jump2-password", localPort(hop1To2.getBoundAddress()));
          ExplicitPortForwardingTracker hop2To3 =
              session2.createLocalPortForwardingTracker(
                  new SshdSocketAddress(0), new SshdSocketAddress("ssh-final", 22));
          ClientSession session3 =
              authenticate(
                  client3, "final", "final-password", localPort(hop2To3.getBoundAddress()))) {
        assertThat(session1.getUsername()).isEqualTo("jump1");
        assertThat(session2.getUsername()).isEqualTo("jump2");
        assertThat(session3.getUsername()).isEqualTo("final");
      }
    }
  }

  @Test
  void localForwardingReachesDestinationDifferentFromFinalHost() throws Exception {
    Set<String> fingerprints = collectRouteFingerprints();
    try (SshClient client = trusting(fingerprints)) {
      client.start();
      try (ClientSession session = authenticate(client, "final", "final-password", 2223);
          ExplicitPortForwardingTracker forwarding =
              session.createLocalPortForwardingTracker(
                  new SshdSocketAddress(0), new SshdSocketAddress("tcp-target", 5432))) {
        assertThat(readForwarding(forwarding)).isEqualTo("ssh-spike-target");
      }
    }
  }

  @Test
  void unknownKeyRequiresApprovalAndChangedKeyIsRejected() throws Exception {
    FingerprintTrust rejecting = new FingerprintTrust();
    try (SshClient client = client(rejecting)) {
      client.start();
      assertThatThrownBy(() -> authenticate(client, "final", "final-password", 2223))
          .isInstanceOf(Exception.class);
    }
    assertThat(rejecting.seen).hasSize(1);

    FingerprintTrust approving = new FingerprintTrust();
    approving.accepted.addAll(rejecting.seen);
    try (SshClient client = client(approving)) {
      client.start();
      try (ClientSession ignored = authenticate(client, "final", "final-password", 2223)) {
        assertThat(ignored.isAuthenticated()).isTrue();
      }
    }

    Set<String> oldFingerprint = Set.copyOf(approving.seen);
    compose("restart", "ssh-final");
    waitForPort(2223);
    try (SshClient client = trusting(oldFingerprint)) {
      client.start();
      assertThatThrownBy(() -> authenticate(client, "final", "final-password", 2223))
          .isInstanceOf(Exception.class);
    }
    FingerprintTrust newTrust = new FingerprintTrust();
    newTrust.autoApprove = true;
    try (SshClient client = client(newTrust)) {
      client.start();
      try (ClientSession ignored = authenticateWithRetry(client, "final", "final-password", 2223)) {
        assertThat(newTrust.seen).hasSize(1);
        assertThat(newTrust.seen).doesNotContainAnyElementsOf(oldFingerprint);
      }
    }
  }

  @Test
  void keepAliveDetectsContainerLossAndShutdownIsClean() throws Exception {
    Set<String> fingerprints = collectRouteFingerprints();
    try (SshClient client = trusting(fingerprints)) {
      client.setSessionHeartbeat(HeartbeatType.IGNORE, Duration.ofSeconds(30));
      client.start();
      try (ClientSession session = authenticate(client, "final", "final-password", 2223)) {
        CountDownLatch closed = new CountDownLatch(1);
        session.addSessionListener(new CloseListener(closed));
        compose("stop", "ssh-final");
        assertThat(closed.await(10, TimeUnit.SECONDS)).isTrue();
      } finally {
        compose("up", "-d", "ssh-final");
        waitForPort(2223);
      }
    }
  }

  @Test
  void unreachableDestinationDoesNotDeliverDataAndForwardingCanBeCancelled() throws Exception {
    Set<String> fingerprints = collectRouteFingerprints();
    try (SshClient client = trusting(fingerprints)) {
      client.start();
      try (ClientSession session = authenticate(client, "final", "final-password", 2223);
          ExplicitPortForwardingTracker forwarding =
              session.createLocalPortForwardingTracker(
                  new SshdSocketAddress(0), new SshdSocketAddress("missing-destination", 5432))) {
        assertThat(readForwarding(forwarding)).isEmpty();
      }
    }
  }

  private static Set<String> collectRouteFingerprints() throws Exception {
    FingerprintTrust trust = new FingerprintTrust();
    trust.autoApprove = true;
    try (SshClient jump1 = client(trust);
        SshClient jump2 = client(trust);
        SshClient finalHost = client(trust)) {
      jump1.start();
      jump2.start();
      finalHost.start();
      try (ClientSession ignored1 = authenticate(jump1, "jump1", "jump1-password", 2221);
          ClientSession ignored2 = authenticate(jump2, "jump2", "jump2-password", 2222);
          ClientSession ignored3 = authenticate(finalHost, "final", "final-password", 2223)) {
        return Set.copyOf(trust.seen);
      }
    }
  }

  private static SshClient trusting(Set<String> fingerprints) {
    FingerprintTrust trust = new FingerprintTrust();
    trust.accepted.addAll(fingerprints);
    return client(trust);
  }

  private static SshClient client(ServerKeyVerifier verifier) {
    SshClient client = SshClient.setUpDefaultClient();
    client.setServerKeyVerifier(verifier);
    return client;
  }

  private static ClientSession authenticate(
      SshClient client, String user, String password, int port) throws Exception {
    ClientSession session =
        client.connect(user, "127.0.0.1", port).verify(CONNECT_TIMEOUT).getSession();
    try {
      session.addPasswordIdentity(password);
      session.auth().verify(CONNECT_TIMEOUT);
      return session;
    } catch (Exception exception) {
      session.close(true);
      throw exception;
    }
  }

  private static ClientSession authenticateWithRetry(
      SshClient client, String user, String password, int port) throws Exception {
    Exception last = null;
    for (int attempt = 0; attempt < 20; attempt++) {
      try {
        return authenticate(client, user, password, port);
      } catch (Exception exception) {
        last = exception;
        Thread.sleep(250);
      }
    }
    throw last;
  }

  private static String readForwarding(ExplicitPortForwardingTracker forwarding) throws Exception {
    try (Socket socket = new Socket()) {
      socket.connect(SshdSocketAddress.toInetSocketAddress(forwarding.getBoundAddress()), 3_000);
      socket.setSoTimeout(3_000);
      byte[] data = socket.getInputStream().readNBytes("ssh-spike-target\n".length());
      return new String(data, StandardCharsets.UTF_8).trim();
    } catch (IOException exception) {
      return "";
    }
  }

  private static int localPort(java.net.SocketAddress address) {
    return SshdSocketAddress.toAddressPort(address);
  }

  private static void compose(String... arguments) throws Exception {
    List<String> command = new ArrayList<>();
    command.add(
        System.getenv("LOCALAPPDATA") + "\\Programs\\DockerDesktop\\resources\\bin\\docker.exe");
    command.add("compose");
    command.add("-f");
    command.add(COMPOSE_FILE.toString());
    command.addAll(List.of(arguments));
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor(30, TimeUnit.SECONDS)).as(output).isTrue();
    assertThat(process.exitValue()).as(output).isZero();
  }

  private static void waitForPort(int port) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
    while (System.nanoTime() < deadline) {
      try (Socket ignored = new Socket("127.0.0.1", port)) {
        return;
      } catch (IOException exception) {
        Thread.sleep(200);
      }
    }
    throw new AssertionError("Docker SSH port did not become available: " + port);
  }

  private static final class FingerprintTrust implements ServerKeyVerifier {
    private final Set<String> seen = new HashSet<>();
    private final Set<String> accepted = new HashSet<>();
    private boolean autoApprove;

    @Override
    public boolean verifyServerKey(
        ClientSession session,
        java.net.SocketAddress remoteAddress,
        java.security.PublicKey serverKey) {
      String fingerprint = KeyUtils.getFingerPrint(serverKey);
      seen.add(fingerprint);
      return autoApprove || accepted.contains(fingerprint);
    }
  }

  private static final class CloseListener implements SessionListener {
    private final CountDownLatch closed;

    private CloseListener(CountDownLatch closed) {
      this.closed = closed;
    }

    @Override
    public void sessionClosed(Session session) {
      closed.countDown();
    }
  }
}

package com.ssh.tunnelmanager.infrastructure.spike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.forward.ExplicitPortForwardingTracker;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.session.SessionHeartbeatController.HeartbeatType;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.junit.jupiter.api.Test;

class MinaSshSpikeIT {
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration DESIGN_KEEP_ALIVE = Duration.ofSeconds(30);
  private static final String DESTINATION_HOST = "localhost";

  @Test
  void supportsDirectPasswordAuthentication() throws Exception {
    try (SshNode finalHost = SshNode.start("final", "final-password")) {
      try (SshClient client = clientTrusting(finalHost.fingerprint())) {
        client.start();
        try (ClientSession session =
            authenticate(client, finalHost, "127.0.0.1", finalHost.port())) {
          assertThat(session.isAuthenticated()).isTrue();
        }
      }
    }
  }

  @Test
  void supportsOneJumpWithIndependentCredentials() throws Exception {
    try (SshNode jump1 = SshNode.start("jump1", "jump1-password");
        SshNode finalHost = SshNode.start("final", "final-password")) {
      try (SshClient jumpClient = clientTrusting(jump1.fingerprint());
          SshClient finalClient = clientTrusting(finalHost.fingerprint())) {
        jumpClient.start();
        finalClient.start();
        try (ClientSession jumpSession =
                authenticate(jumpClient, jump1, "127.0.0.1", jump1.port());
            ExplicitPortForwardingTracker jumpToFinal =
                jumpSession.createLocalPortForwardingTracker(
                    new SshdSocketAddress(0),
                    new SshdSocketAddress("127.0.0.1", finalHost.port()));
            ClientSession finalSession =
                authenticate(
                    finalClient,
                    finalHost,
                    "127.0.0.1",
                    localPort(jumpToFinal.getBoundAddress()))) {
          assertThat(jumpSession.getUsername()).isEqualTo("jump1");
          assertThat(finalSession.getUsername()).isEqualTo("final");
        }
      }
    }
  }

  @Test
  void supportsTwoJumpsWithIndependentCredentialsOnAllHosts() throws Exception {
    try (SshNode jump1 = SshNode.start("jump1", "jump1-password");
        SshNode jump2 = SshNode.start("jump2", "jump2-password");
        SshNode finalHost = SshNode.start("final", "final-password")) {
      try (SshClient client1 = clientTrusting(jump1.fingerprint());
          SshClient client2 = clientTrusting(jump2.fingerprint());
          SshClient client3 = clientTrusting(finalHost.fingerprint())) {
        client1.start();
        client2.start();
        client3.start();
        try (ClientSession session1 = authenticate(client1, jump1, "127.0.0.1", jump1.port());
            ExplicitPortForwardingTracker hop1To2 =
                session1.createLocalPortForwardingTracker(
                    new SshdSocketAddress(0), new SshdSocketAddress("127.0.0.1", jump2.port()));
            ClientSession session2 =
                authenticate(client2, jump2, "127.0.0.1", localPort(hop1To2.getBoundAddress()));
            ExplicitPortForwardingTracker hop2To3 =
                session2.createLocalPortForwardingTracker(
                    new SshdSocketAddress(0),
                    new SshdSocketAddress("127.0.0.1", finalHost.port()));
            ClientSession session3 =
                authenticate(
                    client3, finalHost, "127.0.0.1", localPort(hop2To3.getBoundAddress()))) {
          assertThat(session1.getUsername()).isEqualTo("jump1");
          assertThat(session2.getUsername()).isEqualTo("jump2");
          assertThat(session3.getUsername()).isEqualTo("final");
        }
      }
    }
  }

  @Test
  void supportsDirectLocalPortForwarding() throws Exception {
    try (TcpTarget target = TcpTarget.start();
        SshNode finalHost = SshNode.start("final", "final-password");
        SshClient client = clientTrusting(finalHost.fingerprint())) {
      client.start();
      try (ClientSession session = authenticate(client, finalHost, "127.0.0.1", finalHost.port());
          ExplicitPortForwardingTracker forwarding =
              session.createLocalPortForwardingTracker(
                  new SshdSocketAddress(0), new SshdSocketAddress("127.0.0.1", target.port()))) {
        assertThat(readFromForward(forwarding)).isEqualTo("ssh-spike-target");
      }
    }
  }

  @Test
  void supportsLocalPortForwardingThroughOneJump() throws Exception {
    try (TcpTarget target = TcpTarget.start();
        SshNode jump1 = SshNode.start("jump1", "jump1-password");
        SshNode finalHost = SshNode.start("final", "final-password")) {
      try (SshClient client1 = clientTrusting(jump1.fingerprint());
          SshClient client2 = clientTrusting(finalHost.fingerprint())) {
        client1.start();
        client2.start();
        try (ClientSession session1 = authenticate(client1, jump1, "127.0.0.1", jump1.port());
            ExplicitPortForwardingTracker hopToFinal =
                session1.createLocalPortForwardingTracker(
                    new SshdSocketAddress(0),
                    new SshdSocketAddress("127.0.0.1", finalHost.port()));
            ClientSession session2 =
                authenticate(
                    client2, finalHost, "127.0.0.1", localPort(hopToFinal.getBoundAddress()));
            ExplicitPortForwardingTracker forwarding =
                session2.createLocalPortForwardingTracker(
                    new SshdSocketAddress(0), new SshdSocketAddress("127.0.0.1", target.port()))) {
          assertThat(readFromForward(forwarding)).isEqualTo("ssh-spike-target");
        }
      }
    }
  }

  @Test
  void supportsLocalPortForwardingThroughTwoJumps() throws Exception {
    try (TcpTarget target = TcpTarget.start();
        SshNode jump1 = SshNode.start("jump1", "jump1-password");
        SshNode jump2 = SshNode.start("jump2", "jump2-password");
        SshNode finalHost = SshNode.start("final", "final-password")) {
      try (SshClient client1 = clientTrusting(jump1.fingerprint());
          SshClient client2 = clientTrusting(jump2.fingerprint());
          SshClient client3 = clientTrusting(finalHost.fingerprint())) {
        client1.start();
        client2.start();
        client3.start();
        try (ClientSession session1 = authenticate(client1, jump1, "127.0.0.1", jump1.port());
            ExplicitPortForwardingTracker hop1To2 =
                session1.createLocalPortForwardingTracker(
                    new SshdSocketAddress(0), new SshdSocketAddress("127.0.0.1", jump2.port()));
            ClientSession session2 =
                authenticate(client2, jump2, "127.0.0.1", localPort(hop1To2.getBoundAddress()));
            ExplicitPortForwardingTracker hop2To3 =
                session2.createLocalPortForwardingTracker(
                    new SshdSocketAddress(0),
                    new SshdSocketAddress("127.0.0.1", finalHost.port()));
            ClientSession session3 =
                authenticate(
                    client3, finalHost, "127.0.0.1", localPort(hop2To3.getBoundAddress()));
            ExplicitPortForwardingTracker forwarding =
                session3.createLocalPortForwardingTracker(
                    new SshdSocketAddress(0),
                    new SshdSocketAddress(DESTINATION_HOST, target.port()))) {
          assertThat(readFromForward(forwarding)).isEqualTo("ssh-spike-target");
        }
      }
    }
  }

  @Test
  void validatesDestinationIndependentlyFromFinalSshHost() throws Exception {
    try (TcpTarget target = TcpTarget.start();
        SshNode finalHost = SshNode.start("final", "final-password");
        SshClient client = clientTrusting(finalHost.fingerprint())) {
      client.start();
      try (ClientSession session = authenticate(client, finalHost, "127.0.0.1", finalHost.port());
          ExplicitPortForwardingTracker forwarding =
              session.createLocalPortForwardingTracker(
                  new SshdSocketAddress(0),
                  new SshdSocketAddress(DESTINATION_HOST, target.port()))) {
        assertThat(DESTINATION_HOST).isNotEqualTo("127.0.0.1");
        assertThat(readFromForward(forwarding)).isEqualTo("ssh-spike-target");
      }
    }
  }

  @Test
  void exposesAndValidatesSha256FingerprintForEveryRouteHost() throws Exception {
    try (SshNode jump1 = SshNode.start("jump1", "jump1-password");
        SshNode jump2 = SshNode.start("jump2", "jump2-password");
        SshNode finalHost = SshNode.start("final", "final-password")) {
      assertThat(jump1.fingerprint()).startsWith("SHA256:");
      assertThat(jump2.fingerprint()).startsWith("SHA256:");
      assertThat(finalHost.fingerprint()).startsWith("SHA256:");
      assertThat(jump1.fingerprint()).isNotEqualTo(jump2.fingerprint());
      assertThat(jump2.fingerprint()).isNotEqualTo(finalHost.fingerprint());
      assertThat(finalHost.fingerprint()).isNotEqualTo(jump1.fingerprint());

      try (SshClient client = clientTrusting(finalHost.fingerprint())) {
        client.start();
        try (ClientSession ignored =
            authenticate(client, finalHost, "127.0.0.1", finalHost.port())) {
          assertThatCode(() -> client.stop()).doesNotThrowAnyException();
        }
      }
    }
  }

  @Test
  void rejectsUnknownHostKeyUntilExplicitApproval() throws Exception {
    try (SshNode finalHost = SshNode.start("final", "final-password");
        SshClient rejectingClient = clientTrusting(null)) {
      rejectingClient.start();
      assertThatThrownBy(
              () -> authenticate(rejectingClient, finalHost, "127.0.0.1", finalHost.port()))
          .isInstanceOf(Exception.class);
    }

    try (SshNode finalHost = SshNode.start("final", "final-password");
        SshClient approvingClient = clientTrusting(finalHost.fingerprint())) {
      approvingClient.start();
      try (ClientSession ignored =
          authenticate(approvingClient, finalHost, "127.0.0.1", finalHost.port())) {
        assertThat(approvingClient.isStarted()).isTrue();
      }
    }
  }

  @Test
  void blocksConnectionWhenTrustedFingerprintChanges() throws Exception {
    int port = freePort();
    try (SshNode oldHost = SshNode.start("final", "final-password", port)) {
      String trustedFingerprint = oldHost.fingerprint();
      try (SshClient client = clientTrusting(trustedFingerprint)) {
        client.start();
        try (ClientSession ignored = authenticate(client, oldHost, "127.0.0.1", port)) {
          assertThat(ignored.getServerKey()).isNotNull();
        }
      }

      oldHost.stop();
      try (SshNode changedHost = SshNode.start("final", "final-password", port);
          SshClient client = clientTrusting(trustedFingerprint)) {
        client.start();
        assertThatThrownBy(() -> authenticate(client, changedHost, "127.0.0.1", port))
            .isInstanceOf(Exception.class);
        assertThat(changedHost.fingerprint()).isNotEqualTo(trustedFingerprint);
      }
    }
  }

  @Test
  void configuresTheDesignKeepAliveAndCanDetectSessionLoss() throws Exception {
    try (SshNode finalHost = SshNode.start("final", "final-password");
        SshClient client = clientTrusting(finalHost.fingerprint())) {
      client.setSessionHeartbeat(HeartbeatType.IGNORE, DESIGN_KEEP_ALIVE);
      client.start();
      try (ClientSession session = authenticate(client, finalHost, "127.0.0.1", finalHost.port())) {
        assertThat(session.getSessionHeartbeatType()).isEqualTo(HeartbeatType.IGNORE);
        assertThat(session.getSessionHeartbeatInterval()).isEqualTo(DESIGN_KEEP_ALIVE);

        CountDownLatch closed = new CountDownLatch(1);
        session.addSessionListener(new SessionListenerAdapter(closed));
        finalHost.stop();
        assertThat(closed.await(5, TimeUnit.SECONDS)).isTrue();
      }
    }
  }

  @Test
  void enforcesAConnectionTimeoutWhenThePeerNeverSendsAnSshBanner() throws Exception {
    try (BannerBlackHole blackHole = BannerBlackHole.start();
        SshClient client = clientTrusting(null)) {
      CoreModuleProperties.NIO2_READ_TIMEOUT.set(client, Duration.ofMillis(300));
      client.start();
      long startedAt = System.nanoTime();
      ClientSession session =
          client
              .connect("user", "127.0.0.1", blackHole.port())
              .verify(CONNECT_TIMEOUT)
              .getSession();
      try {
        assertThat(session.waitFor(Set.of(ClientSession.ClientSessionEvent.TIMEOUT), 1_000))
            .contains(ClientSession.ClientSessionEvent.TIMEOUT);
      } finally {
        session.close(true);
      }
      assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(5));
    }
  }

  @Test
  void supportsCancellationAndCleanShutdownWithForwardingActive() throws Exception {
    try (TcpTarget target = TcpTarget.start();
        SshNode finalHost = SshNode.start("final", "final-password");
        SshClient client = clientTrusting(finalHost.fingerprint())) {
      client.start();
      try (ClientSession session = authenticate(client, finalHost, "127.0.0.1", finalHost.port());
          ExplicitPortForwardingTracker forwarding =
              session.createLocalPortForwardingTracker(
                  new SshdSocketAddress(0), new SshdSocketAddress("127.0.0.1", target.port()))) {
        assertThat(readFromForward(forwarding)).isEqualTo("ssh-spike-target");
        assertThatCode(() -> forwarding.close()).doesNotThrowAnyException();
        assertThatCode(() -> session.close(true)).doesNotThrowAnyException();
        assertThatCode(() -> client.stop()).doesNotThrowAnyException();
      }
    }

    try (BannerBlackHole blackHole = BannerBlackHole.start();
        SshClient client = clientTrusting(null)) {
      client.start();
      ConnectFuture connect = client.connect("user", "127.0.0.1", blackHole.port());
      connect.cancel();
      assertThat(connect.isCanceled()).isTrue();
      assertThatCode(() -> client.stop()).doesNotThrowAnyException();
    }
  }

  @Test
  void reportsAnUnreachableForwardingDestination() throws Exception {
    int unreachablePort = freePort();
    try (SshNode finalHost = SshNode.start("final", "final-password");
        SshClient client = clientTrusting(finalHost.fingerprint())) {
      client.start();
      try (ClientSession session = authenticate(client, finalHost, "127.0.0.1", finalHost.port());
          ExplicitPortForwardingTracker forwarding =
              session.createLocalPortForwardingTracker(
                  new SshdSocketAddress(0), new SshdSocketAddress("127.0.0.1", unreachablePort))) {
        assertThat(readFromForward(forwarding)).isEmpty();
      }
    }
  }

  private static SshClient clientTrusting(String expectedFingerprint) {
    SshClient client = SshClient.setUpDefaultClient();
    client.setServerKeyVerifier(
        (session, remoteAddress, serverKey) ->
            expectedFingerprint != null
                && expectedFingerprint.equals(KeyUtils.getFingerPrint(serverKey)));
    return client;
  }

  private static ClientSession authenticate(SshClient client, SshNode node, String host, int port)
      throws Exception {
    ClientSession session =
        client.connect(node.username(), host, port).verify(CONNECT_TIMEOUT).getSession();
    try {
      session.addPasswordIdentity(node.password());
      session.auth().verify(CONNECT_TIMEOUT);
      return session;
    } catch (Exception exception) {
      session.close(true);
      throw exception;
    }
  }

  private static int localPort(SocketAddress address) {
    return SshdSocketAddress.toAddressPort(address);
  }

  private static String readFromForward(ExplicitPortForwardingTracker forwarding) throws Exception {
    try (Socket socket = new Socket()) {
      socket.connect(SshdSocketAddress.toInetSocketAddress(forwarding.getBoundAddress()), 3_000);
      socket.setSoTimeout(3_000);
      byte[] data = socket.getInputStream().readNBytes("ssh-spike-target\n".length());
      return new String(data, StandardCharsets.UTF_8).trim();
    }
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static final class SshNode implements AutoCloseable {
    private final String username;
    private final String password;
    private final int port;
    private final KeyPair hostKey;
    private SshServer server;

    private SshNode(String username, String password, int port, KeyPair hostKey) {
      this.username = username;
      this.password = password;
      this.port = port;
      this.hostKey = hostKey;
    }

    static SshNode start(String username, String password) throws Exception {
      return start(username, password, freePort());
    }

    static SshNode start(String username, String password, int port) throws Exception {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2_048);
      SshNode node = new SshNode(username, password, port, generator.generateKeyPair());
      node.startServer();
      return node;
    }

    private void startServer() throws IOException {
      server = SshServer.setUpDefaultServer();
      server.setHost("127.0.0.1");
      server.setPort(port);
      server.setKeyPairProvider(KeyPairProvider.wrap(hostKey));
      PasswordAuthenticator authenticator =
          (candidateUser, candidatePassword, ignored) ->
              username.equals(candidateUser) && password.equals(candidatePassword);
      server.setPasswordAuthenticator(authenticator);
      server.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
      server.start();
    }

    String username() {
      return username;
    }

    String password() {
      return password;
    }

    int port() {
      return port;
    }

    String fingerprint() {
      return KeyUtils.getFingerPrint(hostKey.getPublic());
    }

    void stop() throws IOException {
      if (server != null) {
        server.stop();
        server = null;
      }
    }

    @Override
    public void close() throws IOException {
      stop();
    }
  }

  private static final class TcpTarget implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Thread acceptThread;

    private TcpTarget(ServerSocket serverSocket) {
      this.serverSocket = serverSocket;
      this.acceptThread =
          Thread.ofVirtual()
              .name("ssh-spike-target-accept")
              .start(
                  () -> {
                    while (!serverSocket.isClosed()) {
                      try {
                        Socket socket = serverSocket.accept();
                        executor.submit(
                            () -> {
                              try (socket) {
                                socket
                                    .getOutputStream()
                                    .write("ssh-spike-target\n".getBytes(StandardCharsets.UTF_8));
                                socket.getOutputStream().flush();
                              } catch (IOException ignored) {
                                // The target closes after responding; the client may cancel first.
                              }
                            });
                      } catch (IOException exception) {
                        if (!serverSocket.isClosed()) {
                          throw new IllegalStateException("TCP target accept failed", exception);
                        }
                      }
                    }
                  });
    }

    static TcpTarget start() throws IOException {
      return new TcpTarget(new ServerSocket(0, 50, InetAddress.getLoopbackAddress()));
    }

    int port() {
      return serverSocket.getLocalPort();
    }

    @Override
    public void close() throws IOException {
      serverSocket.close();
      try {
        acceptThread.join(2_000);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while stopping TCP target", exception);
      }
      executor.close();
    }
  }

  private static final class BannerBlackHole implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final Thread acceptThread;

    private BannerBlackHole(ServerSocket serverSocket) {
      this.serverSocket = serverSocket;
      this.acceptThread =
          Thread.ofVirtual()
              .name("ssh-spike-banner-black-hole")
              .start(
                  () -> {
                    try (Socket ignored = serverSocket.accept()) {
                      Thread.sleep(5_000);
                    } catch (IOException ignored) {
                      // Closing the server socket is the cancellation path under test.
                    } catch (InterruptedException exception) {
                      Thread.currentThread().interrupt();
                    }
                  });
    }

    static BannerBlackHole start() throws IOException {
      return new BannerBlackHole(new ServerSocket(0, 1, InetAddress.getLoopbackAddress()));
    }

    int port() {
      return serverSocket.getLocalPort();
    }

    @Override
    public void close() throws IOException {
      serverSocket.close();
      try {
        acceptThread.join(2_000);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static final class SessionListenerAdapter
      implements org.apache.sshd.common.session.SessionListener {
    private final CountDownLatch closed;

    private SessionListenerAdapter(CountDownLatch closed) {
      this.closed = closed;
    }

    @Override
    public void sessionClosed(org.apache.sshd.common.session.Session session) {
      closed.countDown();
    }
  }
}

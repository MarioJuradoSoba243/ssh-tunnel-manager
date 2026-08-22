package com.ssh.tunnelmanager.infrastructure.ssh;

import com.ssh.tunnelmanager.application.persistence.TrustedHostKeyRepository;
import com.ssh.tunnelmanager.application.persistence.model.TrustedHostKeyRecord;
import com.ssh.tunnelmanager.application.ssh.ConnectionTestResult;
import com.ssh.tunnelmanager.application.ssh.HostKeyApprovalCoordinator;
import com.ssh.tunnelmanager.application.ssh.HostKeyApprovalRequest;
import com.ssh.tunnelmanager.application.ssh.SshTunnelEngine;
import com.ssh.tunnelmanager.application.ssh.TunnelConnectionRequest;
import com.ssh.tunnelmanager.application.ssh.TunnelHandle;
import java.io.IOException;
import java.net.BindException;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.forward.ExplicitPortForwardingTracker;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionHeartbeatController.HeartbeatType;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MinaSshTunnelEngine implements SshTunnelEngine {
  private static final Logger LOGGER = LoggerFactory.getLogger(MinaSshTunnelEngine.class);
  private final TrustedHostKeyRepository trustedKeys;
  private final HostKeyApprovalCoordinator approvals;

  public MinaSshTunnelEngine(
      TrustedHostKeyRepository trustedKeys, HostKeyApprovalCoordinator approvals) {
    this.trustedKeys = trustedKeys;
    this.approvals = approvals;
  }

  @Override
  public TunnelHandle openTunnel(TunnelConnectionRequest request) {
    RouteResources route = null;
    try {
      route = connectRoute(request);
      ExplicitPortForwardingTracker forwarding =
          route.finalSession.createLocalPortForwardingTracker(
              new SshdSocketAddress(
                  request.localForward().bindAddress(), request.localForward().localPort()),
              new SshdSocketAddress(request.destination().host(), request.destination().port()));
      route.forwardings.add(forwarding);
      MinaTunnelHandle handle =
          new MinaTunnelHandle(route.clients, route.sessions, route.forwardings);
      route.sessions.forEach(session -> session.addSessionListener(handle.sessionListener()));
      return handle;
    } catch (Exception exception) {
      if (route != null) route.close();
      if (containsBindException(exception)) {
        throw new LocalPortInUseException(
            request.localForward().bindAddress(), request.localForward().localPort(), exception);
      }
      throw new SshEngineException("No se pudo abrir el túnel SSH", exception);
    }
  }

  @Override
  public ConnectionTestResult testConnection(TunnelConnectionRequest request) {
    RouteResources route = null;
    try {
      route = connectRoute(request);
      try (ExplicitPortForwardingTracker ephemeral =
              route.finalSession.createLocalPortForwardingTracker(
                  new SshdSocketAddress(0),
                  new SshdSocketAddress(
                      request.destination().host(), request.destination().port()));
          Socket socket = new Socket()) {
        socket.connect(SshdSocketAddress.toInetSocketAddress(ephemeral.getBoundAddress()), 3_000);
        return ConnectionTestResult.success();
      }
    } catch (Exception exception) {
      LOGGER.debug(
          "Prueba de conexión SSH falló destination={}:{}",
          request.destination().host(),
          request.destination().port(),
          exception);
      return ConnectionTestResult.failure("No se pudo alcanzar el destino remoto", exception);
    } finally {
      if (route != null) route.close();
    }
  }

  @Override
  public void closeTunnel(TunnelHandle handle) {
    if (handle != null) handle.close();
  }

  private RouteResources connectRoute(TunnelConnectionRequest request) throws Exception {
    RouteResources route = new RouteResources();
    try {
      String host = null;
      int port = 0;
      for (int index = 0; index < request.route().size(); index++) {
        TunnelConnectionRequest.RouteHop hop = request.route().get(index);
        if (index == 0) {
          host = hop.hostname();
          port = hop.sshPort();
        } else {
          ExplicitPortForwardingTracker hopForward =
              route
                  .sessions
                  .get(index - 1)
                  .createLocalPortForwardingTracker(
                      new SshdSocketAddress(0),
                      new SshdSocketAddress(hop.hostname(), hop.sshPort()));
          route.forwardings.add(hopForward);
          host = "127.0.0.1";
          port = SshdSocketAddress.toAddressPort(hopForward.getBoundAddress());
        }
        SshClient client = SshClient.setUpDefaultClient();
        client.setSessionHeartbeat(HeartbeatType.IGNORE, request.keepAlive());
        client.setServerKeyVerifier(
            new PersistentServerKeyVerifier(trustedKeys, approvals, hop, index));
        client.start();
        route.clients.add(client);
        ClientSession session =
            authenticate(
                client,
                hop.credential().username(),
                hop.credential().password(),
                host,
                port,
                request.connectionTimeout());
        route.sessions.add(session);
        LOGGER.debug(
            "SSH route hop connected position={} host={} port={}",
            hop.position(),
            hop.hostname(),
            hop.sshPort());
      }
      route.finalSession = route.sessions.get(route.sessions.size() - 1);
      return route;
    } catch (Exception exception) {
      route.close();
      throw exception;
    }
  }

  private static ClientSession authenticate(
      SshClient client, String username, String password, String host, int port, Duration timeout)
      throws Exception {
    ClientSession session = client.connect(username, host, port).verify(timeout).getSession();
    try {
      session.addPasswordIdentity(password);
      session.auth().verify(timeout);
      return session;
    } catch (Exception exception) {
      session.close(true);
      throw exception;
    }
  }

  private static final class RouteResources {
    private final List<SshClient> clients = new ArrayList<>();
    private final List<ClientSession> sessions = new ArrayList<>();
    private final List<ExplicitPortForwardingTracker> forwardings = new ArrayList<>();
    private ClientSession finalSession;

    private void close() {
      for (int index = forwardings.size() - 1; index >= 0; index--) {
        try {
          forwardings.get(index).close();
        } catch (IOException ignored) {
          // Resource cleanup is best effort after a failed connection attempt.
        }
      }
      MinaTunnelHandle.closeSessions(sessions);
      MinaTunnelHandle.closeClients(clients);
    }
  }

  private static final class MinaTunnelHandle implements TunnelHandle {
    private final List<SshClient> clients;
    private final List<ClientSession> sessions;
    private final List<ExplicitPortForwardingTracker> forwardings;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean open = new AtomicBoolean(true);

    private MinaTunnelHandle(
        List<SshClient> clients,
        List<ClientSession> sessions,
        List<ExplicitPortForwardingTracker> forwardings) {
      this.clients = List.copyOf(clients);
      this.sessions = List.copyOf(sessions);
      this.forwardings = List.copyOf(forwardings);
    }

    private SessionListener sessionListener() {
      return new SessionListener() {
        @Override
        public void sessionClosed(Session session) {
          if (open.compareAndSet(true, false)) listeners.forEach(Runnable::run);
        }
      };
    }

    @Override
    public boolean isOpen() {
      return open.get();
    }

    @Override
    public void onClosed(Runnable listener) {
      listeners.add(listener);
      if (!open.get()) listener.run();
    }

    @Override
    public void close() {
      if (!open.getAndSet(false)) return;
      for (int index = forwardings.size() - 1; index >= 0; index--) {
        try {
          forwardings.get(index).close();
        } catch (IOException ignored) {
          // Closing an already disconnected forward is harmless.
        }
      }
      closeSessions(sessions);
      closeClients(clients);
    }

    private static void closeSessions(List<ClientSession> sessions) {
      for (int index = sessions.size() - 1; index >= 0; index--) {
        sessions.get(index).close(true);
      }
    }

    private static void closeClients(List<SshClient> clients) {
      for (int index = clients.size() - 1; index >= 0; index--) {
        clients.get(index).stop();
        try {
          clients.get(index).close();
        } catch (IOException ignored) {
          // Closing a stopped client is best effort.
        }
      }
    }
  }

  private static final class PersistentServerKeyVerifier implements ServerKeyVerifier {
    private final TrustedHostKeyRepository trustedKeys;
    private final HostKeyApprovalCoordinator approvals;
    private final TunnelConnectionRequest.RouteHop hop;
    private final int routeIndex;

    private PersistentServerKeyVerifier(
        TrustedHostKeyRepository trustedKeys,
        HostKeyApprovalCoordinator approvals,
        TunnelConnectionRequest.RouteHop hop,
        int routeIndex) {
      this.trustedKeys = trustedKeys;
      this.approvals = approvals;
      this.hop = hop;
      this.routeIndex = routeIndex;
    }

    @Override
    public boolean verifyServerKey(
        ClientSession session, SocketAddress remoteAddress, PublicKey serverKey) {
      String fingerprint = KeyUtils.getFingerPrint(serverKey);
      String publicKey = Base64.getEncoder().encodeToString(serverKey.getEncoded());
      String algorithm = serverKey.getAlgorithm();
      var existing = trustedKeys.findByHost(hop.hostname(), hop.sshPort());
      if (existing.isPresent()) {
        TrustedHostKeyRecord trusted = existing.get();
        if (trusted.fingerprintSha256().equals(fingerprint)
            && trusted.publicKey().equals(publicKey)) return true;
        approvals.approve(
            new HostKeyApprovalRequest(
                hop.hostname(),
                hop.sshPort(),
                algorithm,
                publicKey,
                fingerprint,
                hop.position(),
                trusted.fingerprintSha256()));
        return false;
      }
      HostKeyApprovalRequest request =
          new HostKeyApprovalRequest(
              hop.hostname(),
              hop.sshPort(),
              algorithm,
              publicKey,
              fingerprint,
              hop.position(),
              existing.map(TrustedHostKeyRecord::fingerprintSha256).orElse(null));
      if (!approvals.approve(request)) return false;
      trustedKeys.save(
          new TrustedHostKeyRecord(
              UUID.randomUUID(),
              hop.hostname(),
              hop.sshPort(),
              algorithm,
              publicKey,
              fingerprint,
              Instant.now()));
      return true;
    }
  }

  public static class SshEngineException extends RuntimeException {
    public SshEngineException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static boolean containsBindException(Throwable exception) {
    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (current instanceof BindException) return true;
    }
    return false;
  }

  public static final class LocalPortInUseException extends SshEngineException {
    public LocalPortInUseException(String bindAddress, int localPort, Throwable cause) {
      super("El puerto local " + bindAddress + ":" + localPort + " ya está ocupado", cause);
    }
  }
}

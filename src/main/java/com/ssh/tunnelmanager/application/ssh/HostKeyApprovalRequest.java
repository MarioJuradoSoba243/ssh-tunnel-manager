package com.ssh.tunnelmanager.application.ssh;

import java.util.Objects;

public record HostKeyApprovalRequest(
    String hostname,
    int sshPort,
    String keyAlgorithm,
    String publicKey,
    String fingerprintSha256,
    String routePosition,
    String previousFingerprintSha256) {
  public HostKeyApprovalRequest {
    Objects.requireNonNull(hostname, "hostname");
    Objects.requireNonNull(keyAlgorithm, "keyAlgorithm");
    Objects.requireNonNull(publicKey, "publicKey");
    Objects.requireNonNull(fingerprintSha256, "fingerprintSha256");
    Objects.requireNonNull(routePosition, "routePosition");
  }
}

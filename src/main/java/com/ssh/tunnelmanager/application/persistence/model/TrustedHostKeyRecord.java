package com.ssh.tunnelmanager.application.persistence.model;

import java.time.Instant;
import java.util.UUID;

public record TrustedHostKeyRecord(
    UUID id,
    String hostname,
    int sshPort,
    String keyAlgorithm,
    String publicKey,
    String fingerprintSha256,
    Instant approvedAt) {}

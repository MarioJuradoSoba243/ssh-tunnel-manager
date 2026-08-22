package com.ssh.tunnelmanager.application.persistence.model;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record TunnelRecord(
    UUID id,
    String name,
    String bindAddress,
    int localPort,
    String destinationHost,
    int destinationPort,
    UUID finalHostId,
    UUID jump1HostId,
    UUID jump2HostId,
    Long keepAliveOverrideSeconds,
    Long connectionTimeoutOverrideSeconds,
    Integer retryCountOverride,
    UUID projectId,
    UUID environmentId,
    boolean favorite,
    Set<UUID> tagIds,
    Map<String, String> sshOptions) {}

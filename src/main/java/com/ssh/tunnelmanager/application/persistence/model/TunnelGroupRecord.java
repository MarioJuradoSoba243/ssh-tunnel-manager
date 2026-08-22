package com.ssh.tunnelmanager.application.persistence.model;

import java.util.Set;
import java.util.UUID;

public record TunnelGroupRecord(
    UUID id,
    String name,
    UUID projectId,
    UUID environmentId,
    boolean favorite,
    Set<UUID> tunnelIds,
    Set<UUID> tagIds) {}

package com.ssh.tunnelmanager.application.group;

import java.util.UUID;

public record GroupStartSummary(
    UUID operationId, UUID groupId, int requested, int open, int errors, int cancelled) {}

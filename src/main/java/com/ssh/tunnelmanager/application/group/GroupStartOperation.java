package com.ssh.tunnelmanager.application.group;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public record GroupStartOperation(
    UUID operationId, UUID groupId, CompletableFuture<GroupStartSummary> completion) {}

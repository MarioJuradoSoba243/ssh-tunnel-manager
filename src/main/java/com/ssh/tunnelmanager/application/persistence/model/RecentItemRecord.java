package com.ssh.tunnelmanager.application.persistence.model;

import java.time.Instant;
import java.util.UUID;

public record RecentItemRecord(RecentItemType itemType, UUID itemId, Instant lastStartedAt) {}

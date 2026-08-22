package com.ssh.tunnelmanager.application.portability;

import java.util.UUID;

public record ConflictKey(ConflictType type, UUID id) {}

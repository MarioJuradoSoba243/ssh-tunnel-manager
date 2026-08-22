package com.ssh.tunnelmanager.application.persistence.model;

import java.util.UUID;

public record CredentialRecord(UUID id, String username, String password) {}

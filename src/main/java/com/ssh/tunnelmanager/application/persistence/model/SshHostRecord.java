package com.ssh.tunnelmanager.application.persistence.model;

import java.util.UUID;

public record SshHostRecord(
    UUID id, String name, String hostname, int sshPort, UUID credentialId) {}

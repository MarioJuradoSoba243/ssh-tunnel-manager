package com.ssh.tunnelmanager.infrastructure.persistence.migration;

public record MigrationScript(int version, String name, String sql) {}

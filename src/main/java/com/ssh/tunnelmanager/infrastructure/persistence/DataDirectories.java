package com.ssh.tunnelmanager.infrastructure.persistence;

import java.nio.file.Path;

public record DataDirectories(Path dataDirectory, Path database, Path backups, Path logs) {}

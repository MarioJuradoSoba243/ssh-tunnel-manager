package com.ssh.tunnelmanager.application.portability;

public record ImportConflict(
    ConflictKey key, String name, String currentSummary, String importedSummary) {}

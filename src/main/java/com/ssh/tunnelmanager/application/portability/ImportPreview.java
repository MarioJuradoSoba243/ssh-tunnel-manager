package com.ssh.tunnelmanager.application.portability;

import java.util.List;

public record ImportPreview(
    int newEntities, int unchangedEntities, int conflictCount, List<ImportConflict> conflicts) {
  public ImportPreview {
    conflicts = List.copyOf(conflicts);
  }
}

package com.ssh.tunnelmanager.application.portability;

import java.nio.file.Path;
import java.util.List;

public interface BackupPort {
  void markDirty();

  Path backupIfDue();

  Path beforeImport();

  Path beforeRestore();

  List<Path> listBackups();

  void restore(Path backup);
}

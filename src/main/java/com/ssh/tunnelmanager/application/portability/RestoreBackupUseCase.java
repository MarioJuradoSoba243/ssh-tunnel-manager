package com.ssh.tunnelmanager.application.portability;

import java.nio.file.Path;

public final class RestoreBackupUseCase {
  private final BackupPort backups;
  private final RuntimeControl runtime;

  public RestoreBackupUseCase(BackupPort backups, RuntimeControl runtime) {
    this.backups = backups;
    this.runtime = runtime;
  }

  public void execute(Path source, boolean stopActiveTunnels) {
    if (runtime.hasActiveTunnels()) {
      if (!stopActiveTunnels) throw new ActiveTunnelsException();
      runtime.stopAllAndAwait();
      if (runtime.hasActiveTunnels()) throw new ActiveTunnelsException();
    }
    backups.beforeRestore();
    backups.restore(source);
  }
}

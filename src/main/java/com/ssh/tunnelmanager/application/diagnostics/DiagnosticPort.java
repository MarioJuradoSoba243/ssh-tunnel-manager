package com.ssh.tunnelmanager.application.diagnostics;

import java.nio.file.Path;

public interface DiagnosticPort {
  Path export(Path target);

  Path logsDirectory();
}

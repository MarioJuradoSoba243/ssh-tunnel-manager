package com.ssh.tunnelmanager.application.workspace;

import java.util.UUID;

public record SearchResult(Kind kind, UUID id, String title, String subtitle) {
  public enum Kind {
    TUNNEL,
    GROUP,
    HOST
  }
}

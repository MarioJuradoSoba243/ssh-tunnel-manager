package com.ssh.tunnelmanager.application.organization;

public record ClassificationUsage(int tunnelCount, int groupCount) {
  public int total() {
    return tunnelCount + groupCount;
  }
}

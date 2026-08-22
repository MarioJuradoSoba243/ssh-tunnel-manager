package com.ssh.tunnelmanager.application.organization;

import com.ssh.tunnelmanager.application.persistence.TunnelGroupRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import java.util.UUID;

public final class ClassificationUsageService {
  private final TunnelRepository tunnels;
  private final TunnelGroupRepository groups;

  public ClassificationUsageService(TunnelRepository tunnels, TunnelGroupRepository groups) {
    this.tunnels = tunnels;
    this.groups = groups;
  }

  public ClassificationUsage project(UUID id) {
    return usage(
        tunnels.findAll().stream().filter(item -> id.equals(item.projectId())).count(),
        groups.findAll().stream().filter(item -> id.equals(item.projectId())).count());
  }

  public ClassificationUsage environment(UUID id) {
    return usage(
        tunnels.findAll().stream().filter(item -> id.equals(item.environmentId())).count(),
        groups.findAll().stream().filter(item -> id.equals(item.environmentId())).count());
  }

  public ClassificationUsage tag(UUID id) {
    return usage(
        tunnels.findAll().stream().filter(item -> item.tagIds().contains(id)).count(),
        groups.findAll().stream().filter(item -> item.tagIds().contains(id)).count());
  }

  private static ClassificationUsage usage(long tunnels, long groups) {
    return new ClassificationUsage(Math.toIntExact(tunnels), Math.toIntExact(groups));
  }
}

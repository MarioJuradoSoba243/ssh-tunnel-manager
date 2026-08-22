package com.ssh.tunnelmanager.application.persistence.model;

import java.util.List;

/** Consistent, schema-independent view of all restorable application configuration. */
public record ConfigurationSnapshot(
    int databaseSchemaVersion,
    List<CredentialRecord> credentials,
    List<SshHostRecord> hosts,
    List<ProjectRecord> projects,
    List<EnvironmentRecord> environments,
    List<TagRecord> tags,
    List<TunnelRecord> tunnels,
    List<TunnelGroupRecord> groups,
    List<TrustedHostKeyRecord> trustedHostKeys,
    List<SettingRecord> settings,
    List<RecentItemRecord> recentItems,
    List<KeyboardShortcutRecord> keyboardShortcuts) {
  public ConfigurationSnapshot {
    credentials = List.copyOf(credentials);
    hosts = List.copyOf(hosts);
    projects = List.copyOf(projects);
    environments = List.copyOf(environments);
    tags = List.copyOf(tags);
    tunnels = List.copyOf(tunnels);
    groups = List.copyOf(groups);
    trustedHostKeys = List.copyOf(trustedHostKeys);
    settings = List.copyOf(settings);
    recentItems = List.copyOf(recentItems);
    keyboardShortcuts = List.copyOf(keyboardShortcuts);
  }
}

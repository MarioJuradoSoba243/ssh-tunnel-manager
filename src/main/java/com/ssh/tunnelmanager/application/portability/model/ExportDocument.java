package com.ssh.tunnelmanager.application.portability.model;

import com.ssh.tunnelmanager.application.persistence.model.ConfigurationSnapshot;
import com.ssh.tunnelmanager.application.persistence.model.CredentialRecord;
import com.ssh.tunnelmanager.application.persistence.model.EnvironmentRecord;
import com.ssh.tunnelmanager.application.persistence.model.KeyboardShortcutRecord;
import com.ssh.tunnelmanager.application.persistence.model.ProjectRecord;
import com.ssh.tunnelmanager.application.persistence.model.RecentItemRecord;
import com.ssh.tunnelmanager.application.persistence.model.RecentItemType;
import com.ssh.tunnelmanager.application.persistence.model.SettingRecord;
import com.ssh.tunnelmanager.application.persistence.model.SshHostRecord;
import com.ssh.tunnelmanager.application.persistence.model.TagRecord;
import com.ssh.tunnelmanager.application.persistence.model.TrustedHostKeyRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelGroupRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Explicit JSON contract. It deliberately does not expose JDBC or SQLite types. */
public record ExportDocument(
    int formatVersion,
    String applicationVersion,
    String exportedAt,
    int databaseSchemaVersion,
    List<Credential> credentials,
    List<Host> hosts,
    List<Project> projects,
    List<Environment> environments,
    List<Tag> tags,
    List<Tunnel> tunnels,
    List<Group> groups,
    List<TrustedHostKey> trustedHostKeys,
    List<Setting> settings,
    List<Favorite> favorites,
    List<RecentItem> recentItems,
    List<KeyboardShortcut> keyboardShortcuts) {
  public ExportDocument {
    credentials = List.copyOf(credentials == null ? List.of() : credentials);
    hosts = List.copyOf(hosts == null ? List.of() : hosts);
    projects = List.copyOf(projects == null ? List.of() : projects);
    environments = List.copyOf(environments == null ? List.of() : environments);
    tags = List.copyOf(tags == null ? List.of() : tags);
    tunnels = List.copyOf(tunnels == null ? List.of() : tunnels);
    groups = List.copyOf(groups == null ? List.of() : groups);
    trustedHostKeys = List.copyOf(trustedHostKeys == null ? List.of() : trustedHostKeys);
    settings = List.copyOf(settings == null ? List.of() : settings);
    favorites = List.copyOf(favorites == null ? List.of() : favorites);
    recentItems = List.copyOf(recentItems == null ? List.of() : recentItems);
    keyboardShortcuts = List.copyOf(keyboardShortcuts == null ? List.of() : keyboardShortcuts);
  }

  public static ExportDocument from(
      ConfigurationSnapshot snapshot, String applicationVersion, Instant exportedAt) {
    return new ExportDocument(
        1,
        applicationVersion,
        exportedAt.toString(),
        snapshot.databaseSchemaVersion(),
        snapshot.credentials().stream()
            .map(value -> new Credential(value.id(), value.username(), value.password()))
            .toList(),
        snapshot.hosts().stream()
            .map(
                value ->
                    new Host(
                        value.id(),
                        value.name(),
                        value.hostname(),
                        value.sshPort(),
                        value.credentialId()))
            .toList(),
        snapshot.projects().stream().map(value -> new Project(value.id(), value.name())).toList(),
        snapshot.environments().stream()
            .map(value -> new Environment(value.id(), value.name()))
            .toList(),
        snapshot.tags().stream().map(value -> new Tag(value.id(), value.name())).toList(),
        snapshot.tunnels().stream()
            .map(
                value ->
                    new Tunnel(
                        value.id(),
                        value.name(),
                        value.bindAddress(),
                        value.localPort(),
                        value.destinationHost(),
                        value.destinationPort(),
                        value.finalHostId(),
                        value.jump1HostId(),
                        value.jump2HostId(),
                        value.keepAliveOverrideSeconds(),
                        value.connectionTimeoutOverrideSeconds(),
                        value.retryCountOverride(),
                        value.projectId(),
                        value.environmentId(),
                        value.favorite(),
                        value.tagIds(),
                        value.sshOptions()))
            .toList(),
        snapshot.groups().stream()
            .map(
                value ->
                    new Group(
                        value.id(),
                        value.name(),
                        value.projectId(),
                        value.environmentId(),
                        value.favorite(),
                        value.tunnelIds(),
                        value.tagIds()))
            .toList(),
        snapshot.trustedHostKeys().stream()
            .map(
                value ->
                    new TrustedHostKey(
                        value.id(),
                        value.hostname(),
                        value.sshPort(),
                        value.keyAlgorithm(),
                        value.publicKey(),
                        value.fingerprintSha256(),
                        value.approvedAt().toString()))
            .toList(),
        snapshot.settings().stream().map(value -> new Setting(value.key(), value.value())).toList(),
        favorites(snapshot),
        snapshot.recentItems().stream()
            .map(
                value ->
                    new RecentItem(
                        value.itemType().name(), value.itemId(), value.lastStartedAt().toString()))
            .toList(),
        snapshot.keyboardShortcuts().stream()
            .map(value -> new KeyboardShortcut(value.action(), value.shortcut()))
            .toList());
  }

  public ConfigurationSnapshot toSnapshot() {
    Set<UUID> favoriteTunnels =
        favorites.stream()
            .filter(value -> "TUNNEL".equals(value.itemType()))
            .map(Favorite::itemId)
            .collect(java.util.stream.Collectors.toSet());
    Set<UUID> favoriteGroups =
        favorites.stream()
            .filter(value -> "GROUP".equals(value.itemType()))
            .map(Favorite::itemId)
            .collect(java.util.stream.Collectors.toSet());
    return new ConfigurationSnapshot(
        databaseSchemaVersion,
        credentials.stream()
            .map(value -> new CredentialRecord(value.id(), value.username(), value.password()))
            .toList(),
        hosts.stream()
            .map(
                value ->
                    new SshHostRecord(
                        value.id(),
                        value.name(),
                        value.hostname(),
                        value.sshPort(),
                        value.credentialId()))
            .toList(),
        projects.stream().map(value -> new ProjectRecord(value.id(), value.name())).toList(),
        environments.stream()
            .map(value -> new EnvironmentRecord(value.id(), value.name()))
            .toList(),
        tags.stream().map(value -> new TagRecord(value.id(), value.name())).toList(),
        tunnels.stream()
            .map(
                value ->
                    new TunnelRecord(
                        value.id(),
                        value.name(),
                        value.bindAddress(),
                        value.localPort(),
                        value.destinationHost(),
                        value.destinationPort(),
                        value.finalHostId(),
                        value.jump1HostId(),
                        value.jump2HostId(),
                        value.keepAliveOverrideSeconds(),
                        value.connectionTimeoutOverrideSeconds(),
                        value.retryCountOverride(),
                        value.projectId(),
                        value.environmentId(),
                        value.favorite() || favoriteTunnels.contains(value.id()),
                        value.tagIds(),
                        value.sshOptions()))
            .toList(),
        groups.stream()
            .map(
                value ->
                    new TunnelGroupRecord(
                        value.id(),
                        value.name(),
                        value.projectId(),
                        value.environmentId(),
                        value.favorite() || favoriteGroups.contains(value.id()),
                        value.tunnelIds(),
                        value.tagIds()))
            .toList(),
        trustedHostKeys.stream()
            .map(
                value ->
                    new TrustedHostKeyRecord(
                        value.id(),
                        value.hostname(),
                        value.sshPort(),
                        value.keyAlgorithm(),
                        value.publicKey(),
                        value.fingerprintSha256(),
                        Instant.parse(value.approvedAt())))
            .toList(),
        settings.stream().map(value -> new SettingRecord(value.key(), value.value())).toList(),
        recentItems.stream()
            .map(
                value ->
                    new RecentItemRecord(
                        RecentItemType.valueOf(value.itemType()),
                        value.itemId(),
                        Instant.parse(value.lastStartedAt())))
            .toList(),
        keyboardShortcuts.stream()
            .map(value -> new KeyboardShortcutRecord(value.action(), value.shortcut()))
            .toList());
  }

  private static List<Favorite> favorites(ConfigurationSnapshot snapshot) {
    return java.util.stream.Stream.concat(
            snapshot.tunnels().stream()
                .filter(TunnelRecord::favorite)
                .map(value -> new Favorite("TUNNEL", value.id())),
            snapshot.groups().stream()
                .filter(TunnelGroupRecord::favorite)
                .map(value -> new Favorite("GROUP", value.id())))
        .toList();
  }

  public record Credential(UUID id, String username, String password) {}

  public record Host(UUID id, String name, String hostname, int sshPort, UUID credentialId) {}

  public record Project(UUID id, String name) {}

  public record Environment(UUID id, String name) {}

  public record Tag(UUID id, String name) {}

  public record Tunnel(
      UUID id,
      String name,
      String bindAddress,
      int localPort,
      String destinationHost,
      int destinationPort,
      UUID finalHostId,
      UUID jump1HostId,
      UUID jump2HostId,
      Long keepAliveOverrideSeconds,
      Long connectionTimeoutOverrideSeconds,
      Integer retryCountOverride,
      UUID projectId,
      UUID environmentId,
      boolean favorite,
      Set<UUID> tagIds,
      Map<String, String> sshOptions) {}

  public record Group(
      UUID id,
      String name,
      UUID projectId,
      UUID environmentId,
      boolean favorite,
      Set<UUID> tunnelIds,
      Set<UUID> tagIds) {}

  public record TrustedHostKey(
      UUID id,
      String hostname,
      int sshPort,
      String keyAlgorithm,
      String publicKey,
      String fingerprintSha256,
      String approvedAt) {}

  public record Setting(String key, String value) {}

  public record Favorite(String itemType, UUID itemId) {}

  public record RecentItem(String itemType, UUID itemId, String lastStartedAt) {}

  public record KeyboardShortcut(String action, String shortcut) {}
}

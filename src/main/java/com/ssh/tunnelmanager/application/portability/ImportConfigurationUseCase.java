package com.ssh.tunnelmanager.application.portability;

import com.ssh.tunnelmanager.application.persistence.ConfigurationStore;
import com.ssh.tunnelmanager.application.persistence.model.ConfigurationSnapshot;
import com.ssh.tunnelmanager.application.persistence.model.CredentialRecord;
import com.ssh.tunnelmanager.application.persistence.model.EnvironmentRecord;
import com.ssh.tunnelmanager.application.persistence.model.KeyboardShortcutRecord;
import com.ssh.tunnelmanager.application.persistence.model.ProjectRecord;
import com.ssh.tunnelmanager.application.persistence.model.RecentItemRecord;
import com.ssh.tunnelmanager.application.persistence.model.SettingRecord;
import com.ssh.tunnelmanager.application.persistence.model.SshHostRecord;
import com.ssh.tunnelmanager.application.persistence.model.TagRecord;
import com.ssh.tunnelmanager.application.persistence.model.TrustedHostKeyRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelGroupRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import com.ssh.tunnelmanager.application.portability.model.ExportDocument;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ImportConfigurationUseCase {
  private final ConfigurationStore store;
  private final ConfigurationJsonCodec codec;
  private final BackupPort backups;
  private final RuntimeControl runtime;

  public ImportConfigurationUseCase(
      ConfigurationStore store,
      ConfigurationJsonCodec codec,
      BackupPort backups,
      RuntimeControl runtime) {
    this.store = store;
    this.codec = codec;
    this.backups = backups;
    this.runtime = runtime;
  }

  public ImportSession analyze(Path source) {
    ExportDocument document = codec.read(source);
    ConfigurationSnapshot imported = document.toSnapshot();
    validate(imported);
    ConfigurationSnapshot current = store.snapshot();
    List<ImportConflict> conflicts = new ArrayList<>();
    int[] newCount = {0};
    int[] unchangedCount = {0};
    compare(
        ConflictType.CREDENTIAL,
        current.credentials(),
        imported.credentials(),
        CredentialRecord::id,
        conflicts,
        newCount,
        unchangedCount);
    compare(
        ConflictType.HOST,
        current.hosts(),
        imported.hosts(),
        SshHostRecord::id,
        conflicts,
        newCount,
        unchangedCount);
    compare(
        ConflictType.PROJECT,
        current.projects(),
        imported.projects(),
        ProjectRecord::id,
        conflicts,
        newCount,
        unchangedCount);
    compare(
        ConflictType.ENVIRONMENT,
        current.environments(),
        imported.environments(),
        EnvironmentRecord::id,
        conflicts,
        newCount,
        unchangedCount);
    compare(
        ConflictType.TAG,
        current.tags(),
        imported.tags(),
        TagRecord::id,
        conflicts,
        newCount,
        unchangedCount);
    compare(
        ConflictType.TUNNEL,
        current.tunnels(),
        imported.tunnels(),
        TunnelRecord::id,
        conflicts,
        newCount,
        unchangedCount);
    compare(
        ConflictType.GROUP,
        current.groups(),
        imported.groups(),
        TunnelGroupRecord::id,
        conflicts,
        newCount,
        unchangedCount);
    compare(
        ConflictType.TRUSTED_HOST_KEY,
        current.trustedHostKeys(),
        imported.trustedHostKeys(),
        TrustedHostKeyRecord::id,
        conflicts,
        newCount,
        unchangedCount);
    compare(
        ConflictType.SETTING,
        current.settings(),
        imported.settings(),
        value -> UUID.nameUUIDFromBytes(value.key().getBytes()),
        conflicts,
        newCount,
        unchangedCount);
    compare(
        ConflictType.KEYBOARD_SHORTCUT,
        current.keyboardShortcuts(),
        imported.keyboardShortcuts(),
        value -> UUID.nameUUIDFromBytes(value.action().getBytes()),
        conflicts,
        newCount,
        unchangedCount);
    return new ImportSession(
        current,
        imported,
        new ImportPreview(newCount[0], unchangedCount[0], conflicts.size(), conflicts));
  }

  public ImportSession analyze(String source) {
    return analyze(Path.of(source));
  }

  public void commit(
      ImportSession session,
      Map<ConflictKey, ConflictAction> decisions,
      Map<ConflictType, ConflictAction> applyToAll,
      boolean stopActiveTunnels) {
    if (runtime.hasActiveTunnels()) {
      if (!stopActiveTunnels) throw new ActiveTunnelsException();
      runtime.stopAllAndAwait();
      if (runtime.hasActiveTunnels()) throw new ActiveTunnelsException();
    }
    Map<ConflictKey, ConflictAction> resolved = new HashMap<>(decisions);
    for (ImportConflict conflict : session.preview().conflicts()) {
      ConflictAction action = resolved.get(conflict.key());
      if (action == null) action = applyToAll.get(conflict.key().type());
      if (action == null) {
        throw new PortabilityException("Falta resolver el conflicto " + conflict.key());
      }
      if (action == ConflictAction.CREATE_COPY
          && (conflict.key().type() == ConflictType.TRUSTED_HOST_KEY
              || conflict.key().type() == ConflictType.SETTING
              || conflict.key().type() == ConflictType.KEYBOARD_SHORTCUT)) {
        throw new PortabilityException(
            "Crear copia no está disponible para conflictos de " + conflict.key().type());
      }
      resolved.put(conflict.key(), action);
    }
    backups.beforeImport();
    store.replace(merge(session.current(), session.imported(), resolved));
  }

  private static ConfigurationSnapshot merge(
      ConfigurationSnapshot current,
      ConfigurationSnapshot imported,
      Map<ConflictKey, ConflictAction> decisions) {
    IdRemapping remapping = new IdRemapping();
    planCopies(
        current,
        imported,
        ConflictType.CREDENTIAL,
        imported.credentials(),
        CredentialRecord::id,
        decisions,
        remapping);
    planCopies(
        current,
        imported,
        ConflictType.HOST,
        imported.hosts(),
        SshHostRecord::id,
        decisions,
        remapping);
    planCopies(
        current,
        imported,
        ConflictType.PROJECT,
        imported.projects(),
        ProjectRecord::id,
        decisions,
        remapping);
    planCopies(
        current,
        imported,
        ConflictType.ENVIRONMENT,
        imported.environments(),
        EnvironmentRecord::id,
        decisions,
        remapping);
    planCopies(
        current, imported, ConflictType.TAG, imported.tags(), TagRecord::id, decisions, remapping);
    planCopies(
        current,
        imported,
        ConflictType.TUNNEL,
        imported.tunnels(),
        TunnelRecord::id,
        decisions,
        remapping);
    planCopies(
        current,
        imported,
        ConflictType.GROUP,
        imported.groups(),
        TunnelGroupRecord::id,
        decisions,
        remapping);
    planCopies(
        current,
        imported,
        ConflictType.TRUSTED_HOST_KEY,
        imported.trustedHostKeys(),
        TrustedHostKeyRecord::id,
        decisions,
        remapping);

    List<CredentialRecord> credentials =
        mergeValues(
            current.credentials(),
            imported.credentials(),
            CredentialRecord::id,
            ConflictType.CREDENTIAL,
            decisions,
            remapping,
            value ->
                new CredentialRecord(
                    remapping.id(ConflictType.CREDENTIAL, value.id()),
                    value.username(),
                    value.password()),
            current.credentials().stream().map(CredentialRecord::id).collect(Collectors.toSet()),
            CredentialRecord::username);
    List<SshHostRecord> hosts =
        mergeValues(
            current.hosts(),
            imported.hosts(),
            SshHostRecord::id,
            ConflictType.HOST,
            decisions,
            remapping,
            value ->
                new SshHostRecord(
                    remapping.id(ConflictType.HOST, value.id()),
                    value.name(),
                    value.hostname(),
                    value.sshPort(),
                    remapping.id(ConflictType.CREDENTIAL, value.credentialId())),
            current.hosts().stream().map(SshHostRecord::id).collect(Collectors.toSet()),
            SshHostRecord::name);
    List<ProjectRecord> projects =
        mergeValues(
            current.projects(),
            imported.projects(),
            ProjectRecord::id,
            ConflictType.PROJECT,
            decisions,
            remapping,
            value ->
                new ProjectRecord(remapping.id(ConflictType.PROJECT, value.id()), value.name()),
            current.projects().stream().map(ProjectRecord::id).collect(Collectors.toSet()),
            ProjectRecord::name);
    List<EnvironmentRecord> environments =
        mergeValues(
            current.environments(),
            imported.environments(),
            EnvironmentRecord::id,
            ConflictType.ENVIRONMENT,
            decisions,
            remapping,
            value ->
                new EnvironmentRecord(
                    remapping.id(ConflictType.ENVIRONMENT, value.id()), value.name()),
            current.environments().stream().map(EnvironmentRecord::id).collect(Collectors.toSet()),
            EnvironmentRecord::name);
    List<TagRecord> tags =
        mergeValues(
            current.tags(),
            imported.tags(),
            TagRecord::id,
            ConflictType.TAG,
            decisions,
            remapping,
            value -> new TagRecord(remapping.id(ConflictType.TAG, value.id()), value.name()),
            current.tags().stream().map(TagRecord::id).collect(Collectors.toSet()),
            TagRecord::name);
    List<TunnelRecord> tunnels = mergeTunnels(current, imported, decisions, remapping);
    List<TunnelGroupRecord> groups = mergeGroups(current, imported, decisions, remapping);
    List<TrustedHostKeyRecord> keys =
        mergeValues(
            current.trustedHostKeys(),
            imported.trustedHostKeys(),
            TrustedHostKeyRecord::id,
            ConflictType.TRUSTED_HOST_KEY,
            decisions,
            remapping,
            value ->
                new TrustedHostKeyRecord(
                    remapping.id(ConflictType.TRUSTED_HOST_KEY, value.id()),
                    value.hostname(),
                    value.sshPort(),
                    value.keyAlgorithm(),
                    value.publicKey(),
                    value.fingerprintSha256(),
                    value.approvedAt()),
            current.trustedHostKeys().stream()
                .map(TrustedHostKeyRecord::id)
                .collect(Collectors.toSet()),
            value -> value.hostname() + ":" + value.sshPort());
    List<SettingRecord> settings =
        mergeSimple(
            current.settings(),
            imported.settings(),
            SettingRecord::key,
            ConflictType.SETTING,
            decisions);
    List<KeyboardShortcutRecord> shortcuts =
        mergeSimple(
            current.keyboardShortcuts(),
            imported.keyboardShortcuts(),
            KeyboardShortcutRecord::action,
            ConflictType.KEYBOARD_SHORTCUT,
            decisions);
    Set<UUID> tunnelIds = tunnels.stream().map(TunnelRecord::id).collect(Collectors.toSet());
    Set<UUID> groupIds = groups.stream().map(TunnelGroupRecord::id).collect(Collectors.toSet());
    List<RecentItemRecord> recents =
        imported.recentItems().stream()
            .map(
                value ->
                    new RecentItemRecord(
                        value.itemType(),
                        value.itemType()
                                == com.ssh.tunnelmanager.application.persistence.model
                                    .RecentItemType.TUNNEL
                            ? remapping.id(ConflictType.TUNNEL, value.itemId())
                            : remapping.id(ConflictType.GROUP, value.itemId()),
                        value.lastStartedAt()))
            .filter(
                value ->
                    value.itemType()
                            == com.ssh.tunnelmanager.application.persistence.model.RecentItemType
                                .TUNNEL
                        ? tunnelIds.contains(value.itemId())
                        : groupIds.contains(value.itemId()))
            .toList();
    return new ConfigurationSnapshot(
        current.databaseSchemaVersion(),
        credentials,
        hosts,
        projects,
        environments,
        tags,
        tunnels,
        groups,
        keys,
        settings,
        recents,
        shortcuts);
  }

  private static List<TunnelRecord> mergeTunnels(
      ConfigurationSnapshot current,
      ConfigurationSnapshot imported,
      Map<ConflictKey, ConflictAction> decisions,
      IdRemapping remapping) {
    return mergeValues(
        current.tunnels(),
        imported.tunnels(),
        TunnelRecord::id,
        ConflictType.TUNNEL,
        decisions,
        remapping,
        value ->
            new TunnelRecord(
                remapping.id(ConflictType.TUNNEL, value.id()),
                value.name(),
                value.bindAddress(),
                value.localPort(),
                value.destinationHost(),
                value.destinationPort(),
                remapping.id(ConflictType.HOST, value.finalHostId()),
                remapping.nullableId(ConflictType.HOST, value.jump1HostId()),
                remapping.nullableId(ConflictType.HOST, value.jump2HostId()),
                value.keepAliveOverrideSeconds(),
                value.connectionTimeoutOverrideSeconds(),
                value.retryCountOverride(),
                remapping.nullableId(ConflictType.PROJECT, value.projectId()),
                remapping.nullableId(ConflictType.ENVIRONMENT, value.environmentId()),
                value.favorite(),
                value.tagIds().stream()
                    .map(id -> remapping.id(ConflictType.TAG, id))
                    .collect(Collectors.toSet()),
                value.sshOptions()),
        current.tunnels().stream().map(TunnelRecord::id).collect(Collectors.toSet()),
        TunnelRecord::name);
  }

  private static List<TunnelGroupRecord> mergeGroups(
      ConfigurationSnapshot current,
      ConfigurationSnapshot imported,
      Map<ConflictKey, ConflictAction> decisions,
      IdRemapping remapping) {
    return mergeValues(
        current.groups(),
        imported.groups(),
        TunnelGroupRecord::id,
        ConflictType.GROUP,
        decisions,
        remapping,
        value ->
            new TunnelGroupRecord(
                remapping.id(ConflictType.GROUP, value.id()),
                value.name(),
                remapping.nullableId(ConflictType.PROJECT, value.projectId()),
                remapping.nullableId(ConflictType.ENVIRONMENT, value.environmentId()),
                value.favorite(),
                value.tunnelIds().stream()
                    .map(id -> remapping.id(ConflictType.TUNNEL, id))
                    .collect(Collectors.toSet()),
                value.tagIds().stream()
                    .map(id -> remapping.id(ConflictType.TAG, id))
                    .collect(Collectors.toSet())),
        current.groups().stream().map(TunnelGroupRecord::id).collect(Collectors.toSet()),
        TunnelGroupRecord::name);
  }

  private static <T, K> List<T> mergeSimple(
      List<T> current,
      List<T> imported,
      Function<T, K> key,
      ConflictType type,
      Map<ConflictKey, ConflictAction> decisions) {
    Map<K, T> result = new LinkedHashMap<>();
    current.forEach(value -> result.put(key.apply(value), value));
    for (T value : imported) {
      K valueKey = key.apply(value);
      if (!result.containsKey(valueKey)) result.put(valueKey, value);
      else if (!result.get(valueKey).equals(value)) {
        ConflictAction action =
            decisions.get(
                new ConflictKey(type, UUID.nameUUIDFromBytes(valueKey.toString().getBytes())));
        if (action == ConflictAction.REPLACE) result.put(valueKey, value);
      }
    }
    return List.copyOf(result.values());
  }

  private static <T> List<T> mergeValues(
      List<T> current,
      List<T> imported,
      Function<T, UUID> key,
      ConflictType type,
      Map<ConflictKey, ConflictAction> decisions,
      IdRemapping remapping,
      Function<T, T> transform,
      Set<UUID> currentIds,
      Function<T, String> name) {
    Map<UUID, T> result = new LinkedHashMap<>();
    current.forEach(value -> result.put(key.apply(value), value));
    Set<String> names = current.stream().map(name).collect(Collectors.toCollection(HashSet::new));
    for (T value : imported) {
      UUID originalId = key.apply(value);
      T transformed = transform.apply(value);
      UUID outputId = key.apply(transformed);
      ConflictAction action = decisions.get(new ConflictKey(type, originalId));
      boolean conflict = currentIds.contains(originalId) && !result.get(originalId).equals(value);
      if (conflict && action == ConflictAction.KEEP_CURRENT) continue;
      if (conflict && action == ConflictAction.REPLACE) {
        String previousName = name.apply(result.get(originalId));
        result.put(originalId, transformed);
        names.remove(previousName);
        names.add(name.apply(transformed));
        continue;
      }
      String unique = uniqueName(name.apply(transformed), names);
      if (!unique.equals(name.apply(transformed))) transformed = rename(transformed, unique);
      names.add(unique);
      result.put(outputId, transformed);
    }
    return List.copyOf(result.values());
  }

  @SuppressWarnings("unchecked")
  private static <T> T rename(T value, String name) {
    if (value instanceof CredentialRecord record)
      return (T) new CredentialRecord(record.id(), name, record.password());
    if (value instanceof SshHostRecord record)
      return (T)
          new SshHostRecord(
              record.id(), name, record.hostname(), record.sshPort(), record.credentialId());
    if (value instanceof ProjectRecord record) return (T) new ProjectRecord(record.id(), name);
    if (value instanceof EnvironmentRecord record)
      return (T) new EnvironmentRecord(record.id(), name);
    if (value instanceof TagRecord record) return (T) new TagRecord(record.id(), name);
    if (value instanceof TunnelRecord record)
      return (T)
          new TunnelRecord(
              record.id(),
              name,
              record.bindAddress(),
              record.localPort(),
              record.destinationHost(),
              record.destinationPort(),
              record.finalHostId(),
              record.jump1HostId(),
              record.jump2HostId(),
              record.keepAliveOverrideSeconds(),
              record.connectionTimeoutOverrideSeconds(),
              record.retryCountOverride(),
              record.projectId(),
              record.environmentId(),
              record.favorite(),
              record.tagIds(),
              record.sshOptions());
    if (value instanceof TunnelGroupRecord record)
      return (T)
          new TunnelGroupRecord(
              record.id(),
              name,
              record.projectId(),
              record.environmentId(),
              record.favorite(),
              record.tunnelIds(),
              record.tagIds());
    return value;
  }

  private static String uniqueName(String requested, Set<String> names) {
    if (!names.contains(requested)) return requested;
    String base = requested + " (copia)";
    String candidate = base;
    int suffix = 2;
    while (names.contains(candidate)) candidate = requested + " (copia " + suffix++ + ")";
    return candidate;
  }

  private static <T> void planCopies(
      ConfigurationSnapshot current,
      ConfigurationSnapshot imported,
      ConflictType type,
      List<T> values,
      Function<T, UUID> key,
      Map<ConflictKey, ConflictAction> decisions,
      IdRemapping remapping) {
    Set<UUID> currentIds = ids(current, type);
    for (T value : values) {
      UUID id = key.apply(value);
      if (currentIds.contains(id)
          && !currentValue(current, type, id).equals(value)
          && decisions.get(new ConflictKey(type, id)) == ConflictAction.CREATE_COPY)
        remapping.put(type, id, UUID.randomUUID());
    }
  }

  private static Set<UUID> ids(ConfigurationSnapshot snapshot, ConflictType type) {
    return switch (type) {
      case CREDENTIAL ->
          snapshot.credentials().stream().map(CredentialRecord::id).collect(Collectors.toSet());
      case HOST -> snapshot.hosts().stream().map(SshHostRecord::id).collect(Collectors.toSet());
      case PROJECT ->
          snapshot.projects().stream().map(ProjectRecord::id).collect(Collectors.toSet());
      case ENVIRONMENT ->
          snapshot.environments().stream().map(EnvironmentRecord::id).collect(Collectors.toSet());
      case TAG -> snapshot.tags().stream().map(TagRecord::id).collect(Collectors.toSet());
      case TUNNEL -> snapshot.tunnels().stream().map(TunnelRecord::id).collect(Collectors.toSet());
      case GROUP ->
          snapshot.groups().stream().map(TunnelGroupRecord::id).collect(Collectors.toSet());
      case TRUSTED_HOST_KEY ->
          snapshot.trustedHostKeys().stream()
              .map(TrustedHostKeyRecord::id)
              .collect(Collectors.toSet());
      default -> Set.of();
    };
  }

  private static Object currentValue(ConfigurationSnapshot snapshot, ConflictType type, UUID id) {
    return switch (type) {
      case CREDENTIAL ->
          snapshot.credentials().stream()
              .filter(value -> value.id().equals(id))
              .findFirst()
              .orElseThrow();
      case HOST ->
          snapshot.hosts().stream()
              .filter(value -> value.id().equals(id))
              .findFirst()
              .orElseThrow();
      case PROJECT ->
          snapshot.projects().stream()
              .filter(value -> value.id().equals(id))
              .findFirst()
              .orElseThrow();
      case ENVIRONMENT ->
          snapshot.environments().stream()
              .filter(value -> value.id().equals(id))
              .findFirst()
              .orElseThrow();
      case TAG ->
          snapshot.tags().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
      case TUNNEL ->
          snapshot.tunnels().stream()
              .filter(value -> value.id().equals(id))
              .findFirst()
              .orElseThrow();
      case GROUP ->
          snapshot.groups().stream()
              .filter(value -> value.id().equals(id))
              .findFirst()
              .orElseThrow();
      case TRUSTED_HOST_KEY ->
          snapshot.trustedHostKeys().stream()
              .filter(value -> value.id().equals(id))
              .findFirst()
              .orElseThrow();
      default -> throw new IllegalArgumentException("Tipo no basado en UUID: " + type);
    };
  }

  private static <T> void compare(
      ConflictType type,
      List<T> current,
      List<T> imported,
      Function<T, UUID> key,
      List<ImportConflict> conflicts,
      int[] newCount,
      int[] unchangedCount) {
    Map<UUID, T> currentById = current.stream().collect(Collectors.toMap(key, Function.identity()));
    for (T value : imported) {
      T local = currentById.get(key.apply(value));
      if (local == null) newCount[0]++;
      else if (local.equals(value)) unchangedCount[0]++;
      else
        conflicts.add(
            new ImportConflict(
                new ConflictKey(type, key.apply(value)),
                displayName(value),
                summary(local),
                summary(value)));
    }
  }

  private static String displayName(Object value) {
    if (value instanceof CredentialRecord record) return record.username();
    if (value instanceof SettingRecord record) return record.key();
    if (value instanceof KeyboardShortcutRecord record) return record.action();
    if (value instanceof TrustedHostKeyRecord record)
      return record.hostname() + ":" + record.sshPort();
    try {
      return String.valueOf(value.getClass().getMethod("name").invoke(value));
    } catch (ReflectiveOperationException exception) {
      return value.toString();
    }
  }

  private static String summary(Object value) {
    if (value instanceof CredentialRecord record) return "usuario=" + record.username();
    return displayName(value) + " · " + value.getClass().getSimpleName();
  }

  private static void validate(ConfigurationSnapshot snapshot) {
    checkUnique(
        snapshot.credentials().stream().map(CredentialRecord::id).toList(), "credential id");
    checkUnique(snapshot.hosts().stream().map(SshHostRecord::id).toList(), "host id");
    checkUnique(snapshot.projects().stream().map(ProjectRecord::id).toList(), "project id");
    checkUnique(
        snapshot.environments().stream().map(EnvironmentRecord::id).toList(), "environment id");
    checkUnique(snapshot.tags().stream().map(TagRecord::id).toList(), "tag id");
    checkUnique(snapshot.tunnels().stream().map(TunnelRecord::id).toList(), "tunnel id");
    checkUnique(snapshot.groups().stream().map(TunnelGroupRecord::id).toList(), "group id");
    checkUnique(
        snapshot.trustedHostKeys().stream().map(TrustedHostKeyRecord::id).toList(),
        "trusted host key id");
    checkUniqueNames(
        snapshot.credentials().stream().map(CredentialRecord::username).toList(),
        "credential username");
    checkUniqueNames(snapshot.hosts().stream().map(SshHostRecord::name).toList(), "host name");
    checkUniqueNames(
        snapshot.projects().stream().map(ProjectRecord::name).toList(), "project name");
    checkUniqueNames(
        snapshot.environments().stream().map(EnvironmentRecord::name).toList(), "environment name");
    checkUniqueNames(snapshot.tags().stream().map(TagRecord::name).toList(), "tag name");
    checkUniqueNames(snapshot.tunnels().stream().map(TunnelRecord::name).toList(), "tunnel name");
    checkUniqueNames(
        snapshot.groups().stream().map(TunnelGroupRecord::name).toList(), "group name");
    Set<UUID> credentials =
        snapshot.credentials().stream().map(CredentialRecord::id).collect(Collectors.toSet());
    Set<UUID> hosts = snapshot.hosts().stream().map(SshHostRecord::id).collect(Collectors.toSet());
    Set<UUID> projects =
        snapshot.projects().stream().map(ProjectRecord::id).collect(Collectors.toSet());
    Set<UUID> environments =
        snapshot.environments().stream().map(EnvironmentRecord::id).collect(Collectors.toSet());
    Set<UUID> tags = snapshot.tags().stream().map(TagRecord::id).collect(Collectors.toSet());
    Set<UUID> tunnels =
        snapshot.tunnels().stream().map(TunnelRecord::id).collect(Collectors.toSet());
    for (SshHostRecord value : snapshot.hosts())
      if (!credentials.contains(value.credentialId()))
        throw new PortabilityException("Host con credential inexistente: " + value.id());
    for (TunnelRecord value : snapshot.tunnels()) {
      if (!hosts.contains(value.finalHostId())
          || (value.jump1HostId() != null && !hosts.contains(value.jump1HostId()))
          || (value.jump2HostId() != null && !hosts.contains(value.jump2HostId())))
        throw new PortabilityException("Túnel con ruta SSH inválida: " + value.id());
      if (value.projectId() != null && !projects.contains(value.projectId()))
        throw new PortabilityException("Proyecto inexistente en túnel: " + value.id());
      if (value.environmentId() != null && !environments.contains(value.environmentId()))
        throw new PortabilityException("Entorno inexistente en túnel: " + value.id());
      if (!tags.containsAll(value.tagIds()))
        throw new PortabilityException("Tag inexistente en túnel: " + value.id());
    }
    for (TunnelGroupRecord value : snapshot.groups())
      if (!tunnels.containsAll(value.tunnelIds()) || !tags.containsAll(value.tagIds()))
        throw new PortabilityException("Relación inválida en grupo: " + value.id());
  }

  private static void checkUnique(List<UUID> values, String label) {
    if (values.size() != new HashSet<>(values).size())
      throw new PortabilityException("IDs duplicados en " + label);
  }

  private static void checkUniqueNames(List<String> values, String label) {
    if (values.size() != new HashSet<>(values).size())
      throw new PortabilityException("Nombres duplicados en " + label);
  }

  private static final class IdRemapping {
    private final Map<ConflictType, Map<UUID, UUID>> values = new HashMap<>();

    void put(ConflictType type, UUID oldId, UUID newId) {
      values.computeIfAbsent(type, ignored -> new HashMap<>()).put(oldId, newId);
    }

    UUID id(ConflictType type, UUID id) {
      return id == null ? null : values.getOrDefault(type, Map.of()).getOrDefault(id, id);
    }

    UUID nullableId(ConflictType type, UUID id) {
      return id == null ? null : id(type, id);
    }
  }
}

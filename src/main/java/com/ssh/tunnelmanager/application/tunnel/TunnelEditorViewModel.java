package com.ssh.tunnelmanager.application.tunnel;

import com.ssh.tunnelmanager.application.persistence.CredentialRepository;
import com.ssh.tunnelmanager.application.persistence.EnvironmentRepository;
import com.ssh.tunnelmanager.application.persistence.ProjectRepository;
import com.ssh.tunnelmanager.application.persistence.SshHostRepository;
import com.ssh.tunnelmanager.application.persistence.TagRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.persistence.model.CredentialRecord;
import com.ssh.tunnelmanager.application.persistence.model.SshHostRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import com.ssh.tunnelmanager.domain.credential.Credential;
import com.ssh.tunnelmanager.domain.host.SshHost;
import com.ssh.tunnelmanager.domain.tunnel.LocalForward;
import com.ssh.tunnelmanager.domain.tunnel.RemoteDestination;
import com.ssh.tunnelmanager.domain.tunnel.SshRoute;
import com.ssh.tunnelmanager.domain.tunnel.Tunnel;
import com.ssh.tunnelmanager.domain.tunnel.TunnelOptions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Coordinates tunnel-editor validation and conversion without depending on JavaFX. */
public final class TunnelEditorViewModel {
  private final SshHostRepository hosts;
  private final CredentialRepository credentials;
  private final TunnelRepository tunnels;
  private final SaveTunnelUseCase save;
  private final ProjectRepository projects;
  private final EnvironmentRepository environments;
  private final TagRepository tags;
  private volatile List<TunnelRecord> cachedTunnels = List.of();

  public TunnelEditorViewModel(
      SshHostRepository hosts,
      CredentialRepository credentials,
      TunnelRepository tunnels,
      SaveTunnelUseCase save) {
    this(hosts, credentials, tunnels, save, null, null, null);
  }

  public TunnelEditorViewModel(
      SshHostRepository hosts,
      CredentialRepository credentials,
      TunnelRepository tunnels,
      SaveTunnelUseCase save,
      ProjectRepository projects,
      EnvironmentRepository environments,
      TagRepository tags) {
    this.hosts = Objects.requireNonNull(hosts);
    this.credentials = Objects.requireNonNull(credentials);
    this.tunnels = Objects.requireNonNull(tunnels);
    this.save = Objects.requireNonNull(save);
    this.projects = projects;
    this.environments = environments;
    this.tags = tags;
  }

  public List<SshHostRecord> hosts() {
    cachedTunnels = tunnels.findAll();
    return hosts.findAll();
  }

  public List<com.ssh.tunnelmanager.application.persistence.model.ProjectRecord> projects() {
    return projects == null ? List.of() : projects.findAll();
  }

  public List<com.ssh.tunnelmanager.application.persistence.model.EnvironmentRecord>
      environments() {
    return environments == null ? List.of() : environments.findAll();
  }

  public List<com.ssh.tunnelmanager.application.persistence.model.TagRecord> tags() {
    return tags == null ? List.of() : tags.findAll();
  }

  public List<String> validate(Form form) {
    List<String> errors = new ArrayList<>();
    required(form.name(), "El nombre es obligatorio", errors);
    required(form.bindAddress(), "El bind local es obligatorio", errors);
    required(form.localPort(), "El puerto local es obligatorio", errors);
    required(form.destinationHost(), "El destino es obligatorio", errors);
    required(form.destinationPort(), "El puerto destino es obligatorio", errors);
    if (form.finalHostId() == null) errors.add("El host SSH final es obligatorio");
    if (form.jump2HostId() != null && form.jump1HostId() == null) {
      errors.add("Jump 2 requiere Jump 1");
    }
    if (form.jump1HostId() != null && form.jump1HostId().equals(form.jump2HostId())) {
      errors.add("Jump 1 y Jump 2 deben ser distintos");
    }
    if (form.finalHostId() != null
        && (form.finalHostId().equals(form.jump1HostId())
            || form.finalHostId().equals(form.jump2HostId()))) {
      errors.add("El host final no puede repetirse como jump");
    }
    parsePort(form.localPort(), "Puerto local", errors);
    parsePort(form.destinationPort(), "Puerto destino", errors);
    parseOptionalPositive(form.keepAliveSeconds(), "Keep-alive", errors);
    parseOptionalPositive(form.connectionTimeoutSeconds(), "Timeout", errors);
    parseOptionalNonNegative(form.retryCount(), "Reintentos", errors);
    parseOptions(form.sshOptions(), errors);
    if (cachedTunnels.stream()
        .anyMatch(
            tunnel -> !tunnel.id().equals(form.id()) && tunnel.name().equals(form.name().trim()))) {
      errors.add("El nombre del túnel ya está utilizado");
    }
    return List.copyOf(errors);
  }

  public void save(Form form) {
    List<String> errors = validate(form);
    if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
    save.execute(toDomain(form));
  }

  public Form fromRecord(TunnelRecord record) {
    return new Form(
        record.id(),
        record.name(),
        record.bindAddress(),
        Integer.toString(record.localPort()),
        record.destinationHost(),
        Integer.toString(record.destinationPort()),
        record.finalHostId(),
        record.jump1HostId(),
        record.jump2HostId(),
        optional(record.keepAliveOverrideSeconds()),
        optional(record.connectionTimeoutOverrideSeconds()),
        optional(record.retryCountOverride()),
        formatOptions(record.sshOptions()),
        record.projectId(),
        record.environmentId(),
        record.tagIds(),
        record.favorite());
  }

  public static Form newForm() {
    return new Form(null, "", "127.0.0.1", "", "127.0.0.1", "", null, null, null, "", "", "", "");
  }

  private Tunnel toDomain(Form form) {
    Map<UUID, SshHost> resolved = new LinkedHashMap<>();
    for (UUID hostId :
        java.util.Arrays.asList(form.jump1HostId(), form.jump2HostId(), form.finalHostId())) {
      if (hostId != null) resolved.put(hostId, loadHost(hostId));
    }
    List<SshHost> jumps = new ArrayList<>();
    if (form.jump1HostId() != null) jumps.add(resolved.get(form.jump1HostId()));
    if (form.jump2HostId() != null) jumps.add(resolved.get(form.jump2HostId()));
    return new Tunnel(
        form.id() == null ? UUID.randomUUID() : form.id(),
        form.name().trim(),
        new LocalForward(form.bindAddress().trim(), parse(form.localPort())),
        new RemoteDestination(form.destinationHost().trim(), parse(form.destinationPort())),
        new SshRoute(jumps, resolved.get(form.finalHostId())),
        new TunnelOptions(
            parseOptionalLong(form.keepAliveSeconds()),
            parseOptionalLong(form.connectionTimeoutSeconds()),
            parseOptionalInteger(form.retryCount()),
            parseOptions(form.sshOptions(), new ArrayList<>())),
        form.projectId(),
        form.environmentId(),
        form.favorite(),
        form.tagIds());
  }

  private SshHost loadHost(UUID id) {
    SshHostRecord host =
        hosts.findById(id).orElseThrow(() -> new IllegalArgumentException("Host no encontrado"));
    CredentialRecord credential =
        credentials
            .findById(host.credentialId())
            .orElseThrow(() -> new IllegalArgumentException("Credencial no encontrada"));
    return new SshHost(
        host.id(),
        host.name(),
        host.hostname(),
        host.sshPort(),
        new Credential(credential.username(), credential.password()));
  }

  private static void required(String value, String message, List<String> errors) {
    if (value == null || value.isBlank()) errors.add(message);
  }

  private static void parsePort(String value, String label, List<String> errors) {
    if (value == null || value.isBlank()) return;
    try {
      int port = Integer.parseInt(value.trim());
      if (port < 1 || port > 65535) errors.add(label + " debe estar entre 1 y 65535");
    } catch (NumberFormatException exception) {
      errors.add(label + " debe ser numérico");
    }
  }

  private static void parseOptionalPositive(String value, String label, List<String> errors) {
    if (value == null || value.isBlank()) return;
    try {
      if (Long.parseLong(value.trim()) < 1) errors.add(label + " debe ser positivo");
    } catch (NumberFormatException exception) {
      errors.add(label + " debe ser numérico");
    }
  }

  private static void parseOptionalNonNegative(String value, String label, List<String> errors) {
    if (value == null || value.isBlank()) return;
    try {
      if (Integer.parseInt(value.trim()) < 0) errors.add(label + " no puede ser negativo");
    } catch (NumberFormatException exception) {
      errors.add(label + " debe ser numérico");
    }
  }

  private static int parse(String value) {
    return Integer.parseInt(value.trim());
  }

  private static Long parseOptionalLong(String value) {
    return value == null || value.isBlank() ? null : Long.parseLong(value.trim());
  }

  private static Integer parseOptionalInteger(String value) {
    return value == null || value.isBlank() ? null : Integer.parseInt(value.trim());
  }

  private static Map<String, String> parseOptions(String text, List<String> errors) {
    Map<String, String> options = new LinkedHashMap<>();
    if (text == null || text.isBlank()) return options;
    for (String line : text.lines().toList()) {
      if (line.isBlank()) continue;
      int separator = line.indexOf('=');
      if (separator <= 0) {
        errors.add("Cada opción SSH debe tener formato clave=valor");
        continue;
      }
      String key = line.substring(0, separator).trim();
      String value = line.substring(separator + 1).trim();
      if (key.isBlank() || value.isBlank()) {
        errors.add("Las opciones SSH no pueden tener clave o valor vacío");
      } else if (options.put(key, value) != null) {
        errors.add("La opción SSH está repetida: " + key);
      }
    }
    try {
      new TunnelOptions(null, null, null, options);
    } catch (IllegalArgumentException exception) {
      errors.add(exception.getMessage());
    }
    return Map.copyOf(options);
  }

  private static String formatOptions(Map<String, String> options) {
    return options.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
  }

  private static String optional(Number value) {
    return value == null ? "" : value.toString();
  }

  public record Form(
      UUID id,
      String name,
      String bindAddress,
      String localPort,
      String destinationHost,
      String destinationPort,
      UUID finalHostId,
      UUID jump1HostId,
      UUID jump2HostId,
      String keepAliveSeconds,
      String connectionTimeoutSeconds,
      String retryCount,
      String sshOptions,
      UUID projectId,
      UUID environmentId,
      java.util.Set<UUID> tagIds,
      boolean favorite) {
    public Form(
        UUID id,
        String name,
        String bindAddress,
        String localPort,
        String destinationHost,
        String destinationPort,
        UUID finalHostId,
        UUID jump1HostId,
        UUID jump2HostId,
        String keepAliveSeconds,
        String connectionTimeoutSeconds,
        String retryCount,
        String sshOptions) {
      this(
          id,
          name,
          bindAddress,
          localPort,
          destinationHost,
          destinationPort,
          finalHostId,
          jump1HostId,
          jump2HostId,
          keepAliveSeconds,
          connectionTimeoutSeconds,
          retryCount,
          sshOptions,
          null,
          null,
          java.util.Set.of(),
          false);
    }

    public Form {
      tagIds = java.util.Set.copyOf(tagIds == null ? java.util.Set.of() : tagIds);
    }
  }
}

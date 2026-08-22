package com.ssh.tunnelmanager.infrastructure.diagnostics;

import com.ssh.tunnelmanager.application.diagnostics.DiagnosticPort;
import com.ssh.tunnelmanager.application.persistence.ConfigurationStore;
import com.ssh.tunnelmanager.application.persistence.model.ConfigurationSnapshot;
import com.ssh.tunnelmanager.application.persistence.model.CredentialRecord;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntime;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager;
import com.ssh.tunnelmanager.infrastructure.persistence.DataDirectories;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates an asynchronous-callable, password-free diagnostic bundle. */
public final class ZipDiagnosticService implements DiagnosticPort {
  private static final Logger LOGGER = LoggerFactory.getLogger(ZipDiagnosticService.class);
  private final ConfigurationStore configuration;
  private final TunnelRuntimeManager runtime;
  private final DataDirectories directories;
  private final String applicationVersion;

  public ZipDiagnosticService(
      ConfigurationStore configuration,
      TunnelRuntimeManager runtime,
      DataDirectories directories,
      String applicationVersion) {
    this.configuration = Objects.requireNonNull(configuration);
    this.runtime = Objects.requireNonNull(runtime);
    this.directories = Objects.requireNonNull(directories);
    this.applicationVersion = Objects.requireNonNull(applicationVersion);
  }

  @Override
  public Path logsDirectory() {
    return directories.logs();
  }

  @Override
  public Path export(Path target) {
    Objects.requireNonNull(target);
    try {
      Files.createDirectories(target.toAbsolutePath().normalize().getParent());
      ConfigurationSnapshot snapshot = configuration.snapshot();
      try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
        add(zip, "system.txt", systemText(snapshot));
        add(zip, "runtime-state.json", runtimeJson());
        add(zip, "configuration.json", configurationJson(snapshot));
        addLogs(zip, snapshot.credentials());
      }
      return target;
    } catch (IOException exception) {
      throw new IllegalStateException("No se pudo generar el diagnóstico", exception);
    }
  }

  private String systemText(ConfigurationSnapshot snapshot) {
    return "applicationVersion="
        + applicationVersion
        + "\njava="
        + System.getProperty("java.version")
        + "\nos.name="
        + System.getProperty("os.name")
        + "\nos.version="
        + System.getProperty("os.version")
        + "\nos.arch="
        + System.getProperty("os.arch")
        + "\nstorageMode="
        + (Files.exists(directories.dataDirectory().getParent().resolve("portable.flag"))
            ? "PORTABLE"
            : "INSTALLED")
        + "\nschemaVersion="
        + snapshot.databaseSchemaVersion()
        + "\nexportFormat=1\n";
  }

  private String runtimeJson() {
    StringBuilder json = new StringBuilder("{\"generatedAt\":");
    appendString(json, Instant.now().toString());
    json.append(",\"tunnels\":[");
    boolean first = true;
    for (TunnelRuntime value : runtime.runtimes().values()) {
      if (!first) json.append(',');
      first = false;
      json.append("{\"tunnelId\":");
      appendString(json, value.tunnelId().toString());
      json.append(",\"desiredState\":");
      appendString(json, value.desiredState().name());
      json.append(",\"actualState\":");
      appendString(json, value.actualState().name());
      json.append(",\"retryAttempt\":").append(value.retryAttempt());
      json.append(",\"startedAt\":");
      appendString(json, value.startedAt() == null ? null : value.startedAt().toString());
      json.append(",\"connectionEstablishedAt\":");
      appendString(
          json,
          value.connectionEstablishedAt() == null
              ? null
              : value.connectionEstablishedAt().toString());
      json.append(",\"lastError\":");
      appendString(json, value.lastError() == null ? null : safeMessage(value.lastError()));
      json.append(",\"sessionEvents\":");
      appendStrings(json, value.sessionEvents());
      json.append('}');
    }
    return json.append("]}").toString();
  }

  private String configurationJson(ConfigurationSnapshot snapshot) {
    StringBuilder json = new StringBuilder("{\"schemaVersion\":");
    json.append(snapshot.databaseSchemaVersion()).append(",\"credentials\":[");
    appendCredentials(json, snapshot.credentials());
    json.append("],\"hosts\":");
    appendRecords(json, snapshot.hosts());
    json.append(",\"projects\":");
    appendRecords(json, snapshot.projects());
    json.append(",\"environments\":");
    appendRecords(json, snapshot.environments());
    json.append(",\"tags\":");
    appendRecords(json, snapshot.tags());
    json.append(",\"tunnels\":");
    appendRecords(json, snapshot.tunnels());
    json.append(",\"groups\":");
    appendRecords(json, snapshot.groups());
    json.append(",\"trustedHostKeys\":");
    appendRecords(json, snapshot.trustedHostKeys());
    json.append(",\"settings\":");
    appendRecords(json, snapshot.settings());
    json.append(",\"recentItems\":");
    appendRecords(json, snapshot.recentItems());
    json.append(",\"keyboardShortcuts\":");
    appendRecords(json, snapshot.keyboardShortcuts());
    return json.append('}').toString();
  }

  private void addLogs(ZipOutputStream zip, List<CredentialRecord> credentials) throws IOException {
    if (!Files.isDirectory(directories.logs())) return;
    try (var files = Files.list(directories.logs())) {
      for (Path log : files.filter(Files::isRegularFile).toList()) {
        String content = Files.readString(log);
        for (CredentialRecord credential : credentials) {
          if (credential.password() != null && !credential.password().isBlank()) {
            content = content.replace(credential.password(), "[REDACTED]");
          }
        }
        add(zip, "logs/" + log.getFileName(), content);
      }
    }
  }

  private static void add(ZipOutputStream zip, String name, String content) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  private static void appendCredentials(StringBuilder json, Collection<CredentialRecord> values) {
    boolean first = true;
    for (CredentialRecord value : values) {
      if (!first) json.append(',');
      first = false;
      json.append("{\"id\":");
      appendString(json, value.id() == null ? null : value.id().toString());
      json.append(",\"username\":");
      appendString(json, value.username());
      json.append('}');
    }
  }

  private static void appendRecords(StringBuilder json, Collection<?> values) {
    json.append('[');
    boolean first = true;
    for (Object value : values) {
      if (!first) json.append(',');
      first = false;
      appendString(json, value == null ? null : value.toString());
    }
    json.append(']');
  }

  private static void appendStrings(StringBuilder json, Collection<String> values) {
    json.append('[');
    boolean first = true;
    for (String value : values) {
      if (!first) json.append(',');
      first = false;
      appendString(json, value);
    }
    json.append(']');
  }

  private static void appendString(StringBuilder json, String value) {
    if (value == null) {
      json.append("null");
      return;
    }
    json.append('"');
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '\\' -> json.append("\\\\");
        case '"' -> json.append("\\\"");
        case '\n' -> json.append("\\n");
        case '\r' -> json.append("\\r");
        case '\t' -> json.append("\\t");
        default -> json.append(character);
      }
    }
    json.append('"');
  }

  private static String safeMessage(Throwable error) {
    String message = error.getMessage();
    return message == null
        ? error.getClass().getSimpleName()
        : message.replaceAll("(?i)password\\s*[=:]\\s*\\S+", "password=[REDACTED]");
  }
}

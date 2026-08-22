package com.ssh.tunnelmanager.application.portability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ssh.tunnelmanager.application.persistence.model.ConfigurationSnapshot;
import com.ssh.tunnelmanager.application.portability.model.ExportDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** Serializes the public export format without exposing the SQLite schema. */
public final class ConfigurationJsonCodec {
  public static final int SUPPORTED_FORMAT_VERSION = 1;

  private final ObjectMapper mapper;

  public ConfigurationJsonCodec() {
    mapper =
        new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  public void write(Path file, ConfigurationSnapshot snapshot, String applicationVersion) {
    ExportDocument document = ExportDocument.from(snapshot, applicationVersion, Instant.now());
    try {
      Path absolute = file.toAbsolutePath().normalize();
      Files.createDirectories(absolute.getParent());
      Files.writeString(absolute, mapper.writeValueAsString(document));
    } catch (IOException exception) {
      throw new PortabilityException("No se pudo escribir la exportación JSON", exception);
    }
  }

  public ExportDocument read(Path file) {
    try {
      String json = Files.readString(file.toAbsolutePath().normalize());
      var tree = mapper.readTree(json);
      if (tree == null || !tree.has("formatVersion")) {
        throw new PortabilityException("La exportación no contiene formatVersion");
      }
      int version = tree.get("formatVersion").asInt(-1);
      if (version != SUPPORTED_FORMAT_VERSION) {
        throw new UnsupportedExportFormatException(version);
      }
      return mapper.treeToValue(tree, ExportDocument.class);
    } catch (PortabilityException exception) {
      throw exception;
    } catch (IOException | RuntimeException exception) {
      throw new PortabilityException("La exportación JSON no es válida", exception);
    }
  }

  public String pretty(ConfigurationSnapshot snapshot, String applicationVersion) {
    try {
      return mapper.writeValueAsString(
          ExportDocument.from(snapshot, applicationVersion, Instant.now()));
    } catch (JsonProcessingException exception) {
      throw new PortabilityException("No se pudo serializar la exportación JSON", exception);
    }
  }
}

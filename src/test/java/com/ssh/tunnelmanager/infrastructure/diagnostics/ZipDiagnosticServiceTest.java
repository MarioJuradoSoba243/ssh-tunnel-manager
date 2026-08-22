package com.ssh.tunnelmanager.infrastructure.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssh.tunnelmanager.application.persistence.ConfigurationStore;
import com.ssh.tunnelmanager.application.persistence.model.ConfigurationSnapshot;
import com.ssh.tunnelmanager.application.persistence.model.CredentialRecord;
import com.ssh.tunnelmanager.application.runtime.ApplicationEventPublisher;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager;
import com.ssh.tunnelmanager.application.ssh.SshTunnelEngine;
import com.ssh.tunnelmanager.application.tunnel.TunnelRequestProvider;
import com.ssh.tunnelmanager.infrastructure.persistence.DataDirectories;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipDiagnosticServiceTest {
  @Test
  void excludesPasswordsFromConfigurationAndLogs(@TempDir Path directory) throws Exception {
    String password = "SUPER_SECRET_TEST_PASSWORD_123";
    Path logs = directory.resolve("logs");
    Files.createDirectories(logs);
    Files.writeString(logs.resolve("application.log"), "password=" + password);
    ConfigurationSnapshot snapshot =
        new ConfigurationSnapshot(
            1,
            List.of(new CredentialRecord(java.util.UUID.randomUUID(), "user", password)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    ConfigurationStore store =
        new ConfigurationStore() {
          @Override
          public ConfigurationSnapshot snapshot() {
            return snapshot;
          }

          @Override
          public void replace(ConfigurationSnapshot ignored) {}
        };
    TunnelRequestProvider requests = id -> null;
    SshTunnelEngine engine =
        new SshTunnelEngine() {
          public com.ssh.tunnelmanager.application.ssh.TunnelHandle openTunnel(
              com.ssh.tunnelmanager.application.ssh.TunnelConnectionRequest request) {
            throw new UnsupportedOperationException();
          }

          public com.ssh.tunnelmanager.application.ssh.ConnectionTestResult testConnection(
              com.ssh.tunnelmanager.application.ssh.TunnelConnectionRequest request) {
            throw new UnsupportedOperationException();
          }

          public void closeTunnel(com.ssh.tunnelmanager.application.ssh.TunnelHandle handle) {}
        };
    TunnelRuntimeManager runtime =
        new TunnelRuntimeManager(engine, requests, new ApplicationEventPublisher(), 1);
    DataDirectories directories =
        new DataDirectories(directory, directory.resolve("db"), directory.resolve("backups"), logs);
    Path output = directory.resolve("diagnostic.zip");
    new ZipDiagnosticService(store, runtime, directories, "1.0.0").export(output);
    runtime.close();
    try (ZipFile zip = new ZipFile(output.toFile())) {
      var entries = zip.entries();
      while (entries.hasMoreElements()) {
        String content = new String(zip.getInputStream(entries.nextElement()).readAllBytes());
        assertThat(content).doesNotContain(password);
      }
    }
  }
}

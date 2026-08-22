package com.ssh.tunnelmanager.application.portability;

import com.ssh.tunnelmanager.application.persistence.ConfigurationStore;
import java.nio.file.Path;

public final class ExportConfigurationUseCase {
  private final ConfigurationStore store;
  private final ConfigurationJsonCodec codec;
  private final String applicationVersion;

  public ExportConfigurationUseCase(
      ConfigurationStore store, ConfigurationJsonCodec codec, String applicationVersion) {
    this.store = store;
    this.codec = codec;
    this.applicationVersion = applicationVersion;
  }

  public void execute(Path destination) {
    codec.write(destination, store.snapshot(), applicationVersion);
  }
}

package com.ssh.tunnelmanager.application.persistence;

import com.ssh.tunnelmanager.application.persistence.model.ConfigurationSnapshot;

/** Port for consistent configuration snapshots and atomic replacement. */
public interface ConfigurationStore {
  ConfigurationSnapshot snapshot();

  void replace(ConfigurationSnapshot snapshot);
}

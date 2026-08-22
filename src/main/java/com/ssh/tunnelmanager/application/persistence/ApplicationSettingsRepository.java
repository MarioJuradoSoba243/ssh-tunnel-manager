package com.ssh.tunnelmanager.application.persistence;

import com.ssh.tunnelmanager.application.persistence.model.SettingRecord;
import java.util.List;
import java.util.Optional;

public interface ApplicationSettingsRepository {
  Optional<String> findValue(String key);

  List<SettingRecord> findAll();

  void put(String key, String value);

  void delete(String key);
}

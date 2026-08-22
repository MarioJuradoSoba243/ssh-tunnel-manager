package com.ssh.tunnelmanager.application.persistence;

import com.ssh.tunnelmanager.application.persistence.model.KeyboardShortcutRecord;
import java.util.List;
import java.util.Optional;

public interface KeyboardShortcutRepository {
  Optional<KeyboardShortcutRecord> findByAction(String action);

  List<KeyboardShortcutRecord> findAll();

  void save(KeyboardShortcutRecord shortcut);

  void delete(String action);
}

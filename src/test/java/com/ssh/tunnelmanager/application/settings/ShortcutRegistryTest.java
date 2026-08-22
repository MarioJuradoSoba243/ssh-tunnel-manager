package com.ssh.tunnelmanager.application.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssh.tunnelmanager.application.persistence.KeyboardShortcutRepository;
import com.ssh.tunnelmanager.application.persistence.model.KeyboardShortcutRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ShortcutRegistryTest {
  @Test
  void normalizesModifiersInStableOrder() {
    assertThat(ShortcutRegistry.normalize("alt+ctrl+shift+k")).isEqualTo("CTRL+SHIFT+ALT+K");
  }

  @Test
  void rejectsConflictingShortcuts() {
    ShortcutRegistry registry = new ShortcutRegistry(new InMemoryRepository());
    assertThatThrownBy(
            () ->
                registry.replace(
                    java.util.Map.of(
                        ShortcutAction.GLOBAL_SEARCH,
                        "CTRL+X",
                        ShortcutAction.NEW_TUNNEL,
                        "CTRL+X")))
        .isInstanceOf(ShortcutConflictException.class);
  }

  private static final class InMemoryRepository implements KeyboardShortcutRepository {
    private final List<KeyboardShortcutRecord> values = new ArrayList<>();

    @Override
    public Optional<KeyboardShortcutRecord> findByAction(String action) {
      return values.stream().filter(value -> value.action().equals(action)).findFirst();
    }

    @Override
    public List<KeyboardShortcutRecord> findAll() {
      return List.copyOf(values);
    }

    @Override
    public void save(KeyboardShortcutRecord shortcut) {
      delete(shortcut.action());
      values.add(shortcut);
    }

    @Override
    public void delete(String action) {
      values.removeIf(value -> value.action().equals(action));
    }
  }
}

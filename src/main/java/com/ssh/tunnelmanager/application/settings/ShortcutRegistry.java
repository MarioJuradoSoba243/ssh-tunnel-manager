package com.ssh.tunnelmanager.application.settings;

import com.ssh.tunnelmanager.application.persistence.KeyboardShortcutRepository;
import com.ssh.tunnelmanager.application.persistence.model.KeyboardShortcutRecord;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Validates and persists the finite set of keyboard shortcuts used by the shell. */
public final class ShortcutRegistry {
  private static final List<String> MODIFIERS = List.of("CTRL", "SHIFT", "ALT");
  private static final Logger LOGGER = LoggerFactory.getLogger(ShortcutRegistry.class);
  private final KeyboardShortcutRepository repository;
  private volatile Map<ShortcutAction, String> cachedValues;

  public ShortcutRegistry(KeyboardShortcutRepository repository) {
    this.repository = repository;
  }

  public Map<ShortcutAction, String> load() {
    Map<ShortcutAction, String> current = cachedValues;
    if (current != null) return current;
    Map<ShortcutAction, String> values = defaults();
    for (KeyboardShortcutRecord record : repository.findAll()) {
      try {
        ShortcutAction action = ShortcutAction.valueOf(record.action());
        values.put(action, normalize(record.shortcut()));
      } catch (IllegalArgumentException exception) {
        // Unknown actions remain forward-compatible and are not applied to the current UI.
        LOGGER.debug("Se ignora un atajo persistido no compatible: {}", record.action(), exception);
      }
    }
    current = Map.copyOf(values);
    cachedValues = current;
    return current;
  }

  public void replace(Map<ShortcutAction, String> requested) {
    Map<ShortcutAction, String> values = defaults();
    requested.forEach((action, shortcut) -> values.put(action, normalize(shortcut)));
    validateConflicts(values);
    values.forEach(
        (action, shortcut) -> repository.save(new KeyboardShortcutRecord(action.name(), shortcut)));
    cachedValues = Map.copyOf(values);
  }

  public static Map<ShortcutAction, String> defaults() {
    Map<ShortcutAction, String> values = new EnumMap<>(ShortcutAction.class);
    for (ShortcutAction action : ShortcutAction.values())
      values.put(action, action.defaultShortcut());
    return values;
  }

  public static String normalize(String shortcut) {
    if (shortcut == null || shortcut.isBlank())
      throw new ShortcutConflictException("El atajo no puede estar vacío");
    String[] tokens = shortcut.trim().toUpperCase(Locale.ROOT).split("\\+");
    boolean keyFound = false;
    boolean modifierFound = false;
    String key = null;
    for (String token : tokens) {
      if (token.isBlank())
        throw new ShortcutConflictException("El atajo contiene una combinación inválida");
      if (MODIFIERS.contains(token)) {
        if (!modifierFound && !token.isBlank()) modifierFound = true;
        else if (count(tokens, token) > 1)
          throw new ShortcutConflictException("El modificador " + token + " está repetido");
      } else if (!keyFound && validKey(token)) {
        keyFound = true;
        key = token;
      } else {
        throw new ShortcutConflictException("Tecla no soportada en el atajo: " + token);
      }
    }
    if (!keyFound) throw new ShortcutConflictException("El atajo debe contener una tecla");
    if (!modifierFound && key.length() == 1)
      throw new ShortcutConflictException("Las teclas de texto requieren Ctrl, Shift o Alt");
    StringBuilder result = new StringBuilder();
    for (String modifier : MODIFIERS) {
      if (contains(tokens, modifier)) {
        if (result.length() > 0) result.append('+');
        result.append(modifier);
      }
    }
    if (result.length() > 0) result.append('+');
    return result.append(key).toString();
  }

  public static void validateConflicts(Map<ShortcutAction, String> values) {
    Map<String, ShortcutAction> owners = new LinkedHashMap<>();
    values.forEach(
        (action, shortcut) -> {
          String normalized = normalize(shortcut);
          ShortcutAction previous = owners.putIfAbsent(normalized, action);
          if (previous != null && previous != action)
            throw new ShortcutConflictException(
                "El atajo "
                    + normalized
                    + " está asignado a "
                    + previous.label()
                    + " y "
                    + action.label());
        });
  }

  public boolean matches(ShortcutAction action, KeyEvent event) {
    String shortcut = load().get(action);
    if (shortcut == null) return false;
    String[] tokens = shortcut.split("\\+");
    String key = tokens[tokens.length - 1];
    return modifier(tokens, "CTRL", event.isControlDown())
        && modifier(tokens, "SHIFT", event.isShiftDown())
        && modifier(tokens, "ALT", event.isAltDown())
        && keyCode(key) == event.getCode();
  }

  private static boolean modifier(String[] tokens, String modifier, boolean pressed) {
    return contains(tokens, modifier) == pressed;
  }

  private static KeyCode keyCode(String key) {
    return switch (key) {
      case "ENTER" -> KeyCode.ENTER;
      case "ESC" -> KeyCode.ESCAPE;
      default -> KeyCode.getKeyCode(key);
    };
  }

  private static boolean validKey(String token) {
    return token.matches("[A-Z0-9]")
        || token.matches("F([1-9]|1[0-2])")
        || token.equals("ENTER")
        || token.equals("ESC");
  }

  private static boolean contains(String[] tokens, String value) {
    for (String token : tokens) if (token.equals(value)) return true;
    return false;
  }

  private static int count(String[] tokens, String value) {
    int count = 0;
    for (String token : tokens) if (token.equals(value)) count++;
    return count;
  }
}

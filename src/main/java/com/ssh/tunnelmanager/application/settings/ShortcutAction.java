package com.ssh.tunnelmanager.application.settings;

public enum ShortcutAction {
  GLOBAL_SEARCH("Búsqueda global", "CTRL+K"),
  NEW_TUNNEL("Nuevo túnel", "CTRL+N"),
  FILTER_VIEW("Buscar o filtrar vista actual", "CTRL+F"),
  REFRESH_VIEW("Refrescar vista local", "F5"),
  OPEN_SELECTION("Abrir o editar selección", "ENTER"),
  CLOSE_OVERLAY("Cerrar diálogo o volver", "ESC");

  private final String label;
  private final String defaultShortcut;

  ShortcutAction(String label, String defaultShortcut) {
    this.label = label;
    this.defaultShortcut = defaultShortcut;
  }

  public String label() {
    return label;
  }

  public String defaultShortcut() {
    return defaultShortcut;
  }
}

package com.ssh.tunnelmanager.application.portability;

public final class UnsupportedExportFormatException extends PortabilityException {
  private final int formatVersion;

  public UnsupportedExportFormatException(int formatVersion) {
    super("Formato de exportación no soportado: formatVersion=" + formatVersion);
    this.formatVersion = formatVersion;
  }

  public int formatVersion() {
    return formatVersion;
  }
}

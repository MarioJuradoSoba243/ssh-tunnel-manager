package com.ssh.tunnelmanager.application.portability;

public final class ActiveTunnelsException extends PortabilityException {
  public ActiveTunnelsException() {
    super("Hay túneles activos. Debe cancelarse o detener todos antes de continuar.");
  }
}

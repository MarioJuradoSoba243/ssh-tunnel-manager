package com.ssh.tunnelmanager.application.portability;

public class PortabilityException extends RuntimeException {
  public PortabilityException(String message) {
    super(message);
  }

  public PortabilityException(String message, Throwable cause) {
    super(message, cause);
  }
}

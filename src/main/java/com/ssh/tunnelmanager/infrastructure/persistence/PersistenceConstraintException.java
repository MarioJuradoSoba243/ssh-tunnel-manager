package com.ssh.tunnelmanager.infrastructure.persistence;

public final class PersistenceConstraintException extends PersistenceException {
  public PersistenceConstraintException(String message, Throwable cause) {
    super(message, cause);
  }
}

package com.ssh.tunnelmanager.infrastructure.persistence;

public final class DatabaseCorruptionException extends PersistenceException {
  public DatabaseCorruptionException(String message) {
    super(message);
  }
}

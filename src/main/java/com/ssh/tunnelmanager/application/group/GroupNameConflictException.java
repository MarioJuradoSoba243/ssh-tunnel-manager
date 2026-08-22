package com.ssh.tunnelmanager.application.group;

public final class GroupNameConflictException extends IllegalArgumentException {
  public GroupNameConflictException(String name) {
    super("Ya existe un grupo con el nombre: " + name);
  }
}

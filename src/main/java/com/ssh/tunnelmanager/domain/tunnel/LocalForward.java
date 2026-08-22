package com.ssh.tunnelmanager.domain.tunnel;

public record LocalForward(String bindAddress, int localPort) {
  public LocalForward {
    if (bindAddress == null || bindAddress.isBlank())
      throw new IllegalArgumentException("bindAddress requerido");
    if (localPort < 1 || localPort > 65535)
      throw new IllegalArgumentException("Puerto local inválido");
  }
}

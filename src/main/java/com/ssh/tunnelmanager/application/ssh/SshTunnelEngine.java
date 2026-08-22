package com.ssh.tunnelmanager.application.ssh;

public interface SshTunnelEngine {
  TunnelHandle openTunnel(TunnelConnectionRequest request);

  ConnectionTestResult testConnection(TunnelConnectionRequest request);

  void closeTunnel(TunnelHandle handle);
}

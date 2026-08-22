package com.ssh.tunnelmanager.application.ssh;

public interface HostKeyApprovalCoordinator {
  boolean approve(HostKeyApprovalRequest request);
}

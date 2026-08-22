package com.ssh.tunnelmanager.application.ssh;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

public final class SerialHostKeyApprovalCoordinator implements HostKeyApprovalCoordinator {
  private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
  private final Predicate<HostKeyApprovalRequest> approvalPolicy;

  public SerialHostKeyApprovalCoordinator(Predicate<HostKeyApprovalRequest> approvalPolicy) {
    this.approvalPolicy = approvalPolicy;
  }

  @Override
  public boolean approve(HostKeyApprovalRequest request) {
    ReentrantLock lock =
        locks.computeIfAbsent(
            request.hostname() + ":" + request.sshPort(), ignored -> new ReentrantLock());
    lock.lock();
    try {
      return approvalPolicy.test(request);
    } finally {
      lock.unlock();
    }
  }
}

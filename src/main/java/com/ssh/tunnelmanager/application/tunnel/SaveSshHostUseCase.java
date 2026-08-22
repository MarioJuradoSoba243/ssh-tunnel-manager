package com.ssh.tunnelmanager.application.tunnel;

import com.ssh.tunnelmanager.application.persistence.CredentialRepository;
import com.ssh.tunnelmanager.application.persistence.SshHostRepository;
import com.ssh.tunnelmanager.application.persistence.model.CredentialRecord;
import com.ssh.tunnelmanager.application.persistence.model.SshHostRecord;
import com.ssh.tunnelmanager.domain.host.SshHost;

public final class SaveSshHostUseCase {
  private final SshHostRepository hosts;
  private final CredentialRepository credentials;

  public SaveSshHostUseCase(SshHostRepository hosts, CredentialRepository credentials) {
    this.hosts = hosts;
    this.credentials = credentials;
  }

  public void execute(SshHost host) {
    credentials.save(
        new CredentialRecord(
            host.id(), host.credential().username(), host.credential().password()));
    hosts.save(
        new SshHostRecord(host.id(), host.name(), host.hostname(), host.sshPort(), host.id()));
  }
}

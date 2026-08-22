package com.ssh.tunnelmanager.application.host;

import com.ssh.tunnelmanager.application.persistence.CredentialRepository;
import com.ssh.tunnelmanager.application.persistence.SshHostRepository;
import com.ssh.tunnelmanager.application.persistence.model.CredentialRecord;
import com.ssh.tunnelmanager.application.persistence.model.SshHostRecord;
import com.ssh.tunnelmanager.application.tunnel.DeleteSshHostUseCase;
import com.ssh.tunnelmanager.application.tunnel.SaveSshHostUseCase;
import com.ssh.tunnelmanager.domain.credential.Credential;
import com.ssh.tunnelmanager.domain.host.SshHost;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class HostListViewModel {
  private final SshHostRepository hosts;
  private final CredentialRepository credentials;
  private final SaveSshHostUseCase save;
  private final DeleteSshHostUseCase delete;

  public HostListViewModel(
      SshHostRepository hosts,
      CredentialRepository credentials,
      SaveSshHostUseCase save,
      DeleteSshHostUseCase delete) {
    this.hosts = hosts;
    this.credentials = credentials;
    this.save = save;
    this.delete = delete;
  }

  public List<SshHostRecord> hosts() {
    return hosts.findAll();
  }

  public Optional<SshHost> find(UUID id) {
    return hosts
        .findById(id)
        .flatMap(
            host ->
                credentials
                    .findById(host.credentialId())
                    .map(credential -> toDomain(host, credential)));
  }

  public void save(SshHost host) {
    save.execute(host);
  }

  public void delete(UUID id) {
    delete.execute(id);
  }

  private static SshHost toDomain(SshHostRecord host, CredentialRecord credential) {
    return new SshHost(
        host.id(),
        host.name(),
        host.hostname(),
        host.sshPort(),
        new Credential(credential.username(), credential.password()));
  }
}

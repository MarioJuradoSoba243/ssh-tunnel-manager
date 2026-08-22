package com.ssh.tunnelmanager.application.bootstrap;

import com.ssh.tunnelmanager.application.persistence.CredentialRepository;
import com.ssh.tunnelmanager.application.persistence.SshHostRepository;
import com.ssh.tunnelmanager.application.persistence.TunnelRepository;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntimeManager;
import com.ssh.tunnelmanager.application.ssh.SshTunnelEngine;
import com.ssh.tunnelmanager.application.tunnel.DeleteSshHostUseCase;
import com.ssh.tunnelmanager.application.tunnel.DeleteTunnelUseCase;
import com.ssh.tunnelmanager.application.tunnel.DuplicateTunnelUseCase;
import com.ssh.tunnelmanager.application.tunnel.RetryTunnelUseCase;
import com.ssh.tunnelmanager.application.tunnel.SaveSshHostUseCase;
import com.ssh.tunnelmanager.application.tunnel.SaveTunnelUseCase;
import com.ssh.tunnelmanager.application.tunnel.StartTunnelUseCase;
import com.ssh.tunnelmanager.application.tunnel.StopTunnelUseCase;
import com.ssh.tunnelmanager.application.tunnel.TestTunnelConnectionUseCase;

public record ApplicationComponents(
    SaveSshHostUseCase saveSshHost,
    DeleteSshHostUseCase deleteSshHost,
    SaveTunnelUseCase saveTunnel,
    DeleteTunnelUseCase deleteTunnel,
    DuplicateTunnelUseCase duplicateTunnel,
    StartTunnelUseCase startTunnel,
    StopTunnelUseCase stopTunnel,
    RetryTunnelUseCase retryTunnel,
    TestTunnelConnectionUseCase testTunnelConnection,
    TunnelRuntimeManager runtimeManager,
    SshTunnelEngine sshEngine,
    SshHostRepository hostRepository,
    TunnelRepository tunnelRepository,
    CredentialRepository credentialRepository) {}

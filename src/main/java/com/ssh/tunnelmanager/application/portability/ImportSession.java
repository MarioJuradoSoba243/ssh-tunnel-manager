package com.ssh.tunnelmanager.application.portability;

import com.ssh.tunnelmanager.application.persistence.model.ConfigurationSnapshot;

public record ImportSession(
    ConfigurationSnapshot current, ConfigurationSnapshot imported, ImportPreview preview) {}

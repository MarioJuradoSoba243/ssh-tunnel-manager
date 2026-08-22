package com.ssh.tunnelmanager.infrastructure.persistence.repository;

import com.ssh.tunnelmanager.application.persistence.SshHostRepository;
import com.ssh.tunnelmanager.application.persistence.model.SshHostRecord;
import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcSshHostRepository extends JdbcRepositorySupport
    implements SshHostRepository {
  public JdbcSshHostRepository(DatabaseFactory database) {
    super(database);
  }

  public JdbcSshHostRepository(DatabaseFactory database, Runnable dirtyMarker) {
    super(database, dirtyMarker);
  }

  @Override
  public Optional<SshHostRecord> findById(UUID id) {
    return database.withConnection(
        connection -> {
          try (var statement =
              connection.prepareStatement(
                  "SELECT id, name, hostname, ssh_port, credential_id FROM ssh_host WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (var result = statement.executeQuery()) {
              return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
          }
        });
  }

  @Override
  public List<SshHostRecord> findAll() {
    return database.withConnection(
        connection -> {
          try (var statement = connection.createStatement();
              var result =
                  statement.executeQuery(
                      "SELECT id, name, hostname, ssh_port, credential_id FROM ssh_host ORDER BY name, id")) {
            var hosts = new java.util.ArrayList<SshHostRecord>();
            while (result.next()) {
              hosts.add(read(result));
            }
            return List.copyOf(hosts);
          }
        });
  }

  @Override
  public void save(SshHostRecord host) {
    try {
      database.withConnection(
          connection -> {
            try (var statement =
                connection.prepareStatement(
                    "INSERT INTO ssh_host(id, name, hostname, ssh_port, credential_id) VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT(id) DO UPDATE SET name = excluded.name, hostname = excluded.hostname, ssh_port = excluded.ssh_port, credential_id = excluded.credential_id")) {
              statement.setString(1, host.id().toString());
              statement.setString(2, host.name());
              statement.setString(3, host.hostname());
              statement.setInt(4, host.sshPort());
              statement.setString(5, host.credentialId().toString());
              statement.executeUpdate();
              return null;
            }
          });
      markDirty();
    } catch (PersistenceException exception) {
      throw translate("guardar el host SSH", exception);
    }
  }

  @Override
  public void delete(UUID id) {
    try {
      database.withConnection(
          connection -> {
            try (var statement = connection.prepareStatement("DELETE FROM ssh_host WHERE id = ?")) {
              statement.setString(1, id.toString());
              statement.executeUpdate();
              return null;
            }
          });
      markDirty();
    } catch (PersistenceException exception) {
      throw translate("borrar el host SSH", exception);
    }
  }

  private static SshHostRecord read(java.sql.ResultSet result) throws SQLException {
    return new SshHostRecord(
        UUID.fromString(result.getString("id")),
        result.getString("name"),
        result.getString("hostname"),
        result.getInt("ssh_port"),
        UUID.fromString(result.getString("credential_id")));
  }
}

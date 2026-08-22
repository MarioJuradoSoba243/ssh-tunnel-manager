package com.ssh.tunnelmanager.infrastructure.persistence.repository;

import com.ssh.tunnelmanager.application.persistence.TrustedHostKeyRepository;
import com.ssh.tunnelmanager.application.persistence.model.TrustedHostKeyRecord;
import com.ssh.tunnelmanager.infrastructure.persistence.DatabaseFactory;
import com.ssh.tunnelmanager.infrastructure.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcTrustedHostKeyRepository extends JdbcRepositorySupport
    implements TrustedHostKeyRepository {
  public JdbcTrustedHostKeyRepository(DatabaseFactory database) {
    super(database);
  }

  public JdbcTrustedHostKeyRepository(DatabaseFactory database, Runnable dirtyMarker) {
    super(database, dirtyMarker);
  }

  @Override
  public Optional<TrustedHostKeyRecord> findByHost(String hostname, int sshPort) {
    return database.withConnection(
        connection -> {
          try (var statement =
              connection.prepareStatement(select() + " WHERE hostname = ? AND ssh_port = ?")) {
            statement.setString(1, hostname);
            statement.setInt(2, sshPort);
            try (var result = statement.executeQuery()) {
              return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
          }
        });
  }

  @Override
  public List<TrustedHostKeyRecord> findAll() {
    return database.withConnection(
        connection -> {
          try (var statement = connection.createStatement();
              var result = statement.executeQuery(select() + " ORDER BY hostname, ssh_port")) {
            var keys = new java.util.ArrayList<TrustedHostKeyRecord>();
            while (result.next()) keys.add(read(result));
            return List.copyOf(keys);
          }
        });
  }

  @Override
  public void save(TrustedHostKeyRecord key) {
    try {
      database.withConnection(
          connection -> {
            try (var statement =
                connection.prepareStatement(
                    "INSERT INTO trusted_host_key(id, hostname, ssh_port, key_algorithm, public_key, fingerprint_sha256, approved_at) VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(hostname, ssh_port) DO UPDATE SET id = excluded.id, key_algorithm = excluded.key_algorithm, public_key = excluded.public_key, fingerprint_sha256 = excluded.fingerprint_sha256, approved_at = excluded.approved_at")) {
              statement.setString(1, key.id().toString());
              statement.setString(2, key.hostname());
              statement.setInt(3, key.sshPort());
              statement.setString(4, key.keyAlgorithm());
              statement.setString(5, key.publicKey());
              statement.setString(6, key.fingerprintSha256());
              statement.setString(7, key.approvedAt().toString());
              statement.executeUpdate();
              return null;
            }
          });
      markDirty();
    } catch (PersistenceException exception) {
      throw translate("guardar la clave confiable", exception);
    }
  }

  @Override
  public void delete(UUID id) {
    mutate("DELETE FROM trusted_host_key WHERE id = ?", id.toString());
  }

  @Override
  public void deleteByHost(String hostname, int sshPort) {
    try {
      database.withConnection(
          connection -> {
            try (var statement =
                connection.prepareStatement(
                    "DELETE FROM trusted_host_key WHERE hostname = ? AND ssh_port = ?")) {
              statement.setString(1, hostname);
              statement.setInt(2, sshPort);
              statement.executeUpdate();
              return null;
            }
          });
      markDirty();
    } catch (PersistenceException exception) {
      throw translate("borrar la clave confiable", exception);
    }
  }

  private void mutate(String sql, String value) {
    try {
      database.withConnection(
          connection -> {
            try (var statement = connection.prepareStatement(sql)) {
              statement.setString(1, value);
              statement.executeUpdate();
              return null;
            }
          });
      markDirty();
    } catch (PersistenceException exception) {
      throw translate("borrar la clave confiable", exception);
    }
  }

  private static String select() {
    return "SELECT id, hostname, ssh_port, key_algorithm, public_key, fingerprint_sha256, approved_at FROM trusted_host_key";
  }

  private static TrustedHostKeyRecord read(java.sql.ResultSet result) throws java.sql.SQLException {
    return new TrustedHostKeyRecord(
        UUID.fromString(result.getString("id")),
        result.getString("hostname"),
        result.getInt("ssh_port"),
        result.getString("key_algorithm"),
        result.getString("public_key"),
        result.getString("fingerprint_sha256"),
        java.time.Instant.parse(result.getString("approved_at")));
  }
}
